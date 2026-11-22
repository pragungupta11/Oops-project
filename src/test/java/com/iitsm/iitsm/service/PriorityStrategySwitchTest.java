package com.iitsm.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.persistence.entity.AppSettingEntity;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.repository.AppSettingRepository;
import com.iitsm.iitsm.repository.AuditLogRepository;
import com.iitsm.iitsm.strategy.priority.ScoreBasedPriorityStrategy;
import com.iitsm.iitsm.strategy.priority.WeightedPriorityStrategy;
import com.iitsm.iitsm.web.IncidentWorkflowService;

/**
 * The admin switches how priority is calculated. Example used throughout: impact 3, urgency 1 on the
 * Intranet (criticality 1). Score-based: 3 x 1 x 1 = 3, so P4. Weighted: 0.5*3 + 0.3*1 + 0.2*1 = 2.0, so P2.
 */
@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:prioritystrategytest",
        "app.max-reports-per-hour=1000", "app.max-active-incidents=1000"})
class PriorityStrategySwitchTest {

    @Autowired PriorityStrategyRegistry registry;
    @Autowired IncidentProcessingService processing;
    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired AppSettingRepository settings;
    @Autowired AuditLogRepository auditLogs;

    @AfterEach
    void backToDefault() {
        registry.switchTo("scoreBased");
    }

    private Incident report(String text) {
        return workflow.report("employee1", text, Category.NETWORK, 3, 1, "Intranet").incident();
    }

    @Test
    void scoreBasedIsTheDefault() {
        assertInstanceOf(ScoreBasedPriorityStrategy.class, processing.getPriorityStrategy());
        assertEquals(Priority.P4, report("Printer offline on floor two").getPriority());
    }

    @Test
    void switchingChangesThePriorityOfNewIncidentsOnly() {
        Incident before = report("Shared drive mapping lost");
        assertEquals(Priority.P4, before.getPriority());

        registry.switchTo("weighted");

        assertEquals(Priority.P2, report("Shared drive mapping lost again").getPriority());
        assertEquals(Priority.P4, incidents.findById(before.getId()).getPriority());   // existing one unchanged
    }

    @Test
    void choiceIsSavedAuditedAndRestoredAfterARestart() {
        registry.switchTo("weighted");
        assertEquals("weighted", settings.findById(PriorityStrategyRegistry.SETTING_KEY).orElseThrow().getValue());
        assertTrue(auditLogs.findAll().stream().anyMatch(a -> a.getAction().equals("PRIORITY_STRATEGY_CHANGED")));

        processing.setPriorityStrategy(new ScoreBasedPriorityStrategy());   // as if freshly started
        registry.restoreSavedChoice();

        assertInstanceOf(WeightedPriorityStrategy.class, processing.getPriorityStrategy());
    }

    @Test
    void unknownStrategyIsRejectedAndUnknownSavedChoiceIgnored() {
        assertThrows(IllegalArgumentException.class, () -> registry.switchTo("random"));

        settings.save(new AppSettingEntity(PriorityStrategyRegistry.SETTING_KEY, "deletedStrategy"));
        registry.restoreSavedChoice();
        assertEquals("scoreBased", registry.currentName());
    }
}
