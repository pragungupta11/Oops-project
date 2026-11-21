package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.IncidentCommentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentCommentRepository extends JpaRepository<IncidentCommentEntity, Long> {
    List<IncidentCommentEntity> findByIncidentIdOrderByCreatedAtAscIdAsc(String incidentId);
}
