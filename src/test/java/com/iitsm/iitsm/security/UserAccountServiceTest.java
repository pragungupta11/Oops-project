package com.iitsm.iitsm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.AccountException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;
import com.iitsm.iitsm.repository.AppUserRepository;
import com.iitsm.iitsm.web.IncidentWorkflowService;

@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:accounttest", "app.max-reports-per-hour=1000", "app.max-active-incidents=1000"})
class UserAccountServiceTest {

    @Autowired UserAccountService accounts;
    @Autowired AppUserRepository users;
    @Autowired AppUserDetailsService login;
    @Autowired TechnicianPersistenceService technicians;
    @Autowired IncidentWorkflowService workflow;
    @Autowired com.iitsm.iitsm.persistence.service.IncidentPersistenceService incidents;

    @Test
    void signUpAlwaysCreatesAnEmployee() {
        accounts.registerEmployee("Priya Shah", "priya.s", "priya.s@company.com", "secret123", "secret123");

        assertEquals("EMPLOYEE", users.findByUsername("priya.s").orElseThrow().getRole());
        assertEquals("Priya Shah", users.findByUsername("priya.s").orElseThrow().getFullName());
        assertTrue(login.loadUserByUsername("priya.s").isEnabled());
    }

    @Test
    void signUpGuardrails() {
        // outside e-mail domain
        assertThrows(AccountException.class,
                () -> accounts.registerEmployee("Eve", "eve", "eve@gmail.com", "secret123", "secret123"));
        // reserved name (impersonation)
        assertThrows(AccountException.class,
                () -> accounts.registerEmployee("Fake Support", "support", "support2@company.com", "secret123", "secret123"));
        // weak password, mismatch, bad username
        assertThrows(AccountException.class,
                () -> accounts.registerEmployee("Raj", "raj", "raj@company.com", "password", "password"));
        assertThrows(AccountException.class,
                () -> accounts.registerEmployee("Raj", "raj", "raj@company.com", "secret123", "secret124"));
        assertThrows(AccountException.class,
                () -> accounts.registerEmployee("Raj", "R@J!", "raj@company.com", "secret123", "secret123"));
    }

    @Test
    void usernameAndEmailMustBeUnique() {
        accounts.registerEmployee("Anil Rao", "anil", "anil@company.com", "secret123", "secret123");
        assertThrows(AccountException.class,
                () -> accounts.registerEmployee("Anil Two", "anil", "anil2@company.com", "secret123", "secret123"));
        assertThrows(AccountException.class,
                () -> accounts.registerEmployee("Anil Three", "anil3", "ANIL@company.com", "secret123", "secret123"));
    }

    @Test
    void adminCreatesATechnicianWithLoginAndTechnicianRecordTogether() {
        accounts.createByAdmin("Kiran Das", "kiran", null, "temp1234", "TECHNICIAN", Set.of("Database", "Cloud"));

        assertEquals("TECHNICIAN", users.findByUsername("kiran").orElseThrow().getRole());
        assertEquals(Set.of("Database", "Cloud"), technicians.findById("kiran").getSkills());
    }

    @Test
    void aTechnicianNeedsAtLeastOneSkill() {
        assertThrows(AccountException.class,
                () -> accounts.createByAdmin("No Skills", "noskills", null, "temp1234", "TECHNICIAN", Set.of()));
        assertFalse(users.existsByUsername("noskills"));   // nothing half-created
    }

    @Test
    void deactivatedAccountsCannotSignIn() {
        accounts.registerEmployee("Leaving Soon", "leaver", "leaver@company.com", "secret123", "secret123");

        accounts.setEnabled("leaver", false, "admin");

        assertFalse(login.loadUserByUsername("leaver").isEnabled());
        assertThrows(AccountException.class, () -> accounts.setEnabled("admin", false, "admin"));   // not yourself
    }

    @Test
    void unavailableTechniciansGetNoNewWork() {
        accounts.createByAdmin("Only Cloud", "cloudonly", null, "temp1234", "TECHNICIAN", Set.of("Cloud"));
        technicians.findAll().stream()
                .filter(t -> t.getSkills().contains("Cloud") && !t.getId().equals("cloudonly"))
                .forEach(t -> accounts.setAvailability(t.getId(), false));
        accounts.setAvailability("cloudonly", false);

        // nobody available: the report is NOT lost, it waits in the queue unassigned
        var queued = workflow.report("employee1", "Cloud bucket permission denied",
                Category.CLOUD, 2, 2, "Payment Gateway").incident();
        assertEquals(null, queued.getAssignedTechnician());

        accounts.setAvailability("cloudonly", true);
        workflow.assignQueuedIncidents();
        assertEquals("cloudonly", incidents.findById(queued.getId()).getAssignedTechnician().getId());
    }
}
