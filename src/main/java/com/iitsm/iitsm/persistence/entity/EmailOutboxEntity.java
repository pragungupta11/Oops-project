package com.iitsm.iitsm.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A simulated e-mail: everything a real mail server would need, kept in an outbox instead. */
@Entity
@Table(name = "email_outbox")
public class EmailOutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String toAddress;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 1000)
    private String body;

    @Column(nullable = false)
    private Instant sentAt;

    protected EmailOutboxEntity() { }

    public EmailOutboxEntity(String toAddress, String subject, String body) {
        this.toAddress = toAddress;
        this.subject = subject;
        this.body = body;
        this.sentAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getToAddress() { return toAddress; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Instant getSentAt() { return sentAt; }
}
