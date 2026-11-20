package com.iitsm.iitsm.notification;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.iitsm.iitsm.repository.AppUserRepository;

/** Sends one message to each recipient through EVERY channel. */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final List<NotificationChannel> channels;
    private final AppUserRepository users;

    public NotificationService(List<NotificationChannel> channels, AppUserRepository users) {
        this.channels = channels;
        this.users = users;
    }

    public void notify(Collection<String> recipients, String subject, String body, String incidentId) {
        Set<String> unique = new LinkedHashSet<>();
        for (String r : recipients) {
            if (r != null && !r.isBlank()) unique.add(r);
        }
        for (String recipient : unique) {
            Notification n = new Notification(recipient, subject, body, incidentId);
            for (NotificationChannel channel : channels) {
                try {
                    channel.send(n);
                } catch (RuntimeException e) {
                    // A broken channel must never undo the incident action or block other channels.
                    log.warn("Notification via {} to {} failed: {}", channel.name(), recipient, e.getMessage());
                }
            }
        }
    }

    /** Usernames of all administrators (they receive SLA violations, escalations, etc.). */
    public List<String> adminUsernames() {
        return users.findAll().stream()
                .filter(u -> "ADMIN".equals(u.getRole()))
                .map(u -> u.getUsername())
                .toList();
    }

    public List<NotificationChannel> channels() {
        return channels;
    }
}
