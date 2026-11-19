package com.iitsm.iitsm.web;

import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;
import com.iitsm.iitsm.repository.AppUserRepository;
import com.iitsm.iitsm.repository.TechnicianRepository;
import com.iitsm.iitsm.security.UserAccountService;

/**
 * Creates demo logins on first start (all with password Demo@1234).
 * Each technician's id equals his username, so a login maps to a Technician record.
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {
    private static final String DEMO_PASSWORD = "Demo@1234";

    private final AppUserRepository userRepository;
    private final UserAccountService userAccountService;
    private final TechnicianRepository technicianRepository;
    private final TechnicianPersistenceService technicianService;

    public DemoDataInitializer(AppUserRepository userRepository,
                               UserAccountService userAccountService,
                               TechnicianRepository technicianRepository,
                               TechnicianPersistenceService technicianService) {
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
        this.technicianRepository = technicianRepository;
        this.technicianService = technicianService;
    }

    @Override
    public void run(ApplicationArguments args) {
        createUser("employee1", "EMPLOYEE");
        createUser("employee2", "EMPLOYEE");

        createTechnician("tech1", "Ravi Kumar", Set.of("Network", "Security"));
        createTechnician("tech2", "Meera Nair", Set.of("Database", "Application"));
        createTechnician("tech3", "Arjun Singh", Set.of("Authentication", "Cloud", "Network"));
    }

    private void createUser(String username, String role) {
        if (!userRepository.existsByUsername(username)) {
            userAccountService.createUser(username, DEMO_PASSWORD, role);
        }
    }

    private void createTechnician(String id, String name, Set<String> skills) {
        createUser(id, "TECHNICIAN");
        if (!technicianRepository.existsById(id)) {
            technicianService.save(new Technician(id, name, skills, 0, true));
        }
    }
}
