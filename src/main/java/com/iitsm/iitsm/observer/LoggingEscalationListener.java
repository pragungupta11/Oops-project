package com.iitsm.iitsm.observer;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.EscalationEvent;

@Component
public class LoggingEscalationListener implements EscalationListener {
    @Override
    public void onEscalation(EscalationEvent event) {
        System.out.println("[ESCALATION] " + event.incidentId() + ": "
                + event.from() + " -> " + event.to() + " at " + event.detectedAt());
    }
}
