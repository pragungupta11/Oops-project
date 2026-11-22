package com.iitsm.iitsm.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;
import com.iitsm.iitsm.model.AlertType;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaStatus;
import com.iitsm.iitsm.service.CategoryCatalog;
import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.iitsm.exception.TechnicianWorkloadExceededException;
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

/**
 * Facade between the web pages and the incident services: one method per user action.
 * It handles reporting and the core lifecycle itself (report, acknowledge, start, resolve,
 * verify, close, priority change) and delegates the specialised use cases to focused services:
 * {@link TechnicianHandoverService}, {@link ResolutionService}, {@link ReroutingService} and
 * {@link DuplicateLinkingService}. Shared rules live in {@link IncidentOperations}.
 */
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
    private final int maxReportsPerHour;
    private final CategoryCatalog categories;
    private final IncidentOperations ops;
    private final TechnicianHandoverService handover;
    private final ResolutionService resolutionService;
    private final ReroutingService rerouting;
    private final DuplicateLinkingService linking;

    public IncidentWorkflowService(IncidentFactory factory,
                                   IncidentProcessingService processing,
                                   DuplicateDetectionService duplicateDetection,
                                   IncidentPersistenceService incidents,
                                   TechnicianPersistenceService technicians,
                                   ServiceCatalog catalog,
                                   AuditService audit,
                                   ApplicationEventPublisher events,
                                   @Value("${app.max-reports-per-hour:20}") int maxReportsPerHour,
                                   CategoryCatalog categories,
                                   IncidentOperations ops,
                                   TechnicianHandoverService handover,
                                   ResolutionService resolutionService,
                                   ReroutingService rerouting,
                                   DuplicateLinkingService linking) {
        this.factory = factory;
        this.processing = processing;
        this.duplicateDetection = duplicateDetection;
        this.incidents = incidents;
        this.technicians = technicians;
        this.catalog = catalog;
        this.audit = audit;
        this.events = events;
        this.maxReportsPerHour = maxReportsPerHour;
        this.categories = categories;
        this.ops = ops;
        this.handover = handover;
        this.resolutionService = resolutionService;
        this.rerouting = rerouting;
        this.linking = linking;
    }

    public record ReportOutcome(Incident incident, ProcessingResult result, List<DuplicateMatch> duplicates) {}

    /**
     * A suspected duplicate goes to the technician already handling the original problem, but
     * only while that technician has fewer active incidents than this; otherwise normal
     * load balancing wins.
     */
    static final int ROUTING_LOAD_LIMIT = 5;

    /** Employee reports an incident: validate, check duplicates, classify, assign, save. */
    @Transactional
    public ReportOutcome report(String username, String description, Category category,
                                int impact, int urgency, String serviceName) {
        if (category == null) throw new InvalidIncidentDataException("Category is required");
        return report(username, description, category.label(), impact, urgency, serviceName);
    }

    /** Same, with the category by name (works for categories the admin added); subject derived. */
    @Transactional
    public ReportOutcome report(String username, String description, String categoryName,
                                int impact, int urgency, String serviceName) {
        return doReport(username, null, description, categoryName, impact, urgency, serviceName);
    }

    /**
     * The employee's report form: a short subject plus a description. serviceName may be empty
     * ("I don't know"): the system then guesses the service from the text, or uses "Not specified".
     */
    @Transactional
    public ReportOutcome report(String username, String subject, String description, String categoryName,
                                int impact, int urgency, String serviceName) {
        if (subject == null || subject.isBlank()) {
            throw new InvalidIncidentDataException("Please enter a subject (a short title for the problem)");
        }
        return doReport(username, subject, description, categoryName, impact, urgency, serviceName);
    }

    private ReportOutcome doReport(String username, String subject, String description, String categoryName,
                                   int impact, int urgency, String serviceName) {
        String category = categories.require(categoryName);
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new InvalidIncidentDataException("Description must be at most " + MAX_DESCRIPTION + " characters");
        }
        User reporter = new Employee(username, username, username + "@company.com", "General");
        String serviceNote = null;
        ItService service;
        if (serviceName == null || serviceName.isBlank()) {
            // "I don't know": the system identifies the service from the text, or uses a neutral placeholder
            String text = (subject == null ? "" : subject + " ") + (description == null ? "" : description);
            service = catalog.guessFromText(text).orElse(catalog.notSpecified());
            serviceNote = service.getName().equals(ServiceCatalog.NOT_SPECIFIED)
                    ? "Employee did not know the service and the system could not tell; set to Not specified"
                    : "Employee did not know the service; identified from the description as " + service.getName();
        } else {
            service = catalog.byName(serviceName);
        }

        // Guardrail against floods of fake reports from one account.
        LocalDateTime hourAgo = LocalDateTime.now().minusHours(1);
        long recent = incidents.findByReporter(username).stream()
                .filter(i -> username.equals(i.getReporterUsername()) && i.getCreatedAt().isAfter(hourAgo))
                .count();
        if (recent >= maxReportsPerHour) {
            throw new InvalidIncidentDataException("You have reported " + recent + " incidents in the last hour. "
                    + "Please add details to an existing incident, or wait a little before reporting more.");
        }

        // The factory's id counter restarts at 1000 on every app start, so skip ids already
        // in the database; otherwise a new incident would overwrite an old one.
        Incident incident;
        do {
            incident = factory.create(reporter, description, category, impact, urgency, service, LocalDateTime.now());
        } while (incidents.exists(incident.getId()));
        incident.setReporterUsername(username);
        incident.setServiceName(service.getName());
        incident.setSubject(subject);

        List<Incident> existing = incidents.findOpen();   // only open incidents can be duplicates
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
        List<Technician> eligible = ops.underLimit(pool);
        if (eligible.isEmpty() && pool.size() == 1) {
            eligible = ops.underLimit(technicians.findAll());   // the routed technician is full: use everyone
        }
        ProcessingResult result;
        try {
            result = processing.process(incident, eligible);
        } catch (TechnicianUnavailableException nobodyFits) {
            // Never lose a report: save it unassigned (status CLASSIFIED) and queue it.
            Incident saved = incidents.save(incident);
            String why = ops.whyNobodyFits(incident.getCategory(), technicians.findAll());
            audit.record("QUEUED_UNASSIGNED", "INCIDENT", saved.getId(), "Waiting for a technician: " + why);
            events.publishEvent(new IncidentEvent(IncidentEventType.QUEUED, saved));
            return new ReportOutcome(saved, null, matches);
        }

        technicians.save(result.technician());      // technician first (the incident links to him)
        Incident saved = incidents.save(incident);
        if (serviceNote != null) {
            audit.record("SERVICE_IDENTIFIED", "INCIDENT", saved.getId(), serviceNote);
        }
        events.publishEvent(new IncidentEvent(IncidentEventType.REPORTED, saved));
        return new ReportOutcome(saved, result, matches);
    }

    /**
     * Technician (or admin, technicianId null) corrects the affected service, e.g. after "I don't know".
     * The service's criticality comes with it, so the priority is recalculated with the active
     * strategy; if it changes, SLA deadlines follow and technician and employee are notified.
     */
    @Transactional
    public Incident changeService(String incidentId, String serviceName, String technicianId) {
        Incident incident = technicianId == null ? incidents.findById(incidentId)
                                                 : ops.loadAssignedTo(incidentId, technicianId);
        IncidentOperations.requireNotLinked(incident);
        ItService service = catalog.byName(serviceName);   // unknown or inactive -> IllegalArgumentException
        String oldService = incident.getServiceName();
        if (service.getName().equals(oldService)) {
            throw new InvalidIncidentDataException(incidentId + " is already set to " + oldService);
        }
        Priority oldPriority = incident.getPriority();
        incident.changeAffectedService(service.getName(), service.getCriticality());
        Priority newPriority = processing.getPriorityStrategy()
                .calculate(incident.getImpact(), incident.getUrgency(), incident.getCriticality());
        boolean priorityChanged = newPriority != oldPriority;
        if (priorityChanged) {
            incident.setPriority(newPriority);
            incident.setReportedSla(AlertType.RESPONSE, SlaStatus.OK);
            incident.setReportedSla(AlertType.RESOLUTION, SlaStatus.OK);
        }
        Incident saved = incidents.save(incident);
        audit.record("SERVICE_CHANGED", "INCIDENT", incidentId, "Affected service " + oldService + " -> "
                + service.getName() + (priorityChanged ? "; priority " + oldPriority + " -> " + newPriority : ""),
                oldService, service.getName());
        if (priorityChanged) {
            events.publishEvent(new IncidentEvent(IncidentEventType.PRIORITY_CHANGED, saved));
        }
        return saved;
    }

    /** Technician confirms he has seen the incident: ASSIGNED -> ACKNOWLEDGED. */
    @Transactional
    public Incident acknowledge(String incidentId, String technicianId) {
        Incident incident = ops.loadAssignedTo(incidentId, technicianId);
        IncidentOperations.requireNotLinked(incident);
        incident.transitionTo(IncidentStatus.ACKNOWLEDGED);
        // Keep the first acknowledgement: after a re-routing the response SLA was already met.
        if (incident.getAcknowledgedAt() == null) {
            incident.setAcknowledgedAt(LocalDateTime.now());
        }
        return ops.saveAndCascade(incident);
    }

    /** Technician starts working: ACKNOWLEDGED -> IN_PROGRESS. */
    @Transactional
    public Incident start(String incidentId, String technicianId) {
        Incident incident = ops.loadAssignedTo(incidentId, technicianId);
        IncidentOperations.requireNotLinked(incident);
        incident.transitionTo(IncidentStatus.IN_PROGRESS);
        return ops.saveAndCascade(incident);
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
        Incident incident = ops.loadAssignedTo(incidentId, technicianId);
        IncidentOperations.requireNotLinked(incident);
        incident.transitionTo(IncidentStatus.RESOLVED);
        incident.setResolvedAt(LocalDateTime.now());
        incident.setResolution(resolution.trim());

        Technician technician = technicians.findById(technicianId);
        technician.decrementActiveIncidents();
        technicians.save(technician);
        Incident saved = ops.saveAndCascade(incident);
        events.publishEvent(new IncidentEvent(IncidentEventType.RESOLVED, saved));
        return saved;
    }

    /** Admin checks the fix: RESOLVED -> VERIFIED. */
    @Transactional
    public Incident verify(String incidentId) {
        Incident incident = incidents.findById(incidentId);
        incident.transitionTo(IncidentStatus.VERIFIED);
        return ops.saveAndCascade(incident);
    }

    /** Admin closes the incident: VERIFIED -> CLOSED. */
    @Transactional
    public Incident close(String incidentId) {
        Incident incident = incidents.findById(incidentId);
        incident.transitionTo(IncidentStatus.CLOSED);
        incident.markClosed(AuditService.currentActor());
        Incident saved = ops.saveAndCascade(incident);
        events.publishEvent(new IncidentEvent(IncidentEventType.CLOSED, saved));
        return saved;
    }

    /**
     * Admin changes the priority of an open incident (e.g. the impact turned out bigger or smaller).
     * Deadlines follow the new priority automatically; the SLA alert memory is reset so the monitor
     * re-evaluates against the new targets.
     */
    @Transactional
    public Incident changePriority(String incidentId, Priority newPriority, String reason) {
        if (newPriority == null) throw new InvalidIncidentDataException("Choose a priority");
        if (reason == null || reason.isBlank()) throw new InvalidIncidentDataException("Give a reason for the change");
        Incident incident = incidents.findById(incidentId);
        if (incident.isFinished()) {
            throw new InvalidIncidentDataException("Only open incidents can change priority");
        }
        if (incident.isLinked()) {
            throw new InvalidIncidentDataException(incidentId + " follows " + incident.getParentId()
                    + "; change the priority of " + incident.getParentId() + " instead");
        }
        Priority old = incident.getPriority();
        if (old == newPriority) {
            throw new InvalidIncidentDataException(incidentId + " already has priority " + newPriority);
        }
        incident.setPriority(newPriority);
        incident.setReportedSla(AlertType.RESPONSE, SlaStatus.OK);
        incident.setReportedSla(AlertType.RESOLUTION, SlaStatus.OK);
        Incident saved = incidents.save(incident);
        audit.record("PRIORITY_CHANGED", "INCIDENT", incidentId, old + " -> " + newPriority + ": " + reason.trim(),
                old == null ? null : old.name(), newPriority.name());
        events.publishEvent(new IncidentEvent(IncidentEventType.PRIORITY_CHANGED, saved));
        return saved;
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

    // ---------------------------------------------------------------- delegated use cases

    /** Result of handing over a technician's open incidents. */
    public record HandoverResult(int moved, List<String> notMoved) {}

    public int maxActiveIncidents() {
        return ops.maxActiveIncidents();
    }

    public int assignQueuedIncidents() {
        return handover.assignQueuedIncidents();
    }

    public int reconcileWorkload() {
        return handover.reconcileWorkload();
    }

    public HandoverResult reassignOpenIncidentsOf(String technicianId) {
        return handover.reassignOpenIncidentsOf(technicianId);
    }

    public Incident handOver(String incidentId, String toTechnicianId) {
        return handover.handOver(incidentId, toTechnicianId);
    }

    public Incident confirmResolution(String incidentId, String employee) {
        return resolutionService.confirmResolution(incidentId, employee);
    }

    public Incident reopen(String incidentId, String employee, String reason) {
        return resolutionService.reopen(incidentId, employee, reason);
    }

    public Incident adminClose(String incidentId, String reason) {
        return resolutionService.adminClose(incidentId, reason);
    }

    public int autoCloseResolvedBefore(LocalDateTime cutoff) {
        return resolutionService.autoCloseResolvedBefore(cutoff);
    }

    public Incident requestReassignment(String incidentId, String technicianId, String correctCategory, String reason) {
        return rerouting.requestReassignment(incidentId, technicianId, correctCategory, reason);
    }

    public Incident approveReassignment(String incidentId, String technicianId) {
        return rerouting.approveReassignment(incidentId, technicianId);
    }

    public Incident rejectReassignment(String incidentId) {
        return rerouting.rejectReassignment(incidentId);
    }

    public Incident linkToSuggestedParent(String incidentId, String technicianId) {
        return linking.linkToSuggestedParent(incidentId, technicianId);
    }

    public Incident dismissDuplicateSuggestion(String incidentId, String technicianId) {
        return linking.dismissDuplicateSuggestion(incidentId, technicianId);
    }

    public Incident unlink(String incidentId, String technicianId) {
        return linking.unlink(incidentId, technicianId);
    }
}
