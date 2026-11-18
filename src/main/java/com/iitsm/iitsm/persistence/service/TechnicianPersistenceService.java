package com.iitsm.iitsm.persistence.service;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.persistence.entity.TechnicianEntity;
import com.iitsm.iitsm.repository.TechnicianRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TechnicianPersistenceService {
    private final TechnicianRepository technicianRepository;
    private final AuditService auditService;

    public TechnicianPersistenceService(TechnicianRepository technicianRepository, AuditService auditService) {
        this.technicianRepository = technicianRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Technician save(Technician technician) {
        boolean existed = technicianRepository.existsById(technician.getId());
        TechnicianEntity saved = technicianRepository.save(TechnicianEntity.fromDomain(technician));
        auditService.record(existed ? "TECHNICIAN_UPDATED" : "TECHNICIAN_CREATED",
                "TECHNICIAN", saved.getId(), "Technician record saved");
        return saved.toDomain();
    }

    @Transactional(readOnly = true)
    public List<Technician> findAll() {
        return technicianRepository.findAll().stream().map(TechnicianEntity::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public Technician findById(String id) {
        return technicianRepository.findById(id).map(TechnicianEntity::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Technician not found: " + id));
    }
}
