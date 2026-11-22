package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.ItServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItServiceRepository extends JpaRepository<ItServiceEntity, String> {
}
