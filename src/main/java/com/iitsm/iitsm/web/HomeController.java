package com.iitsm.iitsm.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Sends each logged-in user to the home page of their role. */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            switch (authority.getAuthority()) {
                case "ROLE_ADMIN":
                    return "redirect:/admin/dashboard";
                case "ROLE_TECHNICIAN":
                    return "redirect:/technician/incidents";
                case "ROLE_EMPLOYEE":
                    return "redirect:/employee/incidents";
                default:
                    break;
            }
        }
        return "redirect:/employee/incidents";
    }
}
