package com.iitsm.iitsm.persistence.entity;

import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "incidents")
public class IncidentEntity {
    @Id
    @Column(length = 80)
    private String id;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 120)
    private String category;

    private int impact;
    private int urgency;
    private int criticality;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_technician_id")
    private TechnicianEntity assignedTechnician;

    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;

    protected IncidentEntity() { }

    public IncidentEntity(String id, String description, String category,
                          int impact, int urgency, int criticality,
                          LocalDateTime createdAt, Priority priority,
                          IncidentStatus status, TechnicianEntity assignedTechnician,
                          LocalDateTime acknowledgedAt, LocalDateTime resolvedAt) {
        this.id = id;
        this.description = description;
        this.category = category;
        this.impact = impact;
        this.urgency = urgency;
        this.criticality = criticality;
        this.createdAt = createdAt;
        this.priority = priority;
        this.status = status;
        this.assignedTechnician = assignedTechnician;
        this.acknowledgedAt = acknowledgedAt;
        this.resolvedAt = resolvedAt;
    }

    public static IncidentEntity fromDomain(Incident incident, TechnicianEntity technician) {
        return new IncidentEntity(incident.getId(), incident.getDescription(), incident.getCategory(),
                incident.getImpact(), incident.getUrgency(), incident.getCriticality(),
                incident.getCreatedAt(), incident.getPriority(), incident.getStatus(), technician,
                incident.getAcknowledgedAt(), incident.getResolvedAt());
    }

    public Incident toDomain() {
        Incident incident = new Incident(id, description, category, impact, urgency, criticality, createdAt);
        incident.setPriority(priority);
        incident.setStatus(status);
        incident.setAssignedTechnician(assignedTechnician == null ? null : assignedTechnician.toDomain());
        incident.setAcknowledgedAt(acknowledgedAt);
        incident.setResolvedAt(resolvedAt);
        return incident;
    }

    public String getId() { return id; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public void setAssignedTechnician(TechnicianEntity technician) { this.assignedTechnician = technician; }
}
