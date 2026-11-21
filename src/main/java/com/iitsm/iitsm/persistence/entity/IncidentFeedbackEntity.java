package com.iitsm.iitsm.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The employee's rating (1-5) after the incident was resolved. At most one per incident. */
@Entity
@Table(name = "incident_feedback")
public class IncidentFeedbackEntity {
    @Id
    @Column(length = 20)
    private String incidentId;

    @Column(nullable = false, length = 80)
    private String employee;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false)
    private Instant createdAt;

    protected IncidentFeedbackEntity() { }

    public IncidentFeedbackEntity(String incidentId, String employee, int rating, String comment) {
        this.incidentId = incidentId;
        this.employee = employee;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public String getIncidentId() { return incidentId; }
    public String getEmployee() { return employee; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
