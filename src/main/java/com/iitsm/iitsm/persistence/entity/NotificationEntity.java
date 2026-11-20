package com.iitsm.iitsm.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One in-app notification shown under the bell icon. */
@Entity
@Table(name = "notifications")
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 1000)
    private String body;

    @Column(length = 20)
    private String incidentId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean readFlag;

    protected NotificationEntity() { }

    public NotificationEntity(String recipient, String subject, String body, String incidentId) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.incidentId = incidentId;
        this.createdAt = Instant.now();
        this.readFlag = false;
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getIncidentId() { return incidentId; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isReadFlag() { return readFlag; }

    public void markRead() { this.readFlag = true; }
}
