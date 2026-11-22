package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.ServiceSlaPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceSlaPolicyRepository extends JpaRepository<ServiceSlaPolicyEntity, Long> {
    java.util.Optional<ServiceSlaPolicyEntity> findByServiceNameAndPriority(String serviceName, com.iitsm.iitsm.model.Priority priority);
}
