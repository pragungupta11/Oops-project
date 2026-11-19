package com.iitsm.iitsm.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.factory.IncidentFactory;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.DuplicateMatch;
import com.iitsm.iitsm.model.Employee;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.ItService;
import com.iitsm.iitsm.model.ProcessingResult;
import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.model.User;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;
import com.iitsm.iitsm.service.DuplicateDetectionService;
import com.iitsm.iitsm.service.IncidentProcessingService;

/** Glue between the web pages and the backend services: one method per user action. */
@Service
public class IncidentWorkflowService {
    private static final int MAX_DESCRIPTION = 2000;

    private final IncidentFactory factory;
    private final IncidentProcessingService processing;
    private final DuplicateDetectionService duplicateDetection;
    private final IncidentPersistenceService incidents;
    private final TechnicianPersistenceService technicians;
    private final ServiceCatalog catalog;

    public IncidentWorkflowService(IncidentFactory factory,
                                   IncidentProcessingService processing,
                                   DuplicateDetectionService duplicateDetection,
                                   IncidentPersistenceService incidents,
                                   TechnicianPersistenceService technicians,
                                   ServiceCatalog catalog) {
        this.factory = factory;
        this.processing = processing;
        this.duplicateDetection = duplicateDetection;
        this.incidents = incidents;
        this.technicians = technicians;
        this.catalog = catalog;
    }

    public record ReportOutcome(Incident incident, ProcessingResult result, List<DuplicateMatch> duplicates) {}

    /** Employee reports an incident: validate, check duplicates, classify, assign, save. */
    @Transactional
    public ReportOutcome report(String username, String description, Category category,
                                int impact, int urgency, String serviceName) {
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new InvalidIncidentDataException("Description must be at most " + MAX_DESCRIPTION + " characters");
        }
        User reporter = new Employee(username, username, username + "@company.com", "General");
        ItService service = catalog.byName(serviceName);

        List<Incident> existing = incidents.findAll();
        Set<String> takenIds = existing.stream().map(Incident::getId).collect(Collectors.toSet());

        // The factory's id counter restarts at 1000 on every app start, so skip ids already
        // in the database; otherwise a new incident would overwrite an old one.
        Incident incident;
        do {
            incident = factory.create(reporter, description, category, impact, urgency, service);
        } while (takenIds.contains(incident.getId()));
        incident.setReporterUsername(username);
        incident.setServiceName(service.getName());

        List<DuplicateMatch> matches = duplicateDetection.findPossibleDuplicates(incident, existing);

        List<Technician> pool = technicians.findAll();
        ProcessingResult result = processing.process(incident, pool);

        technicians.save(result.technician());      // technician first (the incident links to him)
        Incident saved = incidents.save(incident);
        return new ReportOutcome(saved, result, matches);
    }

    /** Technician confirms he has seen the incident: ASSIGNED -> ACKNOWLEDGED. */
    @Transactional
    public Incident acknowledge(String incidentId, String technicianId) {
        Incident incident = loadAssignedTo(incidentId, technicianId);
        incident.transitionTo(IncidentStatus.ACKNOWLEDGED);
        incident.setAcknowledgedAt(LocalDateTime.now());
        return incidents.save(incident);
    }

    /** Technician starts working: ACKNOWLEDGED -> IN_PROGRESS. */
    @Transactional
    public Incident start(String incidentId, String technicianId) {
        Incident incident = loadAssignedTo(incidentId, technicianId);
        incident.transitionTo(IncidentStatus.IN_PROGRESS);
        return incidents.save(incident);
    }

    /** Technician finishes: IN_PROGRESS -> RESOLVED, stores the notes and frees one workload slot. */
    @Transactional
    public Incident resolve(String incidentId, String technicianId, String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw new InvalidIncidentDataException("Please describe how the incident was resolved");
        }
        if (resolution.length() > MAX_DESCRIPTION) {
            throw new InvalidIncidentDataException("Resolution notes must be at most " + MAX_DESCRIPTION + " characters");
        }
        Incident incident = loadAssignedTo(incidentId, technicianId);
        incident.transitionTo(IncidentStatus.RESOLVED);
        incident.setResolvedAt(LocalDateTime.now());
        incident.setResolution(resolution.trim());

        Technician technician = technicians.findById(technicianId);
        technician.decrementActiveIncidents();
        technicians.save(technician);
        return incidents.save(incident);
    }

    /** Loads an incident and makes sure it belongs to this technician. */
    private Incident loadAssignedTo(String incidentId, String technicianId) {
        Incident incident = incidents.findById(incidentId);
        Technician assigned = incident.getAssignedTechnician();
        if (assigned == null || !assigned.getId().equals(technicianId)) {
            throw new AccessDeniedException("This incident is not assigned to you");
        }
        return incident;
    }

    /** Admin checks the fix: RESOLVED -> VERIFIED. */
    @Transactional
    public Incident verify(String incidentId) {
        Incident incident = incidents.findById(incidentId);
        incident.transitionTo(IncidentStatus.VERIFIED);
        return incidents.save(incident);
    }

    /** Admin closes the incident: VERIFIED -> CLOSED. */
    @Transactional
    public Incident close(String incidentId) {
        Incident incident = incidents.findById(incidentId);
        incident.transitionTo(IncidentStatus.CLOSED);
        return incidents.save(incident);
    }
}
