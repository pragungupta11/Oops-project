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
        auditLogRepository.save(new AuditLogEntity(currentActor(), action, entityType, entityId, details));
    }

    /** Same, for a state change: previous and new state are stored in their own fields. */
    @Transactional
    public void record(String action, String entityType, String entityId, String details,
                       String previousState, String newState) {
        auditLogRepository.save(new AuditLogEntity(currentActor(), action, entityType, entityId, details,
                previousState, newState));
    }

    /** The logged-in user, or "system" for background jobs. */
    public static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                ? "system" : authentication.getName();
    }
}
