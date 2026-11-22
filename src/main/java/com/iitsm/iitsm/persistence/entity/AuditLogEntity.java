package com.iitsm.iitsm.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 120)
    private String actor;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 80)
    private String entityType;

    @Column(nullable = false, length = 100)
    private String entityId;

    @Column(length = 2000)
    private String details;

    @Column(nullable = false)
    private Instant occurredAt;

    /** State before and after the action (e.g. ASSIGNED -> ACKNOWLEDGED); empty when not a state change. */
    @Column(length = 40)
    private String previousState;

    @Column(length = 40)
    private String newState;

    protected AuditLogEntity() { }

    public AuditLogEntity(String actor, String action, String entityType, String entityId, String details) {
        this.id = UUID.randomUUID().toString();
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.occurredAt = Instant.now();
    }

    public AuditLogEntity(String actor, String action, String entityType, String entityId, String details,
                          String previousState, String newState) {
        this(actor, action, entityType, entityId, details);
        this.previousState = previousState;
        this.newState = newState;
    }

    public String getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getPreviousState() { return previousState; }
    public String getNewState() { return newState; }
}
