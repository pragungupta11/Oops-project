package com.iitsm.iitsm.audit;

import com.iitsm.iitsm.persistence.entity.AuditLogEntity;
import com.iitsm.iitsm.repository.AuditLogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(String action, String entityType, String entityId, String details) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null || !authentication.isAuthenticated()
                ? "system" : authentication.getName();
        auditLogRepository.save(new AuditLogEntity(actor, action, entityType, entityId, details));
    }
}
