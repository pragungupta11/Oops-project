package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.ServiceSlaPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import com.iitsm.iitsm.model.Priority;
import java.util.Optional;

public interface ServiceSlaPolicyRepository extends JpaRepository<ServiceSlaPolicyEntity, Long> {
    Optional<ServiceSlaPolicyEntity> findByServiceNameAndPriority(String serviceName, Priority priority);
}
