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
 * Manual duplicate handling: a technician links a report to the incident it duplicates, dismisses
 * the suggestion, or undoes a wrong link.
 */
@Service
public class DuplicateLinkingService {
    private final IncidentPersistenceService incidents;
    private final TechnicianPersistenceService technicians;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final IncidentOperations ops;

    public DuplicateLinkingService(IncidentPersistenceService incidents,
            TechnicianPersistenceService technicians,
            AuditService audit,
            ApplicationEventPublisher events,
            IncidentOperations ops) {
        this.incidents = incidents;
        this.technicians = technicians;
        this.audit = audit;
        this.events = events;
        this.ops = ops;
    }

    /** Technician confirms the system's hint: this report is the same problem as the suggested incident. */
    @Transactional
    public Incident linkToSuggestedParent(String incidentId, String technicianId) {
        Incident child = ops.loadAssignedTo(incidentId, technicianId);
        if (!child.hasDuplicateSuggestion()) {
            throw new InvalidIncidentDataException("There is no duplicate suggestion for " + incidentId);
        }
        boolean hasOwnChildren = !incidents.findLinkedTo(incidentId).isEmpty();
        if (hasOwnChildren) {
            throw new InvalidIncidentDataException(incidentId
                    + " already has linked reports, so it cannot itself be linked to another incident");
        }
        Incident parent = incidents.findById(child.getSuggestedParentId());
        Integer percent = child.getSuggestedMatchPercent();
        Technician previous = child.getAssignedTechnician();

        child.linkTo(parent);

        // A linked report is the same piece of work as its parent, so it stops counting
        // towards anyone's workload.
        Technician old = technicians.findById(previous.getId());
        old.decrementActiveIncidents();
        technicians.save(old);

        ops.catchUpWithParent(child, parent);
        Incident saved = incidents.save(child);
        events.publishEvent(new IncidentEvent(IncidentEventType.LINKED, saved));
        audit.record("DUPLICATE_LINKED", "INCIDENT", incidentId,
                "Linked to " + parent.getId() + " (" + percent + "% match) by " + technicianId
                        + "; now handled by " + parent.getAssignedTechnician().getId());
        return saved;
    }

    /** Technician checked the hint and decided it is a different problem. */
    @Transactional
    public Incident dismissDuplicateSuggestion(String incidentId, String technicianId) {
        Incident incident = ops.loadAssignedTo(incidentId, technicianId);
        String suggested = incident.getSuggestedParentId();
        if (suggested == null) {
            throw new InvalidIncidentDataException("There is no duplicate suggestion for " + incidentId);
        }
        incident.dismissDuplicateSuggestion();
        Incident saved = incidents.save(incident);
        audit.record("DUPLICATE_DISMISSED", "INCIDENT", incidentId,
                technicianId + " decided it is not the same problem as " + suggested);
        return saved;
    }

    /**
     * Undo a wrong link. technicianId is the technician doing it, or null for the admin.
     * The report becomes an independent incident again: normal assignment, it counts towards
     * workload again, and its own SLA and escalation apply (its clock never stopped).
     */
    @Transactional
    public Incident unlink(String incidentId, String technicianId) {
        Incident child = technicianId == null ? incidents.findById(incidentId)
                                              : ops.loadAssignedTo(incidentId, technicianId);
        String formerParent = child.getParentId();
        child.unlink();

        Technician next = ops.pickWithinLimit(child.getCategory(), technicians.findAll());
        next.incrementActiveIncidents();
        technicians.save(next);
        child.setAssignedTechnician(next);
        if (child.getStatus() != IncidentStatus.ASSIGNED) {
            child.transitionTo(IncidentStatus.ASSIGNED);
        }
        child.setAcknowledgedAt(null);   // its new technician must respond to it on its own

        Incident saved = incidents.save(child);
        events.publishEvent(new IncidentEvent(IncidentEventType.UNLINKED, saved));
        audit.record("INCIDENT_UNLINKED", "INCIDENT", incidentId,
                "Unlinked from " + formerParent + " by " + (technicianId == null ? "admin" : technicianId)
                        + "; assigned to " + next.getId());
        return saved;
    }
}
