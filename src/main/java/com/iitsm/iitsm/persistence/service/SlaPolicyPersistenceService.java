package com.iitsm.iitsm.persistence.service;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaPolicy;
import com.iitsm.iitsm.persistence.entity.SlaPolicyEntity;
import com.iitsm.iitsm.repository.SlaPolicyRepository;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaPolicyPersistenceService {
    private final SlaPolicyRepository repository;
    private final AuditService auditService;

    public SlaPolicyPersistenceService(SlaPolicyRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public SlaPolicy save(Priority priority, SlaPolicy policy) {
        SlaPolicyEntity saved = repository.save(SlaPolicyEntity.fromDomain(priority, policy));
        auditService.record("SLA_POLICY_SAVED", "SLA_POLICY", priority.name(),
                "Response target=" + policy.responseTarget() + ", resolution target=" + policy.resolutionTarget());
        return saved.toDomain();
    }

    @Transactional(readOnly = true)
    public SlaPolicy findByPriority(Priority priority) {
        return repository.findById(priority).map(SlaPolicyEntity::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("No SLA policy configured for " + priority));
    }

    @Transactional(readOnly = true)
    public Map<Priority, SlaPolicy> findAll() {
        Map<Priority, SlaPolicy> policies = new EnumMap<>(Priority.class);
        repository.findAll().forEach(entity -> policies.put(entity.getPriority(), entity.toDomain()));
        return policies;
    }
}
