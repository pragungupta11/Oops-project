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
 * Building blocks shared by the incident services: picking a technician within the workload limit,
 * moving an incident to another technician, saving an incident together with its linked reports,
 * and the lifecycle checks. Kept in one place so every service applies the same rules.
 */
@Component
public class IncidentOperations {
    private final IncidentPersistenceService incidents;
    private final TechnicianPersistenceService technicians;
    private final IncidentProcessingService processing;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final int maxActiveIncidents;

    public IncidentOperations(IncidentPersistenceService incidents,
            TechnicianPersistenceService technicians,
            IncidentProcessingService processing,
            AuditService audit,
            ApplicationEventPublisher events,
            @Value("${app.max-active-incidents:8}") int maxActiveIncidents) {
        this.incidents = incidents;
        this.technicians = technicians;
        this.processing = processing;
        this.audit = audit;
        this.events = events;
        this.maxActiveIncidents = maxActiveIncidents;
    }

    /** Technicians who are below the maximum number of active incidents. */
    List<Technician> underLimit(List<Technician> candidates) {
        return candidates.stream().filter(t -> t.getActiveIncidents() < maxActiveIncidents).toList();
    }

    /**
     * Picks with the active strategy among technicians below the workload limit. If only the
     * limit stands in the way, throws TechnicianWorkloadExceededException; if nobody has the skill
     * or is available, TechnicianUnavailableException.
     */
    Technician pickWithinLimit(String category, List<Technician> candidates) {
        try {
            return processing.getAssignmentStrategy().pick(category, underLimit(candidates));
        } catch (TechnicianUnavailableException none) {
            if (whyNobodyFits(category, candidates).startsWith("all")) {
                throw new TechnicianWorkloadExceededException(whyNobodyFits(category, candidates));
            }
            throw none;
        }
    }

    String whyNobodyFits(String category, List<Technician> candidates) {
        try {
            processing.getAssignmentStrategy().pick(category, candidates);   // without the limit
            return "all technicians who could take it already have " + maxActiveIncidents + " active incidents";
        } catch (TechnicianUnavailableException e) {
            return "no available technician has the " + category + " skill";
        }
    }

    /** A linked report follows its parent automatically; work happens on the parent. */
    static void requireNotLinked(Incident incident) {
        if (incident.isLinked()) {
            throw new InvalidIncidentDataException(incident.getId() + " is linked to " + incident.getParentId()
                    + "; work on " + incident.getParentId() + " and this report will follow it");
        }
    }

    /** Loads an incident and makes sure it belongs to this technician. */
    Incident loadAssignedTo(String incidentId, String technicianId) {
        Incident incident = incidents.findById(incidentId);
        Technician assigned = incident.getAssignedTechnician();
        if (assigned == null || !assigned.getId().equals(technicianId)) {
            throw new AccessDeniedException("This incident is not assigned to you");
        }
        return incident;
    }

    /** Moves an open incident to another technician: workloads, status, audit and notifications. */
    Incident moveTo(Incident incident, Technician next, String how) {
        Technician previous = incident.getAssignedTechnician();
        next.incrementActiveIncidents();
        technicians.save(next);
        if (previous != null) {
            Technician old = technicians.findById(previous.getId());
            old.decrementActiveIncidents();
            technicians.save(old);
        }
        incident.setAssignedTechnician(next);
        if (incident.getStatus() == IncidentStatus.ACKNOWLEDGED || incident.getStatus() == IncidentStatus.IN_PROGRESS
                || incident.getStatus() == IncidentStatus.CLASSIFIED) {
            incident.transitionTo(IncidentStatus.ASSIGNED);   // the new technician must acknowledge it
        }
        Incident saved = saveAndCascade(incident);
        audit.record("INCIDENT_REASSIGNED", "INCIDENT", incident.getId(), "Handed over from "
                + (previous == null ? "-" : previous.getId()) + " to " + next.getId() + " (" + how + ")");
        events.publishEvent(new IncidentEvent(IncidentEventType.REASSIGNED, saved,
                previous == null ? null : previous.getId()));
        return saved;
    }

    /** Saves an incident and brings its linked reports along (same technician, same progress). */
    Incident saveAndCascade(Incident parent) {
        Incident saved = incidents.save(parent);
        for (Incident child : incidents.findLinkedTo(parent.getId())) {
            IncidentStatus before = child.getStatus();
            child.setAssignedTechnician(parent.getAssignedTechnician());
            catchUpWithParent(child, parent);
            Incident savedChild = incidents.save(child);
            if (before != savedChild.getStatus()) {
                if (savedChild.getStatus() == IncidentStatus.RESOLVED) {
                    events.publishEvent(new IncidentEvent(IncidentEventType.RESOLVED, savedChild));
                } else if (savedChild.getStatus() == IncidentStatus.CLOSED) {
                    events.publishEvent(new IncidentEvent(IncidentEventType.CLOSED, savedChild));
                }
            }
        }
        return saved;
    }

    /**
     * Keeps a linked report in step with its parent: forward one legal step at a time, and back to
     * ASSIGNED when the parent went back there (handover or re-routing to a new technician).
     */
    void catchUpWithParent(Incident child, Incident parent) {
        if (parent.getStatus() == IncidentStatus.ASSIGNED
                && (child.getStatus() == IncidentStatus.ACKNOWLEDGED || child.getStatus() == IncidentStatus.IN_PROGRESS)) {
            child.transitionTo(IncidentStatus.ASSIGNED);   // legal: ACKNOWLEDGED/IN_PROGRESS -> ASSIGNED
            return;
        }
        int target = HAPPY_PATH.indexOf(parent.getStatus());
        int current = HAPPY_PATH.indexOf(child.getStatus());
        while (current < target) {
            IncidentStatus next = HAPPY_PATH.get(++current);
            child.transitionTo(next);
            if (next == IncidentStatus.ACKNOWLEDGED && child.getAcknowledgedAt() == null) {
                child.setAcknowledgedAt(LocalDateTime.now());
            }
            if (next == IncidentStatus.RESOLVED) {
                child.setResolvedAt(LocalDateTime.now());
                child.setResolution(parent.getResolution());
            }
            if (next == IncidentStatus.CLOSED) {
                child.markClosed(parent.getClosedBy());
            }
        }
    }

    static final List<IncidentStatus> HAPPY_PATH = List.of(
            IncidentStatus.NEW, IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED,
            IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED,
            IncidentStatus.VERIFIED, IncidentStatus.CLOSED);

    /** Technicians with this many active incidents get no new automatic assignments. */
    int maxActiveIncidents() {
        return maxActiveIncidents;
    }
}
