package com.iitsm.iitsm.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.iitsm.iitsm.exception.AccountException;
import com.iitsm.iitsm.security.UserAccountService;

/** Public self sign-up. Always creates an EMPLOYEE: no role is accepted from the browser. */
@Controller
public class RegistrationController {
    private final UserAccountService accounts;

    public RegistrationController(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/register")
    public String form(Model model) {
        model.addAttribute("emailDomain", accounts.emailDomain());
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam(name = "fullName", required = false) String fullName,
                           @RequestParam(name = "username", required = false) String username,
                           @RequestParam(name = "email", required = false) String email,
                           @RequestParam(name = "password", required = false) String password,
                           @RequestParam(name = "confirm", required = false) String confirm,
                           Model model, RedirectAttributes redirect) {
        // First: every empty field, all at once, so the user sees everything that is missing.
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (fullName == null || fullName.isBlank()) missing.add("fullName");
        if (email == null || email.isBlank()) missing.add("email");
        if (username == null || username.isBlank()) missing.add("username");
        if (password == null || password.isEmpty()) missing.add("password");
        if (confirm == null || confirm.isEmpty()) missing.add("confirm");
        if (!missing.isEmpty()) {
            java.util.Map<String, String> names = java.util.Map.of("fullName", "Full name", "email", "Company e-mail",
                    "username", "Username", "password", "Password", "confirm", "Confirm password");
            model.addAttribute("error", "Please fill in the required field" + (missing.size() > 1 ? "s" : "") + ": "
                    + String.join(", ", missing.stream().map(names::get).toList()) + ".");
            return showAgain(model, missing, fullName, username, email);
        }
        try {
            accounts.registerEmployee(fullName, username, email, password, confirm);
            redirect.addFlashAttribute("registered", username.trim().toLowerCase());
            return "redirect:/login";
        } catch (AccountException e) {
            model.addAttribute("error", e.getMessage());
            return showAgain(model, java.util.List.of(), fullName, username, email);
        }
    }

    /** Shows the form again with what was typed (never the passwords) and the empty fields marked. */
    private String showAgain(Model model, java.util.List<String> missing, String fullName, String username, String email) {
        model.addAttribute("missing", missing);
        model.addAttribute("fullName", fullName);
        model.addAttribute("username", username);
        model.addAttribute("email", email);
        model.addAttribute("emailDomain", accounts.emailDomain());
        return "register";
    }
}
