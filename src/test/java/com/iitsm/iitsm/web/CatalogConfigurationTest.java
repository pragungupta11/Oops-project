package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.CatalogException;
import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.security.UserAccountService;
import com.iitsm.iitsm.service.CategoryCatalog;
import com.iitsm.iitsm.service.SlaConfigurationService;
import com.iitsm.iitsm.service.SlaService;
import com.iitsm.iitsm.strategy.category.CategorySuggester;

/** Admin-managed IT services, service-specific SLAs and admin-managed categories, end to end. */
@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:catalogtest",
        "app.max-reports-per-hour=1000", "app.max-active-incidents=1000", "app.ai.gemini.api-key="})
class CatalogConfigurationTest {

    @Autowired ServiceCatalog services;
    @Autowired CategoryCatalog categories;
    @Autowired SlaConfigurationService slaConfig;
    @Autowired SlaService sla;
    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired UserAccountService accounts;
    @Autowired CategorySuggester suggester;

    private final LocalDateTime created = LocalDateTime.of(2026, 11, 22, 10, 0);

    @Test
    void newServiceCanBeReportedAgainstAndItsCriticalityIsUsed() {
        services.add("Payroll", 3, Set.of("Intranet"));

        Incident inc = workflow.report("employee1", "Payslips not generated", "Application", 2, 2, "Payroll").incident();

        assertEquals("Payroll", inc.getServiceName());
        assertEquals(3, inc.getCriticality());
    }

    @Test
    void deactivatedServicesCannotBeReportedAgainst() {
        services.add("Old Fax Gateway", 1, Set.of());
        services.update("Old Fax Gateway", 1, Set.of(), false);

        assertThrows(IllegalArgumentException.class, () ->
                workflow.report("employee1", "Fax not sending", "Network", 1, 1, "Old Fax Gateway"));
    }

    @Test
    void serviceRulesAreChecked() {
        assertThrows(CatalogException.class, () -> services.add("Email", 2, Set.of()));            // duplicate
        assertThrows(CatalogException.class, () -> services.add("Badge System", 5, Set.of()));     // criticality
        assertThrows(CatalogException.class, () -> services.add("Loop", 2, Set.of("Loop")));       // unknown/self
    }

    @Test
    void serviceSpecificSlaOverridesTheDefaultForThatServiceOnly() {
        slaConfig.setServiceOverride("Payment Gateway", Priority.P1, 3, 15);
        try {
            assertEquals(created.plusMinutes(15), sla.resolutionDeadline(created, Priority.P1, "Payment Gateway"));
            assertEquals(created.plusMinutes(30), sla.resolutionDeadline(created, Priority.P1, "Email"));

            slaConfig.loadFromDatabase();   // survives a restart
            assertEquals(created.plusMinutes(15), sla.resolutionDeadline(created, Priority.P1, "Payment Gateway"));
        } finally {
            slaConfig.removeServiceOverride("Payment Gateway", Priority.P1);
        }
        assertEquals(created.plusMinutes(30), sla.resolutionDeadline(created, Priority.P1, "Payment Gateway"));
    }

    @Test
    void newCategoryIsSuggestedQueuedUntilSomeoneHasTheSkillThenAssigned() {
        categories.add("Hardware", "printer, laptop, keyboard, monitor");

        assertEquals("Hardware", suggester.suggest("Laptop screen broken and printer offline").orElseThrow().category());

        Incident inc = workflow.report("employee2", "Laptop keyboard keys stuck", "Hardware", 2, 2, "Intranet").incident();
        assertNull(inc.getAssignedTechnician());                       // nobody has the skill yet: queued

        accounts.updateSkills("tech1", Set.of("Network", "Security", "Hardware"));
        workflow.assignQueuedIncidents();
        assertEquals("tech1", incidents.findById(inc.getId()).getAssignedTechnician().getId());
    }

    @Test
    void deactivatedCategoryIsNotOfferedAnyMore() {
        categories.add("Telephony", "phone, dial, voicemail");
        categories.update("Telephony", "phone, dial, voicemail", false);

        assertThrows(InvalidIncidentDataException.class, () ->
                workflow.report("employee1", "Desk phone dead", "Telephony", 1, 1, "Intranet"));
        assertThrows(CatalogException.class, () -> categories.add("Network", ""));   // duplicate
        assertThrows(CatalogException.class, () -> categories.add("A/B", ""));       // not a valid name
    }
}
