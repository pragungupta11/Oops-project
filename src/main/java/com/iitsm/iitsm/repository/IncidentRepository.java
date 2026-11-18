package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.persistence.entity.IncidentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<IncidentEntity, String> {
    List<IncidentEntity> findByStatus(IncidentStatus status);
    List<IncidentEntity> findByAssignedTechnician_Id(String technicianId);
}
