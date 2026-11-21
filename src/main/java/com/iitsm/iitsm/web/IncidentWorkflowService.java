package com.iitsm.iitsm.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.exception.TechnicianUnavailableException;
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
import com.iitsm.iitsm.notification.IncidentEvent;
import com.iitsm.iitsm.notification.IncidentEventType;
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
    private final ApplicationEventPublisher events;
    private final com.iitsm.iitsm.repository.IncidentCommentRepository comments;
    private final int maxReportsPerHour;

    public IncidentWorkflowService(IncidentFactory factory,
                                   IncidentProcessingService processing,
                                   DuplicateDetectionService duplicateDetection,
                                   IncidentPersistenceService incidents,
                                   TechnicianPersistenceService technicians,
                                   ServiceCatalog catalog,
                                   AuditService audit,
                                   ApplicationEventPublisher events,
                                   com.iitsm.iitsm.repository.IncidentCommentRepository comments,
                                   @org.springframework.beans.factory.annotation.Value("${app.max-reports-per-hour:5}") int maxReportsPerHour) {
        this.factory = factory;
        this.processing = processing;
        this.duplicateDetection = duplicateDetection;
        this.incidents = incidents;
        this.technicians = technicians;
        this.catalog = catalog;
        this.audit = audit;
        this.events = events;
        this.comments = comments;
        this.maxReportsPerHour = maxReportsPerHour;
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

        // Guardrail against floods of fake reports from one account.
        LocalDateTime hourAgo = LocalDateTime.now().minusHours(1);
        long recent = existing.stream()
                .filter(i -> username.equals(i.getReporterUsername()) && i.getCreatedAt().isAfter(hourAgo))
                .count();
        if (recent >= maxReportsPerHour) {
            throw new InvalidIncidentDataException("You have reported " + recent + " incidents in the last hour. "
                    + "Please add details to an existing incident, or wait a little before reporting more.");
        }

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
        events.publishEvent(new IncidentEvent(IncidentEventType.REPORTED, saved));
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
        Incident saved = saveAndCascade(incident);
        events.publishEvent(new IncidentEvent(IncidentEventType.RESOLVED, saved));
        return saved;
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
        Incident saved = saveAndCascade(incident);
        events.publishEvent(new IncidentEvent(IncidentEventType.CLOSED, saved));
        return saved;
    }

    // ------------------------------------------------------------ employee confirms or reopens

    /** The employee confirms the fix works: RESOLVED -> VERIFIED -> CLOSED. */
    @Transactional
    public Incident confirmResolution(String incidentId, String employee) {
        Incident incident = loadResolvedOwnIncident(incidentId, employee);
        incident.transitionTo(IncidentStatus.VERIFIED);
        saveAndCascade(incident);
        incident.transitionTo(IncidentStatus.CLOSED);
        Incident saved = saveAndCascade(incident);
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
        if (tech != null) {
            Technician fresh = technicians.findById(tech.getId());
            fresh.incrementActiveIncidents();
            technicians.save(fresh);
        }
        Incident saved = incidents.save(incident);

        // linked reports were resolved together with it, so they reopen together too
        for (Incident child : incidents.findAll()) {
            if (incidentId.equals(child.getParentId()) && child.getStatus() == IncidentStatus.RESOLVED) {
                child.transitionTo(IncidentStatus.IN_PROGRESS);
                child.setResolvedAt(null);
                child.setResolution(null);
                incidents.save(child);
            }
        }
        comments.save(new com.iitsm.iitsm.persistence.entity.IncidentCommentEntity(incidentId, employee, "EMPLOYEE",
                com.iitsm.iitsm.model.CommentKind.MESSAGE, "Still not fixed: " + why));
        audit.record("INCIDENT_REOPENED", "INCIDENT", incidentId,
                "Reopened by " + employee + ": " + why + (previousFix == null ? "" : " (previous fix: " + previousFix + ")"));
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
            saveAndCascade(incident);
        }
        incident.transitionTo(IncidentStatus.CLOSED);   // throws if it was not resolved/verified
        Incident saved = saveAndCascade(incident);
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
            saveAndCascade(incident);
            incident.transitionTo(IncidentStatus.CLOSED);
            Incident saved = saveAndCascade(incident);
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

    // ------------------------------------------------------------ technician unavailable / deactivated

    /** Result of handing over a technician's open incidents. */
    public record HandoverResult(int moved, List<String> notMoved) {}

    /**
     * Hands over every open incident of a technician who is unavailable or deactivated to other
     * available technicians with the right skill (active assignment strategy). Incidents nobody
     * else can take stay where they are and are reported back to the admin.
     */
    @Transactional
    public HandoverResult reassignOpenIncidentsOf(String technicianId) {
        int moved = 0;
        List<String> notMoved = new java.util.ArrayList<>();
        for (Incident incident : incidents.findAll()) {
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
                next = processing.getAssignmentStrategy().pick(incident.getCategory(), others);
            } catch (TechnicianUnavailableException e) {
                notMoved.add(incident.getId());
                continue;
            }
            moveTo(incident, next, "automatic, matching skill");
            moved++;
        }
        return new HandoverResult(moved, notMoved);
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
        return moveTo(incident, target, "chosen by admin");
    }

    /** Moves an open incident to another technician: workloads, status, audit and notifications. */
    private Incident moveTo(Incident incident, Technician next, String how) {
        Technician previous = incident.getAssignedTechnician();
        next.incrementActiveIncidents();
        technicians.save(next);
        if (previous != null) {
            Technician old = technicians.findById(previous.getId());
            old.decrementActiveIncidents();
            technicians.save(old);
        }
        incident.setAssignedTechnician(next);
        if (incident.getStatus() == IncidentStatus.ACKNOWLEDGED || incident.getStatus() == IncidentStatus.IN_PROGRESS) {
            incident.transitionTo(IncidentStatus.ASSIGNED);   // the new technician must acknowledge it
        }
        Incident saved = saveAndCascade(incident);
        audit.record("INCIDENT_REASSIGNED", "INCIDENT", incident.getId(), "Handed over from "
                + (previous == null ? "-" : previous.getId()) + " to " + next.getId() + " (" + how + ")");
        events.publishEvent(new IncidentEvent(IncidentEventType.REASSIGNED, saved,
                previous == null ? null : previous.getId()));
        return saved;
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
        events.publishEvent(new IncidentEvent(IncidentEventType.LINKED, saved));
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
        events.publishEvent(new IncidentEvent(IncidentEventType.UNLINKED, saved));
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
