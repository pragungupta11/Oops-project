package com.iitsm.iitsm.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.EscalationEvent;
import com.iitsm.iitsm.model.EscalationLevel;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.observer.EscalationListener;

@Component
public class EscalationService {
    private static final Set<Priority> ESCALATING = EnumSet.of(Priority.P1);
    private static final Set<IncidentStatus> WAITING_FOR_ACK =
            EnumSet.of(IncidentStatus.NEW, IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED);

    private final SlaService slaService;
    private final List<EscalationListener> listeners;

    public EscalationService(SlaService slaService, List<EscalationListener> listeners) {
        this.slaService = slaService;
        this.listeners = listeners;
    }

    /** Escalates every unacknowledged critical incident that is overdue. Returns what it escalated. */
    public List<EscalationEvent> evaluate(List<Incident> incidents, LocalDateTime now) {
        List<EscalationEvent> events = new ArrayList<>();
        for (Incident incident : incidents) {
            if (!needsWatching(incident)) continue;

            EscalationLevel current = incident.getEscalationLevel();
            EscalationLevel target = expectedLevel(incident, now);
            if (target.ordinal() <= current.ordinal()) continue;

            incident.setEscalationLevel(target);
            EscalationEvent event = new EscalationEvent(incident.getId(), current, target, now);
            events.add(event);
            for (EscalationListener listener : listeners) {
                listener.onEscalation(event);
            }
        }
        return events;
    }

    private boolean needsWatching(Incident incident) {
        return incident.getPriority() != null
                && !incident.isLinked()          // escalate the parent, not every duplicate report
                && ESCALATING.contains(incident.getPriority())
                && WAITING_FOR_ACK.contains(incident.getStatus())
                && incident.getAcknowledgedAt() == null;
    }

    private EscalationLevel expectedLevel(Incident incident, LocalDateTime now) {
        Duration period = slaService.policyFor(incident.getPriority(), incident.getServiceName()).responseTarget();
        long periodsElapsed = Duration.between(incident.getCreatedAt(), now).toSeconds()
                / period.toSeconds();
        EscalationLevel[] levels = EscalationLevel.values();
        int index = (int) Math.min(periodsElapsed, levels.length - 1);
        return levels[index];
    }
}