package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.TechnicianEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicianRepository extends JpaRepository<TechnicianEntity, String> { }
