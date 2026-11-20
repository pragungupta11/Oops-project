package com.iitsm.iitsm.notification;

import com.iitsm.iitsm.model.Incident;

/**
 * Published by the workflow whenever something important happens to an incident. The workflow
 * does not know who listens (Observer pattern via Spring's event bus).
 * previousTechnicianId is only used for REROUTED.
 */
public record IncidentEvent(IncidentEventType type, Incident incident, String previousTechnicianId) {
    public IncidentEvent(IncidentEventType type, Incident incident) {
        this(type, incident, null);
    }
}
