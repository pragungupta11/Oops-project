package com.iitsm.iitsm.web;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.iitsm.exception.TechnicianWorkloadExceededException;
import com.iitsm.iitsm.model.AlertType;
import com.iitsm.iitsm.model.CommentKind;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaStatus;
import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.notification.IncidentEvent;
import com.iitsm.iitsm.notification.IncidentEventType;
import com.iitsm.iitsm.persistence.entity.IncidentCommentEntity;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;
import com.iitsm.iitsm.repository.IncidentCommentRepository;
import com.iitsm.iitsm.service.IncidentProcessingService;

/**
 * Technician capacity: assigns queued incidents, keeps workload counters correct, and hands over
 * the open incidents of technicians who are unavailable or deactivated.
 */
@Service
public class TechnicianHandoverService {
    private final IncidentPersistenceService incidents;
    private final TechnicianPersistenceService technicians;
    private final AuditService audit;
    private final IncidentOperations ops;

    public TechnicianHandoverService(IncidentPersistenceService incidents,
            TechnicianPersistenceService technicians,
            AuditService audit,
            IncidentOperations ops) {
        this.incidents = incidents;
        this.technicians = technicians;
        this.audit = audit;
        this.ops = ops;
    }

    /**
     * Assigns queued (unassigned) incidents to technicians who are now free, oldest first.
     * Called every minute by a job and right after an admin frees capacity.
     */
    @Transactional
    public int assignQueuedIncidents() {
        int assigned = 0;
        List<Incident> queued = incidents.findByStatus(IncidentStatus.CLASSIFIED).stream()
                .filter(i -> i.getAssignedTechnician() == null && !i.isLinked() && !i.isFinished()
                        && i.getStatus() == IncidentStatus.CLASSIFIED)
                .sorted(Comparator.comparing(Incident::getCreatedAt))
                .toList();
        for (Incident incident : queued) {
            try {
                Technician next = ops.pickWithinLimit(incident.getCategory(), technicians.findAll());
                ops.moveTo(incident, next, "taken from the queue");
                assigned++;
            } catch (TechnicianUnavailableException stillNobody) {
                // stays in the queue
            }
        }
        return assigned;
    }

    private static final Set<IncidentStatus> ACTIVE_WORK =
            EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS);

    /**
     * Self-correction of the stored workload counters: recounts each technician's active incidents
     * (assigned, acknowledged or in progress; linked reports excluded) and fixes any counter that
     * drifted, e.g. after two actions at the same moment. Returns how many counters were corrected.
     */
    @Transactional
    public int reconcileWorkload() {
        Map<String, Long> actual = incidents.findOpen().stream()
                .filter(i -> i.getAssignedTechnician() != null && !i.isLinked() && ACTIVE_WORK.contains(i.getStatus()))
                .collect(Collectors.groupingBy(i -> i.getAssignedTechnician().getId(), Collectors.counting()));
        int corrected = 0;
        for (Technician t : technicians.findAll()) {
            int real = actual.getOrDefault(t.getId(), 0L).intValue();
            if (t.getActiveIncidents() != real) {
                technicians.save(new Technician(t.getId(), t.getName(), t.getSkills(), real, t.isAvailable()));
                audit.record("WORKLOAD_CORRECTED", "TECHNICIAN", t.getId(),
                        "Active incidents counter " + t.getActiveIncidents() + " -> " + real);
                corrected++;
            }
        }
        return corrected;
    }

    /**
     * Hands over every open incident of a technician who is unavailable or deactivated to other
     * available technicians with the right skill (active assignment strategy). Incidents nobody
     * else can take stay where they are and are reported back to the admin.
     */
    @Transactional
    public IncidentWorkflowService.HandoverResult reassignOpenIncidentsOf(String technicianId) {
        int moved = 0;
        List<String> notMoved = new ArrayList<>();
        for (Incident incident : incidents.findByTechnician(technicianId)) {
            Technician current = incident.getAssignedTechnician();
            if (current == null || !current.getId().equals(technicianId)
                    || incident.isLinked() || incident.isFinished()) {
                continue;
            }
            List<Technician> others = technicians.findAll().stream()
                    .filter(t -> !t.getId().equals(technicianId))
                    .toList();
            Technician next;
            try {
                next = ops.pickWithinLimit(incident.getCategory(), others);
            } catch (TechnicianUnavailableException e) {
                notMoved.add(incident.getId());
                continue;
            }
            ops.moveTo(incident, next, "automatic, matching skill");
            moved++;
        }
        return new IncidentWorkflowService.HandoverResult(moved, notMoved);
    }

    /**
     * Admin hands one open incident to a technician of their choice, e.g. when nobody else has
     * the skill. The target must be available.
     */
    @Transactional
    public Incident handOver(String incidentId, String toTechnicianId) {
        Incident incident = incidents.findById(incidentId);
        if (incident.isLinked() || incident.isFinished()) {
            throw new InvalidIncidentDataException("Only open, non-linked incidents can be handed over");
        }
        Technician target = technicians.findById(toTechnicianId);
        if (!target.isAvailable()) {
            throw new InvalidIncidentDataException(target.getName() + " is not available");
        }
        if (incident.getAssignedTechnician() != null && incident.getAssignedTechnician().getId().equals(toTechnicianId)) {
            throw new InvalidIncidentDataException("The incident is already with " + target.getName());
        }
        if (target.getActiveIncidents() >= ops.maxActiveIncidents()) {
            throw new TechnicianWorkloadExceededException(target.getName() + " already has "
                    + target.getActiveIncidents() + " active incidents (limit " + ops.maxActiveIncidents() + ")");
        }
        return ops.moveTo(incident, target, "chosen by admin");
    }
}
