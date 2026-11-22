package com.iitsm.iitsm.notification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.EscalationEvent;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaAlert;
import com.iitsm.iitsm.model.SlaStatus;
import com.iitsm.iitsm.observer.EscalationListener;
import com.iitsm.iitsm.observer.SlaAlertListener;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;

/**
 * Decides WHO is told about WHAT. It observes three sources: incident events from the
 * workflow, SLA alerts from the monitor and escalations, and hands the messages to the
 * NotificationService, which delivers them through every channel.
 */
@Component
public class IncidentNotifier implements SlaAlertListener, EscalationListener {
    private final NotificationService notifications;
    private final IncidentPersistenceService incidents;

    public IncidentNotifier(NotificationService notifications, IncidentPersistenceService incidents) {
        this.notifications = notifications;
        this.incidents = incidents;
    }

    @EventListener
    public void on(IncidentEvent event) {
        Incident i = event.incident();
        String id = i.getId();
        String employee = i.getReporterUsername();
        String tech = technicianId(i);
        String techName = i.getAssignedTechnician() == null ? "a technician" : i.getAssignedTechnician().getName();

        switch (event.type()) {
            case REPORTED -> {
                send(employee, id + " received",
                        "We received your report \"" + brief(i) + "\". It has been assigned to " + techName + ".", id);
                send(tech, "New " + urgency(i) + " incident " + id,
                        "You have been assigned " + id + " (" + i.getCategory() + "): " + brief(i), id);
            }
            case REROUTED -> {
                send(employee, id + " moved to a specialist",
                        "Your incident is now handled by " + techName + " (" + i.getCategory() + ").", id);
                send(tech, id + " re-routed to you",
                        id + " was re-routed to you after its category was corrected to " + i.getCategory() + ".", id);
                if (event.previousTechnicianId() != null && !event.previousTechnicianId().equals(tech)) {
                    send(event.previousTechnicianId(), id + " taken off your list",
                            "The admin approved your re-routing request; " + id + " is now with " + techName + ".", id);
                }
            }
            case PRIORITY_CHANGED -> {
                send(tech, id + " is now " + i.getPriority(), "The priority of " + id + " was changed to "
                        + i.getPriority() + " (" + urgency(i) + "). The SLA deadlines changed with it.", id);
                send(employee, id + " urgency updated", "The urgency of your incident was updated to "
                        + urgency(i) + ".", id);
            }
            case QUEUED -> {
                send(employee, id + " received",
                        "We received your report \"" + brief(i) + "\". All suitable technicians are busy right now, "
                                + "so it is in the queue and will be assigned automatically as soon as someone is free.", id);
                sendAll(notifications.adminUsernames(), id + " waiting for a technician",
                        id + " (" + i.getCategory() + ", " + i.getPriority() + ") could not be assigned automatically. "
                                + "Assign it on the Users page or free up capacity.", id);
            }
            case REASSIGNED -> {
                boolean fromQueue = event.previousTechnicianId() == null;
                send(employee, id + (fromQueue ? " assigned" : " has a new technician"),
                        "Your incident is now handled by " + techName
                                + (fromQueue ? "." : " because the previous technician is unavailable."), id);
                send(tech, id + " assigned to you", id + (fromQueue ? " was assigned to you from the queue: "
                        : " was handed over to you because its technician is unavailable: ") + brief(i), id);
            }
            case LINKED -> send(employee, id + " is part of a known issue",
                    "Our team confirmed your report is part of an issue that is already being worked on. "
                            + "You will be told as soon as it is fixed.", id);
            case UNLINKED -> send(tech, id + " assigned to you",
                    id + " was separated from another incident and assigned to you: " + brief(i), id);
            case RESOLVED -> {
                send(employee, id + " resolved: is it fixed?", "Your incident has been resolved: " + i.getResolution()
                        + (i.isLinked() ? "" : " Please confirm on the incident page whether it is fixed."), id);
            }
            case REOPENED -> {
                send(tech, id + " reopened",
                        "The employee reports " + id + " is still not fixed. Their reason is in the conversation.", id);
                sendAll(notifications.adminUsernames(), id + " reopened", id + " was reopened by the employee.", id);
            }
            case CLOSED -> send(employee, id + " closed", "Your incident has been closed. Thank you!", id);
        }
    }

    /** Observer of the SLA monitor. */
    @Override
    public void onAlert(SlaAlert alert) {
        Incident i = find(alert.incidentId());
        if (i == null) return;
        String what = alert.type().name().toLowerCase() + " target";
        if (alert.status() == SlaStatus.AT_RISK) {
            List<String> who = technicianId(i) != null ? List.of(technicianId(i))
                    : notifications.adminUsernames();   // queued, nobody assigned yet: tell the admins
            sendAll(who, "SLA at risk: " + i.getId(),
                    "The " + what + " of " + i.getId() + " is close to its deadline"
                            + (technicianId(i) == null ? " and it is still waiting for a technician." : "."), i.getId());
        } else if (alert.status() == SlaStatus.VIOLATED) {
            List<String> who = new ArrayList<>(notifications.adminUsernames());
            who.add(technicianId(i));
            sendAll(who, "SLA violated: " + i.getId(),
                    "The " + what + " of " + i.getId() + " (" + urgency(i) + ") has been missed.", i.getId());
        }
    }

    /** Observer of the escalation service. */
    @Override
    public void onEscalation(EscalationEvent event) {
        Incident i = find(event.incidentId());
        if (i == null) return;
        List<String> who = new ArrayList<>(notifications.adminUsernames());
        who.add(technicianId(i));
        sendAll(who, "Escalated: " + i.getId(),
                i.getId() + " was not acknowledged in time and is escalated from "
                        + event.from() + " to " + event.to() + ".", i.getId());
    }

    // ------------------------------------------------------------------ helpers

    private void send(String recipient, String subject, String body, String incidentId) {
        sendAll(recipient == null ? List.of() : List.of(recipient), subject, body, incidentId);
    }

    private void sendAll(List<String> recipients, String subject, String body, String incidentId) {
        notifications.notify(recipients, subject, body, incidentId);
    }

    private Incident find(String id) {
        try {
            return incidents.findById(id);
        } catch (IllegalArgumentException e) {
            return null;   // not in the database (e.g. in-memory test incidents)
        }
    }

    private static String technicianId(Incident i) {
        return i.getAssignedTechnician() == null ? null : i.getAssignedTechnician().getId();
    }

    private static String brief(Incident i) {
        String d = i.getSubject();
        return d == null ? "" : (d.length() > 80 ? d.substring(0, 77) + "..." : d);
    }

    private static String urgency(Incident i) {
        Priority p = i.getPriority();
        if (p == null) return "new";
        return switch (p) {
            case P1 -> "critical";
            case P2 -> "high-urgency";
            case P3 -> "medium-urgency";
            case P4 -> "low-urgency";
        };
    }
}
