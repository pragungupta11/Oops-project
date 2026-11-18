package com.iitsm.iitsm.observer;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.SlaAlert;

@Component
public class LoggingSlaAlertListener implements SlaAlertListener {
    @Override
    public void onAlert(SlaAlert alert) {
        System.out.println("[SLA ALERT] " + alert.incidentId() + " " + alert.type()
                + " -> " + alert.status() + " at " + alert.detectedAt());
    }
}