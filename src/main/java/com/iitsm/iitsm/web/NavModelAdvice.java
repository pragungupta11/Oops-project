package com.iitsm.iitsm.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds the logged-in user's name and role flags to every page's model,
 * so the navbar can show the right links without any extra security tags.
 */
@ControllerAdvice
public class NavModelAdvice {

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
