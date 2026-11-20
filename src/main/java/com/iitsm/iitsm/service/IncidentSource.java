package com.iitsm.iitsm.service;

import java.util.List;

import com.iitsm.iitsm.model.Incident;

public interface IncidentSource {
    List<Incident> findOpenIncidents();

    /** Saves changes the monitoring job made (e.g. a new escalation level). */
    default void save(Incident incident) {
        // In-memory incidents are shared objects, so there is nothing to save.
    }
}