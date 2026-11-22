package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.persistence.entity.IncidentEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Targeted queries, so pages and jobs load only the incidents they need. */
public interface IncidentRepository extends JpaRepository<IncidentEntity, String> {
    List<IncidentEntity> findByStatus(IncidentStatus status);
    List<IncidentEntity> findByAssignedTechnician_Id(String technicianId);
    List<IncidentEntity> findByReporterUsername(String reporterUsername);
    List<IncidentEntity> findByStatusNotIn(Collection<IncidentStatus> statuses);
    List<IncidentEntity> findByParentId(String parentId);
    List<IncidentEntity> findByRequestedCategoryIsNotNull();
}
