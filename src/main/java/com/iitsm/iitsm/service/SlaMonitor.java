package com.iitsm.iitsm.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.AlertType;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.SlaAlert;
import com.iitsm.iitsm.model.SlaStatus;
import com.iitsm.iitsm.observer.SlaAlertListener;

@Component
public class SlaMonitor {
    private final SlaService slaService;
    private final List<SlaAlertListener> listeners;
    private final Map<String, SlaStatus> lastReported = new HashMap<>();

    public SlaMonitor(SlaService slaService, List<SlaAlertListener> listeners) {
        this.slaService = slaService;
        this.listeners = listeners;
    }

    /** Checks every open incident and returns the NEW alerts raised by this scan. */
    public List<SlaAlert> scan(List<Incident> incidents, LocalDateTime now) {
        List<SlaAlert> raised = new ArrayList<>();
        for (Incident incident : incidents) {
            if (!isOpen(incident)) continue;

            SlaStatus response = slaService.checkResponse(
                    incident.getCreatedAt(), incident.getPriority(),
                    incident.getAcknowledgedAt(), now);
            report(incident, AlertType.RESPONSE, response, now, raised);

            SlaStatus resolution = slaService.checkResolution(
                    incident.getCreatedAt(), incident.getPriority(), now);
            report(incident, AlertType.RESOLUTION, resolution, now, raised);
        }
        return raised;
    }

    private boolean isOpen(Incident incident) {
        IncidentStatus s = incident.getStatus();
        return incident.getPriority() != null
                && !incident.isLinked()          // the parent incident carries the SLA
                && s != IncidentStatus.RESOLVED
                && s != IncidentStatus.VERIFIED
                && s != IncidentStatus.CLOSED;
    }

    private void report(Incident incident, AlertType type, SlaStatus status,
                        LocalDateTime now, List<SlaAlert> raised) {
        String key = incident.getId() + ":" + type;
        SlaStatus previous = lastReported.getOrDefault(key, SlaStatus.OK);
        if (status == previous) return;

        lastReported.put(key, status);
        if (status != SlaStatus.OK) {
            SlaAlert alert = new SlaAlert(incident.getId(), type, status, now);
            raised.add(alert);
            for (SlaAlertListener listener : listeners) {
                listener.onAlert(alert);
            }
        }
    }
}