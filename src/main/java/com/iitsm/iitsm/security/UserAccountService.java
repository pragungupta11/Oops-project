package com.iitsm.iitsm.security;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.exception.AccountException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.persistence.entity.AppUserEntity;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;
import com.iitsm.iitsm.repository.AppUserRepository;

/**
 * All account rules in one place.
 * - Employees may sign up themselves (low privilege), with guardrails: company e-mail only,
 *   reserved names blocked, strong password. The role is never taken from the browser.
 * - Technicians and admins are created by an admin only (high privilege).
 * - Abuse is handled afterwards: an admin can deactivate any account.
 */
@Service
public class UserAccountService {
    static final Set<String> ROLES = Set.of("EMPLOYEE", "TECHNICIAN", "ADMIN");

    /** Names that could be used to impersonate staff or the system. */
    static final Set<String> RESERVED = Set.of("admin", "administrator", "root", "system", "support",
            "it", "itsupport", "helpdesk", "servicedesk", "security", "ceo", "manager", "technician",
            "staff", "service", "noreply");

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._]{3,30}");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final TechnicianPersistenceService technicians;
    private final String emailDomain;
    private final com.iitsm.iitsm.service.CategoryCatalog categories;

    public UserAccountService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                              AuditService auditService, TechnicianPersistenceService technicians,
                              @Value("${app.signup-email-domain:company.com}") String emailDomain,
                              com.iitsm.iitsm.service.CategoryCatalog categories) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.technicians = technicians;
        this.emailDomain = emailDomain.toLowerCase();
        this.categories = categories;
    }

    public String emailDomain() {
        return emailDomain;
    }

    // ------------------------------------------------------------------ self sign-up (employees only)

    @Transactional
    public void registerEmployee(String fullName, String username, String email, String password, String confirm) {
        String user = checkUsername(username, true);
        String mail = checkEmail(email, true);
        String name = checkName(fullName);
        checkPassword(password, confirm);
        userRepository.save(new AppUserEntity(user, passwordEncoder.encode(password), "EMPLOYEE", name, mail));
        auditService.record("USER_REGISTERED", "USER", user, "Self sign-up as EMPLOYEE (" + mail + ")");
    }

    // ------------------------------------------------------------------ admin

    /**
     * Admin creates any account. For a technician, the login and the technician record are created
     * together in ONE transaction, so there is never a technician login without a technician.
     */
    @Transactional
    public void createByAdmin(String fullName, String username, String email, String password,
                              String role, Set<String> skills) {
        String r = role == null ? "" : role.trim().toUpperCase();
        if (!ROLES.contains(r)) throw new AccountException("Choose a role: Employee, Technician or Admin");
        String user = checkUsername(username, false);
        String mail = (email == null || email.isBlank()) ? null : checkEmail(email, false);
        String name = checkName(fullName);
        checkPassword(password, password);
        Set<String> skillSet = r.equals("TECHNICIAN") ? checkSkills(skills) : Set.of();

        userRepository.save(new AppUserEntity(user, passwordEncoder.encode(password), r, name, mail));
        if (r.equals("TECHNICIAN")) {
            technicians.save(new Technician(user, name, skillSet, 0, true));
        }
        auditService.record("USER_CREATED", "USER", user, "Created by admin with role " + r
                + (skillSet.isEmpty() ? "" : ", skills " + skillSet));
    }

    /** Deactivate (login refused) or reactivate an account. Deactivated technicians get no new work. */
    @Transactional
    public void setEnabled(String username, boolean enabled, String actingAdmin) {
        if (username.equals(actingAdmin) && !enabled) {
            throw new AccountException("You cannot deactivate your own account");
        }
        AppUserEntity account = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccountException("No such user: " + username));
        if (!enabled && "ADMIN".equals(account.getRole()) && activeAdmins() <= 1) {
            throw new AccountException("At least one active admin is required");
        }
        account.setEnabled(enabled);
        userRepository.save(account);
        if ("TECHNICIAN".equals(account.getRole()) && !enabled) {
            Technician t = technicians.findById(username);
            t.setAvailable(false);
            technicians.save(t);
        }
        auditService.record(enabled ? "USER_REACTIVATED" : "USER_DEACTIVATED", "USER", username,
                (enabled ? "Reactivated" : "Deactivated") + " by " + actingAdmin);
    }

    /** Admin changes a technician's skills (at least one). */
    @Transactional
    public void updateSkills(String technicianId, Set<String> skills) {
        Set<String> skillSet = checkSkills(skills);
        Technician t = technicians.findById(technicianId);
        technicians.save(new Technician(t.getId(), t.getName(), skillSet, t.getActiveIncidents(), t.isAvailable()));
        auditService.record("SKILLS_CHANGED", "TECHNICIAN", technicianId, "Skills now " + skillSet);
    }

    /** Admin marks a technician available or unavailable (e.g. on leave): assignment skips unavailable ones. */
    @Transactional
    public void setAvailability(String technicianId, boolean available) {
        Technician t = technicians.findById(technicianId);
        if (available) {
            boolean accountActive = userRepository.findByUsername(technicianId).map(AppUserEntity::isEnabled).orElse(true);
            if (!accountActive) throw new AccountException("Reactivate the account first");
        }
        t.setAvailable(available);
        technicians.save(t);
        auditService.record("AVAILABILITY_CHANGED", "TECHNICIAN", technicianId, available ? "Available" : "Unavailable");
    }

    // ------------------------------------------------------------------ used by the demo/admin initializers

    @Transactional
    public void createUser(String username, String rawPassword, String role) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        if (rawPassword == null || rawPassword.length() < 8)
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        String normalizedRole = role == null ? "EMPLOYEE" : role.trim().toUpperCase();
        if (!ROLES.contains(normalizedRole))
            throw new IllegalArgumentException("Role must be one of " + ROLES);
        if (userRepository.existsByUsername(username))
            throw new IllegalArgumentException("Username already exists: " + username);
        userRepository.save(new AppUserEntity(username, passwordEncoder.encode(rawPassword), normalizedRole));
        auditService.record("USER_CREATED", "USER", username, "Account created with role " + normalizedRole);
    }

    // ------------------------------------------------------------------ checks

    private String checkUsername(String username, boolean selfSignUp) {
        String u = username == null ? "" : username.trim().toLowerCase();
        if (!USERNAME.matcher(u).matches()) {
            throw new AccountException("Username: 3-30 characters, lowercase letters, digits, dot or underscore");
        }
        if (selfSignUp && RESERVED.contains(u)) {
            throw new AccountException("That username is reserved; please choose another");
        }
        if (userRepository.existsByUsername(u)) {
            throw new AccountException("That username is already taken");
        }
        return u;
    }

    private String checkEmail(String email, boolean companyOnly) {
        String e = email == null ? "" : email.trim().toLowerCase();
        if (!e.matches("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")) {
            throw new AccountException("Enter a valid e-mail address");
        }
        if (companyOnly && !e.endsWith("@" + emailDomain)) {
            throw new AccountException("Please use your company e-mail address (@" + emailDomain + ")");
        }
        if (userRepository.existsByEmailIgnoreCase(e)) {
            throw new AccountException("An account with this e-mail already exists");
        }
        return e;
    }

    private static String checkName(String fullName) {
        String n = fullName == null ? "" : fullName.trim();
        if (n.length() < 2 || n.length() > 100) throw new AccountException("Enter your full name");
        return n;
    }

    private static void checkPassword(String password, String confirm) {
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new AccountException("Password: at least 8 characters, with a letter and a digit");
        }
        if (!password.equals(confirm)) {
            throw new AccountException("The two passwords do not match");
        }
    }

    /** Skills a technician can have = the active categories. */
    public java.util.List<String> skillNames() {
        return categories.activeNames();
    }

    private Set<String> checkSkills(Set<String> skills) {
        Set<String> valid = new LinkedHashSet<>();
        if (skills != null) {
            for (String c : categories.activeNames()) {
                if (skills.contains(c)) valid.add(c);
            }
            for (Category c : Category.values()) {   // also accept the built-in constant names
                if (skills.contains(c.name()) && categories.activeNames().contains(c.label())) valid.add(c.label());
            }
        }
        if (valid.isEmpty()) throw new AccountException("A technician needs at least one skill");
        return valid;
    }

    private long activeAdmins() {
        return userRepository.findAll().stream()
                .filter(u -> "ADMIN".equals(u.getRole()) && u.isEnabled())
                .count();
    }
}
