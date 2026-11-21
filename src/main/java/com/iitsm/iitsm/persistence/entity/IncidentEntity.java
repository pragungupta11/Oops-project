package com.iitsm.iitsm.persistence.entity;

import com.iitsm.iitsm.model.AlertType;
import com.iitsm.iitsm.model.EscalationLevel;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.SlaStatus;
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

    @Column(length = 80)
    private String reporterUsername;

    @Column(length = 120)
    private String serviceName;

    @Column(length = 2000)
    private String resolution;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EscalationLevel escalationLevel;

    /** Pending re-routing request from the technician (null when none). */
    @Column(length = 40)
    private String requestedCategory;

    @Column(length = 500)
    private String reassignmentReason;

    /** Set when a technician linked this report to an existing incident (the parent). */
    @Column(length = 20)
    private String parentId;

    /** The system's duplicate hint, waiting for a technician's decision. */
    @Column(length = 20)
    private String suggestedParentId;

    private Integer suggestedMatchPercent;

    /** Last SLA state the monitor reported, so alerts are not repeated after a restart. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SlaStatus reportedResponseSla;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SlaStatus reportedResolutionSla;

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
        IncidentEntity entity = new IncidentEntity(incident.getId(), incident.getDescription(), incident.getCategory(),
                incident.getImpact(), incident.getUrgency(), incident.getCriticality(),
                incident.getCreatedAt(), incident.getPriority(), incident.getStatus(), technician,
                incident.getAcknowledgedAt(), incident.getResolvedAt());
        entity.reporterUsername = incident.getReporterUsername();
        entity.serviceName = incident.getServiceName();
        entity.resolution = incident.getResolution();
        entity.escalationLevel = incident.getEscalationLevel();
        entity.requestedCategory = incident.getRequestedCategory();
        entity.reassignmentReason = incident.getReassignmentReason();
        entity.parentId = incident.getParentId();
        entity.suggestedParentId = incident.getSuggestedParentId();
        entity.suggestedMatchPercent = incident.getSuggestedMatchPercent();
        entity.reportedResponseSla = incident.getReportedSla(AlertType.RESPONSE);
        entity.reportedResolutionSla = incident.getReportedSla(AlertType.RESOLUTION);
        return entity;
    }

    public Incident toDomain() {
        Incident incident = new Incident(id, description, category, impact, urgency, criticality, createdAt);
        incident.setPriority(priority);
        incident.setStatus(status);
        incident.setAssignedTechnician(assignedTechnician == null ? null : assignedTechnician.toDomain());
        incident.setAcknowledgedAt(acknowledgedAt);
        incident.setResolvedAt(resolvedAt);
        incident.setReporterUsername(reporterUsername);
        incident.setServiceName(serviceName);
        incident.setResolution(resolution);
        if (escalationLevel != null) {
            incident.setEscalationLevel(escalationLevel);
        }
        incident.restoreReassignmentRequest(requestedCategory, reassignmentReason);
        incident.restoreDuplicateState(parentId, suggestedParentId, suggestedMatchPercent);
        incident.setReportedSla(AlertType.RESPONSE, reportedResponseSla);
        incident.setReportedSla(AlertType.RESOLUTION, reportedResolutionSla);
        return incident;
    }

    public String getId() { return id; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public void setAssignedTechnician(TechnicianEntity technician) { this.assignedTechnician = technician; }
}
