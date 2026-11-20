package com.iitsm.iitsm.notification;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.persistence.entity.NotificationEntity;
import com.iitsm.iitsm.repository.NotificationRepository;

/** Channel 1: stored in the database and shown under the bell icon in the navbar. */
@Component
public class InAppNotificationChannel implements NotificationChannel {
    private final NotificationRepository repository;

    public InAppNotificationChannel(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() { return "in-app"; }

    @Override
    public void send(Notification n) {
        repository.save(new NotificationEntity(n.recipient(), Text.limit(n.subject(), 200),
                Text.limit(n.body(), 1000), n.incidentId()));
    }
}
