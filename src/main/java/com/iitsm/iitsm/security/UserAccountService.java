package com.iitsm.iitsm.security;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.persistence.entity.AppUserEntity;
import com.iitsm.iitsm.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class UserAccountService {
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "EMPLOYEE", "TECHNICIAN", "ADMIN");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserAccountService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                              AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public void createUser(String username, String rawPassword, String role) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        if (rawPassword == null || rawPassword.length() < 8)
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        String normalizedRole = role == null ? "USER" : role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(normalizedRole))
            throw new IllegalArgumentException("Role must be one of " + ALLOWED_ROLES);
        if (userRepository.existsByUsername(username))
            throw new IllegalArgumentException("Username already exists: " + username);
        userRepository.save(new AppUserEntity(username, passwordEncoder.encode(rawPassword), normalizedRole));
        auditService.record("USER_CREATED", "USER", username, "Account created with role " + normalizedRole);
    }
}
