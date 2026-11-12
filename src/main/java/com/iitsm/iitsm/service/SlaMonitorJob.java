package com.iitsm.iitsm.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.Incident;

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
        monitor.scan(open, now);
        escalation.evaluate(open, now);
    }
}