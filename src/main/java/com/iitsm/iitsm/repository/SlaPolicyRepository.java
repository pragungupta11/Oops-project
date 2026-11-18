package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.persistence.entity.SlaPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicyEntity, Priority> { }
