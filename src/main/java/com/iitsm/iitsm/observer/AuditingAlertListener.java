package com.iitsm.iitsm.observer;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.model.EscalationEvent;
import com.iitsm.iitsm.model.SlaAlert;

@Component
public class AuditingAlertListener implements SlaAlertListener, EscalationListener {
    private final AuditService audit;

    public AuditingAlertListener(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void onAlert(SlaAlert alert) {
        audit.record("SLA_" + alert.status(), "INCIDENT", alert.incidentId(),
                alert.type() + " SLA is " + alert.status());
    }

    @Override
    public void onEscalation(EscalationEvent event) {
        audit.record("INCIDENT_ESCALATED", "INCIDENT", event.incidentId(),
                "Escalated from " + event.from() + " to " + event.to());
    }
}