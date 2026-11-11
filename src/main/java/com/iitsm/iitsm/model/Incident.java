package com.iitsm.iitsm.model;

import java.time.LocalDateTime;

public class Incident {
    private final String id;
    private final String description;
    private final String category;
    private final int impact;
    private final int urgency;
    private final int criticality;
    private final LocalDateTime createdAt;

    private Priority priority;
    private IncidentStatus status = IncidentStatus.NEW;
    private Technician assignedTechnician;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;

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

    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }

    public Technician getAssignedTechnician() { return assignedTechnician; }
    public void setAssignedTechnician(Technician t) { this.assignedTechnician = t; }

    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime t) { this.acknowledgedAt = t; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime t) { this.resolvedAt = t; }
}