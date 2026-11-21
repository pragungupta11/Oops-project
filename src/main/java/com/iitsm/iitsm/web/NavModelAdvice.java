package com.iitsm.iitsm.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;

import com.iitsm.iitsm.repository.NotificationRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds the logged-in user's name and role flags to every page's model,
 * so the navbar can show the right links without any extra security tags.
 */
@ControllerAdvice
public class NavModelAdvice {

    private final NotificationRepository notifications;
    private final String brand;
    private final String tagline;

    public NavModelAdvice(NotificationRepository notifications,
                          @Value("${app.brand:IncidentIQ}") String brand,
                          @Value("${app.tagline:}") String tagline) {
        this.notifications = notifications;
        this.brand = brand;
        this.tagline = tagline;
    }

    @ModelAttribute("brand")
    public String brand() { return brand; }

    @ModelAttribute("tagline")
    public String tagline() { return tagline; }

    /** Number shown on the bell icon. */
    @ModelAttribute("unreadNotifications")
    public long unreadNotifications(Authentication authentication) {
        return authentication == null ? 0 : notifications.countByRecipientAndReadFlagFalse(authentication.getName());
    }

    @ModelAttribute("currentUser")
    public String currentUser(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }

    @ModelAttribute("isEmployee")
    public boolean isEmployee(Authentication authentication) {
        return hasRole(authentication, "ROLE_EMPLOYEE");
    }

    @ModelAttribute("isTechnician")
    public boolean isTechnician(Authentication authentication) {
        return hasRole(authentication, "ROLE_TECHNICIAN");
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }
}
