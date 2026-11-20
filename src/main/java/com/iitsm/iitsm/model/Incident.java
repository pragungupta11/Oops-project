package com.iitsm.iitsm.model;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.state.IncidentState;
import com.iitsm.iitsm.state.IncidentStates;

public class Incident {
    private final String id;
    private final String description;
    private String category;
    private final int impact;
    private final int urgency;
    private final int criticality;
    private final LocalDateTime createdAt;

    private Priority priority;
    private IncidentState state = IncidentStates.of(IncidentStatus.NEW);
    private Technician assignedTechnician;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    private EscalationLevel escalationLevel = EscalationLevel.TECHNICIAN;

    private User reporter;
    private ItService affectedService;
    private String resolution;
    private String reporterUsername;
    private String serviceName;

    // Re-routing: a technician says the category is wrong; an admin approves or rejects.
    private static final Set<IncidentStatus> REROUTABLE = EnumSet.of(
            IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS);
    private String requestedCategory;
    private String reassignmentReason;

    // Duplicate handling: the system only SUGGESTS a parent; a technician confirms the link.
    private static final Set<IncidentStatus> FINISHED = EnumSet.of(
            IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);
    private String parentId;
    private String suggestedParentId;
    private Integer suggestedMatchPercent;

    public Incident(String id, String description, String category,
                    int impact, int urgency, int criticality,
                    LocalDateTime createdAt) {
        this.id = id;
        this.description = description;
        this.category = category;
        this.impact = impact;
        this.urgency = urgency;
        this.criticality = criticality;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public int getImpact() { return impact; }
    public int getUrgency() { return urgency; }
    public int getCriticality() { return criticality; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public IncidentStatus getStatus() { return state.status(); }

    /** Enforced move: throws InvalidStateTransitionException if illegal. */
    public void transitionTo(IncidentStatus target) {
        this.state = state.transitionTo(target);
    }

    /** Unchecked jump, kept so existing tests and setup code still compile. Prefer transitionTo. */
    public void setStatus(IncidentStatus status) {
        this.state = IncidentStates.of(status);
    }

    public Technician getAssignedTechnician() { return assignedTechnician; }
    public void setAssignedTechnician(Technician t) { this.assignedTechnician = t; }

    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime t) { this.acknowledgedAt = t; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime t) { this.resolvedAt = t; }

    public EscalationLevel getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(EscalationLevel level) { this.escalationLevel = level; }

    public User getReporter() { return reporter; }
    public void setReporter(User reporter) { this.reporter = reporter; }

    public ItService getAffectedService() { return affectedService; }
    public void setAffectedService(ItService s) { this.affectedService = s; }

    public String getReporterUsername() { return reporterUsername; }
    public void setReporterUsername(String reporterUsername) { this.reporterUsername = reporterUsername; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    // ------------------------------------------------------------ re-routing (reassignment)

    public String getRequestedCategory() { return requestedCategory; }
    public String getReassignmentReason() { return reassignmentReason; }

    public boolean hasPendingReassignment() { return requestedCategory != null; }

    /** Technician reports a wrong category. The incident stays with him until an admin decides. */
    public void requestReassignment(String newCategory, String reason) {
        if (!REROUTABLE.contains(getStatus())) {
            throw new InvalidIncidentDataException("Only an active incident can be re-routed (status is " + getStatus() + ")");
        }
        if (isLinked()) {
            throw new InvalidIncidentDataException("This report is linked to " + parentId + "; unlink it before re-routing");
        }
        if (hasPendingReassignment()) {
            throw new InvalidIncidentDataException("A re-routing request is already waiting for the admin");
        }
        if (newCategory == null || newCategory.isBlank()) {
            throw new InvalidIncidentDataException("Choose the correct category");
        }
        if (newCategory.equalsIgnoreCase(category)) {
            throw new InvalidIncidentDataException("The incident is already in category " + category);
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidIncidentDataException("Explain why the category is wrong");
        }
        this.requestedCategory = newCategory;
        this.reassignmentReason = reason.trim();
    }

    /**
     * Admin approves: the category is corrected, the request is cleared and the incident goes
     * back to ASSIGNED so the new technician must acknowledge it. The SLA clock keeps running,
     * because the employee is still waiting.
     */
    public void approveReassignment(Technician newTechnician) {
        if (!hasPendingReassignment()) {
            throw new InvalidIncidentDataException("There is no re-routing request to approve");
        }
        if (newTechnician == null) {
            throw new InvalidIncidentDataException("A new technician is required");
        }
        this.category = requestedCategory;
        this.assignedTechnician = newTechnician;
        clearReassignmentRequest();
        if (getStatus() != IncidentStatus.ASSIGNED) {
            transitionTo(IncidentStatus.ASSIGNED);
        }
    }

    /** Admin rejects: nothing changes except that the request is cleared. */
    public void rejectReassignment() {
        if (!hasPendingReassignment()) {
            throw new InvalidIncidentDataException("There is no re-routing request to reject");
        }
        clearReassignmentRequest();
    }

    /** Used only when loading from the database. */
    public void restoreReassignmentRequest(String requestedCategory, String reason) {
        this.requestedCategory = requestedCategory;
        this.reassignmentReason = reason;
    }

    private void clearReassignmentRequest() {
        this.requestedCategory = null;
        this.reassignmentReason = null;
    }

    // ------------------------------------------------------------ duplicates (manual linking)

    public String getParentId() { return parentId; }
    public boolean isLinked() { return parentId != null; }
    public String getSuggestedParentId() { return suggestedParentId; }
    public Integer getSuggestedMatchPercent() { return suggestedMatchPercent; }
    public boolean hasDuplicateSuggestion() { return suggestedParentId != null; }
    public boolean isFinished() { return FINISHED.contains(getStatus()); }

    /** The system thinks this may be the same problem as another open incident. Only a hint. */
    public void suggestDuplicateOf(String incidentId, int matchPercent) {
        this.suggestedParentId = incidentId;
        this.suggestedMatchPercent = matchPercent;
    }

    /** Technician checked the hint and decided it is a different problem. */
    public void dismissDuplicateSuggestion() {
        this.suggestedParentId = null;
        this.suggestedMatchPercent = null;
    }

    /**
     * Technician confirms this report is the same problem as {@code parent}. The report is then
     * handled by the parent's technician and follows the parent's progress.
     */
    public void linkTo(Incident parent) {
        if (parent == null) {
            throw new InvalidIncidentDataException("Choose the incident to link to");
        }
        if (parent.getId().equals(id)) {
            throw new InvalidIncidentDataException("An incident cannot be linked to itself");
        }
        if (isLinked()) {
            throw new InvalidIncidentDataException(id + " is already linked to " + parentId);
        }
        if (parent.isLinked()) {
            throw new InvalidIncidentDataException(parent.getId() + " is itself linked to "
                    + parent.getParentId() + "; link to that one instead");
        }
        if (isFinished() || parent.isFinished()) {
            throw new InvalidIncidentDataException("Only open incidents can be linked");
        }
        if (parent.getAssignedTechnician() == null) {
            throw new InvalidIncidentDataException(parent.getId() + " has no technician yet");
        }
        this.parentId = parent.getId();
        this.assignedTechnician = parent.getAssignedTechnician();
        dismissDuplicateSuggestion();
    }

    /** The link was a mistake: this report becomes an independent incident again. */
    public void unlink() {
        if (!isLinked()) {
            throw new InvalidIncidentDataException(id + " is not linked to another incident");
        }
        if (isFinished()) {
            throw new InvalidIncidentDataException("A finished incident cannot be unlinked");
        }
        this.parentId = null;
    }

    /** Used only when loading from the database. */
    public void restoreDuplicateState(String parentId, String suggestedParentId, Integer matchPercent) {
        this.parentId = parentId;
        this.suggestedParentId = suggestedParentId;
        this.suggestedMatchPercent = matchPercent;
    }
}
