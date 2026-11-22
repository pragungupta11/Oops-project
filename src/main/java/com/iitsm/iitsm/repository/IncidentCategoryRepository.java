package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.IncidentCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentCategoryRepository extends JpaRepository<IncidentCategoryEntity, String> {
}
