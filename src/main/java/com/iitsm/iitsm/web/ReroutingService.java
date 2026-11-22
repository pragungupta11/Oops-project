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
 * Re-routing: a technician reports a wrong category and an admin approves (the incident moves to a
 * technician with the right skill) or rejects the request.
 */
@Service
public class ReroutingService {
    private final IncidentPersistenceService incidents;
    private final TechnicianPersistenceService technicians;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final IncidentOperations ops;

    public ReroutingService(IncidentPersistenceService incidents,
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

    /** Technician says the category is wrong and asks the admin to re-route the incident. */
    @Transactional
    public Incident requestReassignment(String incidentId, String technicianId,
                                        String correctCategory, String reason) {
        Incident incident = ops.loadAssignedTo(incidentId, technicianId);
        String oldCategory = incident.getCategory();
        incident.requestReassignment(correctCategory, reason);
        Incident saved = incidents.save(incident);
        audit.record("REASSIGNMENT_REQUESTED", "INCIDENT", incidentId,
                "Category " + oldCategory + " -> " + correctCategory + " requested by " + technicianId
                        + ": " + incident.getReassignmentReason());
        return saved;
    }

    /**
     * Admin approves a re-routing. technicianId "auto" lets the active assignment strategy pick
     * (excluding the current technician); otherwise the admin's chosen technician is used.
     */
    @Transactional
    public Incident approveReassignment(String incidentId, String technicianId) {
        Incident incident = incidents.findById(incidentId);
        if (!incident.hasPendingReassignment()) {
            throw new InvalidIncidentDataException("There is no re-routing request for " + incidentId);
        }
        String newCategory = incident.getRequestedCategory();
        Technician previous = incident.getAssignedTechnician();

        Technician next;
        if (technicianId == null || technicianId.isBlank() || technicianId.equals("auto")) {
            List<Technician> pool = technicians.findAll().stream()
                    .filter(t -> previous == null || !t.getId().equals(previous.getId()))
                    .toList();
            next = ops.pickWithinLimit(newCategory, pool);
        } else {
            next = technicians.findById(technicianId);
        }

        String oldCategory = incident.getCategory();
        incident.approveReassignment(next);

        if (previous != null && !previous.getId().equals(next.getId())) {
            Technician old = technicians.findById(previous.getId());
            old.decrementActiveIncidents();
            technicians.save(old);
            next.incrementActiveIncidents();
            technicians.save(next);
        }
        Incident saved = ops.saveAndCascade(incident);
        events.publishEvent(new IncidentEvent(IncidentEventType.REROUTED, saved,
                previous == null ? null : previous.getId()));
        audit.record("REASSIGNMENT_APPROVED", "INCIDENT", incidentId,
                "Category " + oldCategory + " -> " + newCategory + "; technician "
                        + (previous == null ? "-" : previous.getId()) + " -> " + next.getId());
        return saved;
    }

    /** Admin rejects a re-routing: the incident stays as it was. */
    @Transactional
    public Incident rejectReassignment(String incidentId) {
        Incident incident = incidents.findById(incidentId);
        String requested = incident.getRequestedCategory();
        incident.rejectReassignment();
        Incident saved = incidents.save(incident);
        audit.record("REASSIGNMENT_REJECTED", "INCIDENT", incidentId,
                "Request to move to " + requested + " was rejected");
        return saved;
    }
}
