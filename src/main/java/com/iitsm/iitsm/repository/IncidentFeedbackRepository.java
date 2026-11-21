package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.IncidentFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentFeedbackRepository extends JpaRepository<IncidentFeedbackEntity, String> {
}
