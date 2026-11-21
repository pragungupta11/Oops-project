package com.iitsm.iitsm.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.EscalationEvent;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.SlaAlert;

@Component
public class SlaMonitorJob {
    private final SlaMonitor monitor;
    private final EscalationService escalation;
    private final IncidentSource source;

    public SlaMonitorJob(SlaMonitor monitor, EscalationService escalation, IncidentSource source) {
        this.monitor = monitor;
        this.escalation = escalation;
        this.source = source;
    }

    @Scheduled(fixedRate = 60_000)
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        List<Incident> open = source.findOpenIncidents();
        Set<String> changed = new HashSet<>();
        for (SlaAlert alert : monitor.scan(open, now)) {
            changed.add(alert.incidentId());
        }
        for (EscalationEvent event : escalation.evaluate(open, now)) {
            changed.add(event.incidentId());
        }
        open.stream().filter(i -> changed.contains(i.getId())).forEach(source::save);
    }
}
