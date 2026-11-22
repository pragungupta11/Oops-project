package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.AuditLogEntity;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Slice;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, String> {
    List<AuditLogEntity> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId);

    /** Newest entries first, one page at a time (the database does the limiting). */
    Slice<AuditLogEntity> findAllByOrderByOccurredAtDesc(Pageable page);

    /** Case-insensitive search over all text columns, newest first, one page at a time. */
    @Query("select a from AuditLogEntity a where lower(a.actor) like :q or lower(a.action) like :q "
            + "or lower(a.entityType) like :q or lower(a.entityId) like :q or lower(a.details) like :q "
            + "order by a.occurredAt desc")
    Slice<AuditLogEntity> search(@Param("q") String likePattern, Pageable page);

    /** Ids of the records that ever had a given action, e.g. every incident that was reopened. */
    @Query("select distinct a.entityId from AuditLogEntity a where a.action = :action")
    Set<String> entityIdsWithAction(@Param("action") String action);
}
