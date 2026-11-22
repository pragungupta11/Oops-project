package com.iitsm.iitsm.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.persistence.entity.EmailOutboxEntity;
import com.iitsm.iitsm.repository.EmailOutboxRepository;
import com.iitsm.iitsm.repository.AppUserRepository;

/**
 * Channel 2: a simulated e-mail. It builds the real message (address, subject, body) but puts
 * it in an outbox the admin can read, instead of contacting a mail server. A real sender
 * (for example via Spring's JavaMailSender) would be another class behind the same interface.
 */
@Component
public class SimulatedEmailChannel implements NotificationChannel {
    static final String DOMAIN = "@company.com";

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailChannel.class);
    private final EmailOutboxRepository outbox;
    private final AppUserRepository users;

    public SimulatedEmailChannel(EmailOutboxRepository outbox, AppUserRepository users) {
        this.outbox = outbox;
        this.users = users;
    }

    /** The e-mail address the user registered with; demo accounts without one use username@company.com. */
    String addressOf(String username) {
        return users.findByUsername(username)
                .map(u -> u.getEmail())
                .filter(e -> e != null && !e.isBlank())
                .orElse(username + DOMAIN);
    }

    @Override
    public String name() { return "email (simulated)"; }

    @Override
    public void send(Notification n) {
        String to = addressOf(n.recipient());
        outbox.save(new EmailOutboxEntity(to, Text.limit("[I-ITSM] " + n.subject(), 200),
                Text.limit(n.body(), 1000)));
        log.info("[EMAIL] to={} subject={}", to, n.subject());
    }
}
