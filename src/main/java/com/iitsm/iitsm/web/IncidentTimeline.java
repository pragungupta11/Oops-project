package com.iitsm.iitsm.web;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.persistence.entity.AuditLogEntity;
import com.iitsm.iitsm.repository.AuditLogRepository;

/**
 * Turns the audit log of one incident into a readable timeline. Staff see everything
 * (who did what, reasons, SLA and escalation events); employees see a short, friendly
 * version without internal details.
 */
@Component
public class IncidentTimeline {
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    /** One row of the timeline. colour is a Bootstrap colour name (primary, success, ...). */
    public record Entry(String time, String title, String detail, String icon, String colour) {}

    private final AuditLogRepository auditLogs;
    private final UiLabels labels;

    public IncidentTimeline(AuditLogRepository auditLogs, UiLabels labels) {
        this.auditLogs = auditLogs;
        this.labels = labels;
    }

    public List<Entry> forIncident(String incidentId, boolean staffView) {
        List<AuditLogEntity> rows = new ArrayList<>(
                auditLogs.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("INCIDENT", incidentId));
        Collections.reverse(rows);   // oldest first

        List<Entry> entries = new ArrayList<>();
        String lastStatus = null;
        for (AuditLogEntity a : rows) {
            String time = FORMAT.format(a.getOccurredAt());
            String by = a.getActor() == null ? "" : "by " + a.getActor();
            String details = a.getDetails() == null ? "" : a.getDetails();
            switch (a.getAction()) {
                case "INCIDENT_CREATED" -> {
                    entries.add(new Entry(time, "Reported", staffView ? by : "", "inbox", "primary"));
                    lastStatus = statusIn(details);
                    if ("ASSIGNED".equals(lastStatus)) {
                        entries.add(new Entry(time, "Classified and assigned automatically", "", "person-check", "info"));
                    }
                }
                case "INCIDENT_UPDATED", "INCIDENT_STATUS_CHANGED" -> {
                    String status = statusIn(details);
                    if (status != null && !status.equals(lastStatus)) {
                        entries.add(new Entry(time, labels.status(status), staffView ? by : "",
                                labels.statusIcon(status), colourFor(status)));
                        lastStatus = status;
                    }
                }
                case "DUPLICATE_LINKED" -> entries.add(staffView
                        ? new Entry(time, "Linked as a duplicate", details, "link-45deg", "secondary")
                        : new Entry(time, "Confirmed as part of a known issue", "", "link-45deg", "secondary"));
                case "INCIDENT_UNLINKED" -> entries.add(new Entry(time, "Separated from the other incident",
                        staffView ? details : "", "scissors", "secondary"));
                case "REASSIGNMENT_APPROVED" -> entries.add(new Entry(time,
                        staffView ? "Re-routed" : "Moved to a specialist", staffView ? details : "", "arrow-left-right", "warning"));
                case "REASSIGNMENT_REQUESTED", "REASSIGNMENT_REJECTED", "DUPLICATE_DISMISSED" -> {
                    if (staffView) entries.add(new Entry(time, friendly(a.getAction()), details, "chat-left-text", "secondary"));
                }
                case "SLA_AT_RISK", "SLA_VIOLATED" -> {
                    if (staffView) {
                        String clock = details.startsWith("RESPONSE") ? "Response" : "Resolution";
                        boolean missed = a.getAction().equals("SLA_VIOLATED");
                        entries.add(new Entry(time, clock + (missed ? " target missed" : " deadline approaching"), "",
                                missed ? "exclamation-octagon" : "hourglass-split", missed ? "danger" : "warning"));
                    }
                }
                case "INCIDENT_ESCALATED" -> {
                    if (staffView) entries.add(new Entry(time, "Escalated", details, "arrow-up-circle", "danger"));
                }
                case "INFO_REQUESTED" -> entries.add(new Entry(time, "More information requested",
                        staffView ? details : "", "question-circle", "warning"));
                case "INFO_PROVIDED" -> entries.add(new Entry(time, "Information provided by the employee", "",
                        "reply", "info"));
                case "RESOLUTION_CONFIRMED" -> entries.add(new Entry(time, "Fix confirmed by the employee", "",
                        "hand-thumbs-up", "success"));
                case "INCIDENT_REOPENED" -> entries.add(new Entry(time,
                        staffView ? "Reopened by the employee" : "Reopened: you reported it is still not fixed",
                        staffView ? details : "", "arrow-counterclockwise", "danger"));
                case "QUEUED_UNASSIGNED" -> entries.add(new Entry(time, "Waiting in the queue for a technician",
                        staffView ? details : "", "hourglass", "warning"));
                case "PRIORITY_CHANGED" -> entries.add(new Entry(time, "Priority changed",
                        staffView ? details : "", "arrow-down-up", "warning"));
                case "INCIDENT_REASSIGNED" -> entries.add(new Entry(time,
                        staffView ? "Handed over to another technician" : "Your incident has a new technician",
                        staffView ? details : "", "arrow-left-right", "warning"));
                case "CLOSED_BY_ADMIN" -> entries.add(new Entry(time, "Closed by an administrator",
                        staffView ? details : "", "shield-check", "dark"));
                case "AUTO_CLOSED" -> entries.add(new Entry(time, "Closed automatically",
                        staffView ? details : "No answer received, so it was closed", "clock", "dark"));
                case "FEEDBACK_GIVEN" -> entries.add(new Entry(time, "Feedback received", details, "star", "success"));
                case "NOTE_ADDED" -> {
                    if (staffView) entries.add(new Entry(time, "Investigation note", details, "journal", "secondary"));
                }
                default -> { /* other audit rows are not interesting on the timeline */ }
            }
        }
        return entries;
    }

    private static String statusIn(String details) {
        int i = details.indexOf("status=");
        if (i < 0) return null;
        String rest = details.substring(i + 7).trim();
        int end = 0;
        while (end < rest.length() && (Character.isLetter(rest.charAt(end)) || rest.charAt(end) == '_')) end++;
        return end == 0 ? null : rest.substring(0, end);
    }

    private static String colourFor(String status) {
        return switch (status) {
            case "RESOLVED", "VERIFIED" -> "success";
            case "CLOSED" -> "dark";
            case "IN_PROGRESS", "ACKNOWLEDGED" -> "primary";
            default -> "info";
        };
    }

    private static String friendly(String action) {
        return switch (action) {
            case "REASSIGNMENT_REQUESTED" -> "Re-routing requested";
            case "REASSIGNMENT_REJECTED" -> "Re-routing rejected";
            case "DUPLICATE_DISMISSED" -> "Duplicate hint dismissed";
            case "SLA_AT_RISK" -> "SLA at risk";
            case "SLA_VIOLATED" -> "SLA violated";
            case "INCIDENT_ESCALATED" -> "Escalated";
            default -> action;
        };
    }
}
