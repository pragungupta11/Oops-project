package com.iitsm.iitsm.model;

import java.time.LocalDateTime;

import com.iitsm.iitsm.state.IncidentState;
import com.iitsm.iitsm.state.IncidentStates;

public class Incident {
    private final String id;
    private final String description;
    private final String category;
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
}