package com.iitsm.iitsm.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.iitsm.audit.AuditService;
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
    private final AuditService audit;

    public IncidentWorkflowService(IncidentFactory factory,
                                   IncidentProcessingService processing,
                                   DuplicateDetectionService duplicateDetection,
                                   IncidentPersistenceService incidents,
                                   TechnicianPersistenceService technicians,
                                   ServiceCatalog catalog,
                                   AuditService audit) {
        this.factory = factory;
        this.processing = processing;
        this.duplicateDetection = duplicateDetection;
        this.incidents = incidents;
        this.technicians = technicians;
        this.catalog = catalog;
        this.audit = audit;
    }

    public record ReportOutcome(Incident incident, ProcessingResult result, List<DuplicateMatch> duplicates) {}

    /**
     * A suspected duplicate goes to the technician already handling the original problem, but
     * only while that technician has fewer active incidents than this; otherwise normal
     * load balancing wins.
     */
    static final int ROUTING_LOAD_LIMIT = 5;

    private static final List<IncidentStatus> HAPPY_PATH = List.of(
            IncidentStatus.NEW, IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED,
            IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED,
            IncidentStatus.VERIFIED, IncidentStatus.CLOSED);

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

        // Duplicate hint: never linked automatically. The best match is only remembered as a
        // suggestion, and if possible the report is routed to the technician who already
        // knows the problem, so ONE person reviews it.
        if (!matches.isEmpty()) {
            Incident suspected = rootOf(matches.get(0).incident(), existing);
            if (suspected != null && !suspected.isFinished() && suspected.getAssignedTechnician() != null) {
                incident.suggestDuplicateOf(suspected.getId(), (int) Math.round(matches.get(0).score() * 100));
                Technician sameTechnician = routableTechnician(suspected, incident, pool);
                if (sameTechnician != null) {
                    pool = List.of(sameTechnician);
                }
            }
        }
        ProcessingResult result = processing.process(incident, pool);

        technicians.save(result.technician());      // technician first (the incident links to him)
        Incident saved = incidents.save(incident);
        return new ReportOutcome(saved, result, matches);
    }

    /** Technician confirms he has seen the incident: ASSIGNED -> ACKNOWLEDGED. */
    @Transactional
    public Incident acknowledge(String incidentId, String technicianId) {
        Incident incident = loadAssignedTo(incidentId, technicianId);
        requireNotLinked(incident);
        incident.transitionTo(IncidentStatus.ACKNOWLEDGED);
        // Keep the first acknowledgement: after a re-routing the response SLA was already met.
        if (incident.getAcknowledgedAt() == null) {
            incident.setAcknowledgedAt(LocalDateTime.now());
        }
        return saveAndCascade(incident);
    }

    /** Technician starts working: ACKNOWLEDGED -> IN_PROGRESS. */
    @Transactional
    public Incident start(String incidentId, String technicianId) {
        Incident incident = loadAssignedTo(incidentId, technicianId);
        requireNotLinked(incident);
        incident.transitionTo(IncidentStatus.IN_PROGRESS);
        return saveAndCascade(incident);
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
        requireNotLinked(incident);
        incident.transitionTo(IncidentStatus.RESOLVED);
        incident.setResolvedAt(LocalDateTime.now());
        incident.setResolution(resolution.trim());

        Technician technician = technicians.findById(technicianId);
        technician.decrementActiveIncidents();
        technicians.save(technician);
        return saveAndCascade(incident);
    }

    /** A linked report follows its parent automatically; work happens on the parent. */
    private static void requireNotLinked(Incident incident) {
        if (incident.isLinked()) {
            throw new InvalidIncidentDataException(incident.getId() + " is linked to " + incident.getParentId()
                    + "; work on " + incident.getParentId() + " and this report will follow it");
        }
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
        return saveAndCascade(incident);
    }

    /** Admin closes the incident: VERIFIED -> CLOSED. */
    @Transactional
    public Incident close(String incidentId) {
        Incident incident = incidents.findById(incidentId);
        incident.transitionTo(IncidentStatus.CLOSED);
        return saveAndCascade(incident);
    }

    // ------------------------------------------------------------ re-routing (reassignment)

    /** Technician says the category is wrong and asks the admin to re-route the incident. */
    @Transactional
    public Incident requestReassignment(String incidentId, String technicianId,
                                        String correctCategory, String reason) {
        Incident incident = loadAssignedTo(incidentId, technicianId);
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
            next = processing.getAssignmentStrategy().pick(newCategory, pool);
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
        Incident saved = saveAndCascade(incident);
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

    // ------------------------------------------------------------ duplicates (manual linking)

    /** Technician confirms the system's hint: this report is the same problem as the suggested incident. */
    @Transactional
    public Incident linkToSuggestedParent(String incidentId, String technicianId) {
        Incident child = loadAssignedTo(incidentId, technicianId);
        if (!child.hasDuplicateSuggestion()) {
            throw new InvalidIncidentDataException("There is no duplicate suggestion for " + incidentId);
        }
        boolean hasOwnChildren = incidents.findAll().stream().anyMatch(i -> incidentId.equals(i.getParentId()));
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

        catchUpWithParent(child, parent);
        Incident saved = incidents.save(child);
        audit.record("DUPLICATE_LINKED", "INCIDENT", incidentId,
                "Linked to " + parent.getId() + " (" + percent + "% match) by " + technicianId
                        + "; now handled by " + parent.getAssignedTechnician().getId());
        return saved;
    }

    /** Technician checked the hint and decided it is a different problem. */
    @Transactional
    public Incident dismissDuplicateSuggestion(String incidentId, String technicianId) {
        Incident incident = loadAssignedTo(incidentId, technicianId);
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
                                              : loadAssignedTo(incidentId, technicianId);
        String formerParent = child.getParentId();
        child.unlink();

        Technician next = processing.getAssignmentStrategy().pick(child.getCategory(), technicians.findAll());
        next.incrementActiveIncidents();
        technicians.save(next);
        child.setAssignedTechnician(next);
        if (child.getStatus() != IncidentStatus.ASSIGNED) {
            child.transitionTo(IncidentStatus.ASSIGNED);
        }
        child.setAcknowledgedAt(null);   // its new technician must respond to it on its own

        Incident saved = incidents.save(child);
        audit.record("INCIDENT_UNLINKED", "INCIDENT", incidentId,
                "Unlinked from " + formerParent + " by " + (technicianId == null ? "admin" : technicianId)
                        + "; assigned to " + next.getId());
        return saved;
    }

    /** Saves an incident and brings its linked reports along (same technician, same progress). */
    private Incident saveAndCascade(Incident parent) {
        Incident saved = incidents.save(parent);
        for (Incident child : incidents.findAll()) {
            if (parent.getId().equals(child.getParentId())) {
                child.setAssignedTechnician(parent.getAssignedTechnician());
                catchUpWithParent(child, parent);
                incidents.save(child);
            }
        }
        return saved;
    }

    /** Moves a linked report forward, one legal step at a time, until it matches its parent. */
    private void catchUpWithParent(Incident child, Incident parent) {
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
        }
    }

    /** A match may itself be a linked report: the real original is its parent. */
    private Incident rootOf(Incident match, List<Incident> existing) {
        if (!match.isLinked()) {
            return match;
        }
        return existing.stream().filter(i -> i.getId().equals(match.getParentId())).findFirst().orElse(null);
    }

    /** The original's technician, if it is sensible to give him this report as well. */
    private Technician routableTechnician(Incident original, Incident report, List<Technician> pool) {
        if (original.getServiceName() == null || !original.getServiceName().equals(report.getServiceName())) {
            return null;
        }
        String techId = original.getAssignedTechnician().getId();
        return pool.stream()
                .filter(t -> t.getId().equals(techId))
                .filter(Technician::isAvailable)
                .filter(t -> t.getSkills().contains(report.getCategory()))
                .filter(t -> t.getActiveIncidents() < ROUTING_LOAD_LIMIT)
                .findFirst()
                .orElse(null);
    }
}
