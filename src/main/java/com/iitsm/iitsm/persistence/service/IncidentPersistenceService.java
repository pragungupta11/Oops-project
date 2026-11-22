package com.iitsm.iitsm.persistence.service;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.persistence.entity.IncidentEntity;
import com.iitsm.iitsm.persistence.entity.TechnicianEntity;
import com.iitsm.iitsm.repository.IncidentRepository;
import com.iitsm.iitsm.repository.TechnicianRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentPersistenceService {
    private final IncidentRepository incidentRepository;
    private final TechnicianRepository technicianRepository;
    private final AuditService auditService;

    public IncidentPersistenceService(IncidentRepository incidentRepository,
                                      TechnicianRepository technicianRepository,
                                      AuditService auditService) {
        this.incidentRepository = incidentRepository;
        this.technicianRepository = technicianRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Incident save(Incident incident) {
        IncidentEntity before = incidentRepository.findById(incident.getId()).orElse(null);
        boolean existed = before != null;
        String previousState = before == null ? null : String.valueOf(before.getStatus());
        TechnicianEntity technician = null;
        if (incident.getAssignedTechnician() != null) {
            String technicianId = incident.getAssignedTechnician().getId();
            technician = technicianRepository.findById(technicianId)
                    .orElseThrow(() -> new IllegalArgumentException("Technician must be saved before assignment: " + technicianId));
        }
        IncidentEntity saved = incidentRepository.save(IncidentEntity.fromDomain(incident, technician));
        auditService.record(existed ? "INCIDENT_UPDATED" : "INCIDENT_CREATED", "INCIDENT",
                saved.getId(), "Incident record saved; status=" + incident.getStatus(),
                previousState, String.valueOf(incident.getStatus()));
        return saved.toDomain();
    }

    @Transactional(readOnly = true)
    public Incident findById(String id) {
        return incidentRepository.findById(id).map(IncidentEntity::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Incident> findAll() {
        return incidentRepository.findAll().stream().map(IncidentEntity::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public List<Incident> findByStatus(IncidentStatus status) {
        return incidentRepository.findByStatus(status).stream().map(IncidentEntity::toDomain).toList();
    }
}
