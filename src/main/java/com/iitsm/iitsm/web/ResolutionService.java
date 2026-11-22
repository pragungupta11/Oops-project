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
 * The end of the lifecycle: the employee confirms the fix or reopens the incident, unconfirmed
 * incidents close automatically, and the admin can close one as an exception.
 */
@Service
public class ResolutionService {
    private final IncidentPersistenceService incidents;
    private final TechnicianPersistenceService technicians;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final IncidentCommentRepository comments;
    private final IncidentOperations ops;

    public ResolutionService(IncidentPersistenceService incidents,
            TechnicianPersistenceService technicians,
            AuditService audit,
            ApplicationEventPublisher events,
            IncidentCommentRepository comments,
            IncidentOperations ops) {
        this.incidents = incidents;
        this.technicians = technicians;
        this.audit = audit;
        this.events = events;
        this.comments = comments;
        this.ops = ops;
    }

    /** The employee confirms the fix works: RESOLVED -> VERIFIED -> CLOSED. */
    @Transactional
    public Incident confirmResolution(String incidentId, String employee) {
        Incident incident = loadResolvedOwnIncident(incidentId, employee);
        incident.transitionTo(IncidentStatus.VERIFIED);
        ops.saveAndCascade(incident);
        incident.transitionTo(IncidentStatus.CLOSED);
        incident.markClosed(employee);
        Incident saved = ops.saveAndCascade(incident);
        audit.record("RESOLUTION_CONFIRMED", "INCIDENT", incidentId, employee + " confirmed the fix works");
        events.publishEvent(new IncidentEvent(IncidentEventType.CLOSED, saved));
        return saved;
    }

    /**
     * The employee says it is still broken: RESOLVED -> IN_PROGRESS with the same technician,
     * who gets the workload back. The reason goes into the conversation.
     */
    @Transactional
    public Incident reopen(String incidentId, String employee, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidIncidentDataException("Please tell the technician what is still wrong");
        }
        String why = reason.trim();
        if (why.length() > 1000) {
            throw new InvalidIncidentDataException("Please keep it under 1000 characters");
        }
        Incident incident = loadResolvedOwnIncident(incidentId, employee);
        String previousFix = incident.getResolution();
        incident.transitionTo(IncidentStatus.IN_PROGRESS);
        incident.setResolvedAt(null);
        incident.setResolution(null);

        Technician tech = incident.getAssignedTechnician();
        String handedOver = null;
        if (tech != null) {
            Technician original = technicians.findById(tech.getId());
            Technician next = original;
            if (!original.isAvailable()) {
                // The original technician left or is on leave: give it to an available skilled one.
                try {
                    next = ops.pickWithinLimit(incident.getCategory(), technicians.findAll().stream()
                            .filter(t -> !t.getId().equals(original.getId())).toList());
                    incident.setAssignedTechnician(next);
                    incident.transitionTo(IncidentStatus.ASSIGNED);   // the new technician must acknowledge it
                    handedOver = next.getId();
                } catch (TechnicianUnavailableException nobody) {
                    next = original;   // stays listed under "Waiting for a technician" for the admin
                }
            }
            next.incrementActiveIncidents();
            technicians.save(next);
        }
        Incident saved = incidents.save(incident);

        // linked reports were resolved together with it, so they reopen together too
        for (Incident child : incidents.findLinkedTo(incidentId)) {
            if (child.getStatus() == IncidentStatus.RESOLVED) {
                child.transitionTo(IncidentStatus.IN_PROGRESS);
                child.setResolvedAt(null);
                child.setResolution(null);
                incidents.save(child);
            }
        }
        comments.save(new IncidentCommentEntity(incidentId, employee, "EMPLOYEE",
                CommentKind.MESSAGE, "Still not fixed: " + why));
        audit.record("INCIDENT_REOPENED", "INCIDENT", incidentId,
                "Reopened by " + employee + ": " + why + (previousFix == null ? "" : " (previous fix: " + previousFix + ")")
                        + (handedOver == null ? "" : "; original technician unavailable, handed over to " + handedOver));
        events.publishEvent(new IncidentEvent(IncidentEventType.REOPENED, saved));
        return saved;
    }

    /**
     * Admin override for exceptions (dispute, employee unavailable, duplicate): closes a resolved
     * incident without waiting for the employee. Routine confirmation is the employee's job.
     */
    @Transactional
    public Incident adminClose(String incidentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidIncidentDataException("Give a reason for closing without the employee's confirmation");
        }
        Incident incident = incidents.findById(incidentId);
        if (incident.isLinked()) {
            throw new InvalidIncidentDataException("This report closes together with " + incident.getParentId());
        }
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            incident.transitionTo(IncidentStatus.VERIFIED);
            ops.saveAndCascade(incident);
        }
        incident.transitionTo(IncidentStatus.CLOSED);   // throws if it was not resolved/verified
        incident.markClosed(AuditService.currentActor());
        Incident saved = ops.saveAndCascade(incident);
        audit.record("CLOSED_BY_ADMIN", "INCIDENT", incidentId, "Closed without the employee's confirmation: " + reason.trim());
        events.publishEvent(new IncidentEvent(IncidentEventType.CLOSED, saved));
        return saved;
    }

    /**
     * Closes resolved incidents nobody confirmed or reopened since {@code cutoff}.
     * Linked reports are skipped here: they close together with their parent.
     */
    @Transactional
    public int autoCloseResolvedBefore(LocalDateTime cutoff) {
        int closed = 0;
        for (Incident incident : incidents.findByStatus(IncidentStatus.RESOLVED)) {
            if (incident.isLinked() || incident.getResolvedAt() == null || !incident.getResolvedAt().isBefore(cutoff)) {
                continue;
            }
            incident.transitionTo(IncidentStatus.VERIFIED);
            ops.saveAndCascade(incident);
            incident.transitionTo(IncidentStatus.CLOSED);
            incident.markClosed("system (auto-close)");
            Incident saved = ops.saveAndCascade(incident);
            audit.record("AUTO_CLOSED", "INCIDENT", incident.getId(),
                    "Closed automatically: no answer from the employee since " + incident.getResolvedAt());
            events.publishEvent(new IncidentEvent(IncidentEventType.CLOSED, saved));
            closed++;
        }
        return closed;
    }

    private Incident loadResolvedOwnIncident(String incidentId, String employee) {
        Incident incident = incidents.findById(incidentId);
        if (!employee.equals(incident.getReporterUsername())) {
            throw new AccessDeniedException("This is not your incident");
        }
        if (incident.isLinked()) {
            throw new InvalidIncidentDataException("This report follows a larger incident and closes together with it");
        }
        if (incident.getStatus() != IncidentStatus.RESOLVED) {
            throw new InvalidIncidentDataException("Only a resolved incident can be confirmed or reopened");
        }
        return incident;
    }
}
