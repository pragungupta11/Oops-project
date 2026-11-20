package com.iitsm.iitsm.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;

@Component
@Primary
public class DatabaseIncidentSource implements IncidentSource {
    private static final Set<IncidentStatus> FINISHED =
            EnumSet.of(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);

    private final IncidentPersistenceService incidents;

    public DatabaseIncidentSource(IncidentPersistenceService incidents) {
        this.incidents = incidents;
    }

    @Override
    public List<Incident> findOpenIncidents() {
        return incidents.findAll().stream()
                .filter(i -> !FINISHED.contains(i.getStatus()))
                .toList();
    }

    @Override
    public void save(Incident incident) {
        incidents.save(incident);
    }
}