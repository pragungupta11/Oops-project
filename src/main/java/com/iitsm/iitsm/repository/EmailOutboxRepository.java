package com.iitsm.iitsm.repository;

import com.iitsm.iitsm.persistence.entity.EmailOutboxEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntity, Long> {
    List<EmailOutboxEntity> findAllByOrderBySentAtDesc();
    List<EmailOutboxEntity> findByToAddress(String toAddress);
}
