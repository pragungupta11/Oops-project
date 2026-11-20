package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.NotificationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findByRecipientOrderByCreatedAtDesc(String recipient);
    long countByRecipientAndReadFlagFalse(String recipient);
}
