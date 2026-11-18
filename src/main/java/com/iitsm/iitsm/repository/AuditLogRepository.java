package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.AuditLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, String> {
    List<AuditLogEntity> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId);
    List<AuditLogEntity> findAllByOrderByOccurredAtDesc();
}
