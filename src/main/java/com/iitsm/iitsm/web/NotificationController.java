package com.iitsm.iitsm.web;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.iitsm.iitsm.persistence.entity.NotificationEntity;
import com.iitsm.iitsm.repository.EmailOutboxRepository;
import com.iitsm.iitsm.repository.NotificationRepository;

/** The bell page (every user) and the simulated e-mail outbox (admins only, via /admin/**). */
@Controller
public class NotificationController {
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    public record NotificationView(String subject, String body, String incidentId, String time, boolean isNew) {}

    public record EmailView(String to, String subject, String body, String time) {}

    private final NotificationRepository notifications;
    private final EmailOutboxRepository outbox;

    public NotificationController(NotificationRepository notifications, EmailOutboxRepository outbox) {
        this.notifications = notifications;
        this.outbox = outbox;
    }

    /** Shows the user's notifications, newest first; opening the page marks them as read. */
    @GetMapping("/notifications")
    @Transactional
    public String myNotifications(Authentication authentication, Model model) {
        List<NotificationEntity> mine = notifications.findByRecipientOrderByCreatedAtDesc(authentication.getName());
        List<NotificationView> views = mine.stream()
                .map(n -> new NotificationView(n.getSubject(), n.getBody(), n.getIncidentId(),
                        FORMAT.format(n.getCreatedAt()), !n.isReadFlag()))
                .toList();
        mine.forEach(NotificationEntity::markRead);
        notifications.saveAll(mine);
        model.addAttribute("items", views);
        return "notifications";
    }

    @GetMapping("/admin/outbox")
    public String outbox(Model model) {
        model.addAttribute("emails", outbox.findAllByOrderBySentAtDesc().stream()
                .map(e -> new EmailView(e.getToAddress(), e.getSubject(), e.getBody(), FORMAT.format(e.getSentAt())))
                .toList());
        return "admin/outbox";
    }
}
