package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.AppSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSettingEntity, String> {
}
