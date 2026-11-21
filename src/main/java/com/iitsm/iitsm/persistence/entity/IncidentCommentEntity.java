package com.iitsm.iitsm.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

import com.iitsm.iitsm.model.CommentKind;

/** One message, note or information request on an incident. */
@Entity
@Table(name = "incident_comments", indexes = @Index(columnList = "incidentId"))
public class IncidentCommentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String incidentId;

    @Column(nullable = false, length = 80)
    private String author;

    /** EMPLOYEE or TECHNICIAN */
    @Column(nullable = false, length = 20)
    private String authorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentKind kind;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(nullable = false)
    private Instant createdAt;

    protected IncidentCommentEntity() { }

    public IncidentCommentEntity(String incidentId, String author, String authorRole, CommentKind kind, String text) {
        this.incidentId = incidentId;
        this.author = author;
        this.authorRole = authorRole;
        this.kind = kind;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getIncidentId() { return incidentId; }
    public String getAuthor() { return author; }
    public String getAuthorRole() { return authorRole; }
    public CommentKind getKind() { return kind; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
