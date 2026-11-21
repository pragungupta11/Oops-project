package com.iitsm.iitsm.web;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.CommentKind;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.notification.NotificationService;
import com.iitsm.iitsm.persistence.entity.IncidentCommentEntity;
import com.iitsm.iitsm.persistence.entity.IncidentFeedbackEntity;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.repository.IncidentCommentRepository;
import com.iitsm.iitsm.repository.IncidentFeedbackRepository;

/**
 * The conversation on an incident: the employee adds information and answers questions, the
 * technician writes messages, internal investigation notes and information requests, and after
 * resolution the employee rates the service.
 */
@Service
public class IncidentConversationService {
    static final int MAX_TEXT = 1000;
    static final int MAX_FEEDBACK_COMMENT = 500;

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    /** One entry as shown on a page. */
    public record CommentView(String author, String role, String kind, String text, String time) {}

    public record FeedbackView(int rating, String comment, String time) {}

    private final IncidentCommentRepository comments;
    private final IncidentFeedbackRepository feedback;
    private final IncidentPersistenceService incidents;
    private final AuditService audit;
    private final NotificationService notifications;

    public IncidentConversationService(IncidentCommentRepository comments, IncidentFeedbackRepository feedback,
                                       IncidentPersistenceService incidents, AuditService audit,
                                       NotificationService notifications) {
        this.comments = comments;
        this.feedback = feedback;
        this.incidents = incidents;
        this.audit = audit;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ employee

    /** Employee adds information to their own incident, or answers the technician's question. */
    @Transactional
    public void addEmployeeMessage(String incidentId, String employee, String text) {
        Incident incident = incidents.findById(incidentId);
        if (!employee.equals(incident.getReporterUsername())) {
            throw new AccessDeniedException("This is not your incident");
        }
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new InvalidIncidentDataException("This incident is closed; please report a new one");
        }
        boolean answersRequest = isAwaitingInfo(incidentId);
        comments.save(new IncidentCommentEntity(incidentId, employee, "EMPLOYEE", CommentKind.MESSAGE, clean(text)));
        audit.record(answersRequest ? "INFO_PROVIDED" : "COMMENT_ADDED", "INCIDENT", incidentId,
                answersRequest ? "Employee answered the information request" : "Employee added information");
        String tech = technicianId(incident);
        if (tech != null) {
            notifications.notify(List.of(tech), (answersRequest ? "Reply on " : "New information on ") + incidentId,
                    employee + " wrote: " + shorten(text), incidentId);
        }
    }

    /** Employee rates the service after the incident was resolved. Only once. */
    @Transactional
    public void giveFeedback(String incidentId, String employee, int rating, String comment) {
        Incident incident = incidents.findById(incidentId);
        if (!employee.equals(incident.getReporterUsername())) {
            throw new AccessDeniedException("This is not your incident");
        }
        IncidentStatus s = incident.getStatus();
        if (s != IncidentStatus.RESOLVED && s != IncidentStatus.VERIFIED && s != IncidentStatus.CLOSED) {
            throw new InvalidIncidentDataException("Feedback can be given once the incident is resolved");
        }
        if (feedback.existsById(incidentId)) {
            throw new InvalidIncidentDataException("You already gave feedback for this incident");
        }
        if (rating < 1 || rating > 5) {
            throw new InvalidIncidentDataException("Choose a rating from 1 to 5");
        }
        String c = comment == null ? "" : comment.trim();
        if (c.length() > MAX_FEEDBACK_COMMENT) {
            throw new InvalidIncidentDataException("Feedback comment must be at most " + MAX_FEEDBACK_COMMENT + " characters");
        }
        feedback.save(new IncidentFeedbackEntity(incidentId, employee, rating, c.isEmpty() ? null : c));
        audit.record("FEEDBACK_GIVEN", "INCIDENT", incidentId, "Rating " + rating + "/5" + (c.isEmpty() ? "" : ": " + c));
        String tech = technicianId(incident);
        if (tech != null) {
            notifications.notify(List.of(tech), "Feedback on " + incidentId, "Rated " + rating + "/5", incidentId);
        }
    }

    // ------------------------------------------------------------------ technician

    /** Technician writes a message, an internal note or an information request. */
    @Transactional
    public void addTechnicianEntry(String incidentId, String technician, CommentKind kind, String text) {
        Incident incident = incidents.findById(incidentId);
        if (!technician.equals(technicianId(incident))) {
            throw new AccessDeniedException("This incident is not assigned to you");
        }
        if (incident.getStatus() == IncidentStatus.VERIFIED || incident.getStatus() == IncidentStatus.CLOSED) {
            // Verified/closed records are final: the conversation becomes read-only.
            throw new InvalidIncidentDataException("This incident is closed; the conversation is read-only");
        }
        boolean finished = incident.getStatus() == IncidentStatus.RESOLVED;
        if (kind == CommentKind.INFO_REQUEST && finished) {
            throw new InvalidIncidentDataException("You can only ask for information while the incident is open");
        }
        comments.save(new IncidentCommentEntity(incidentId, technician, "TECHNICIAN", kind, clean(text)));
        switch (kind) {
            case NOTE -> audit.record("NOTE_ADDED", "INCIDENT", incidentId, "Investigation note by " + technician);
            case MESSAGE -> {
                audit.record("COMMENT_ADDED", "INCIDENT", incidentId, "Technician " + technician + " wrote to the employee");
                notifications.notify(List.of(incident.getReporterUsername()), "Message about " + incidentId,
                        shorten(text), incidentId);
            }
            case INFO_REQUEST -> {
                audit.record("INFO_REQUESTED", "INCIDENT", incidentId, "Technician " + technician + " asked: " + shorten(text));
                notifications.notify(List.of(incident.getReporterUsername()), "More information needed for " + incidentId,
                        "Your technician asks: " + shorten(text) + " Please reply on the incident page.", incidentId);
            }
        }
    }

    // ------------------------------------------------------------------ reading

    /** Staff see everything; employees do not see internal notes. Oldest first. */
    @Transactional(readOnly = true)
    public List<CommentView> thread(String incidentId, boolean staffView) {
        return comments.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId).stream()
                .filter(c -> staffView || c.getKind() != CommentKind.NOTE)
                .map(c -> new CommentView(c.getAuthor(), c.getAuthorRole(), c.getKind().name(), c.getText(),
                        FORMAT.format(c.getCreatedAt())))
                .toList();
    }

    /** True when the technician's latest information request has not been answered yet. */
    @Transactional(readOnly = true)
    public boolean isAwaitingInfo(String incidentId) {
        List<IncidentCommentEntity> all = comments.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId);
        boolean waiting = false;
        for (IncidentCommentEntity c : all) {
            if (c.getKind() == CommentKind.INFO_REQUEST) waiting = true;
            else if ("EMPLOYEE".equals(c.getAuthorRole())) waiting = false;
        }
        return waiting;
    }

    @Transactional(readOnly = true)
    public Optional<FeedbackView> feedbackFor(String incidentId) {
        return feedback.findById(incidentId)
                .map(f -> new FeedbackView(f.getRating(), f.getComment(), FORMAT.format(f.getCreatedAt())));
    }

    /** Average rating over all feedback, or empty when there is none yet. */
    @Transactional(readOnly = true)
    public Optional<Double> averageRating() {
        List<IncidentFeedbackEntity> all = feedback.findAll();
        if (all.isEmpty()) return Optional.empty();
        return Optional.of(all.stream().mapToInt(IncidentFeedbackEntity::getRating).average().orElse(0));
    }

    @Transactional(readOnly = true)
    public long feedbackCount() {
        return feedback.count();
    }

    // ------------------------------------------------------------------ helpers

    private static String clean(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new InvalidIncidentDataException("Write a message first");
        if (t.length() > MAX_TEXT) throw new InvalidIncidentDataException("Messages must be at most " + MAX_TEXT + " characters");
        return t;
    }

    private static String shorten(String text) {
        String t = text == null ? "" : text.trim();
        return t.length() > 140 ? t.substring(0, 137) + "..." : t;
    }

    private static String technicianId(Incident incident) {
        return incident.getAssignedTechnician() == null ? null : incident.getAssignedTechnician().getId();
    }
}
