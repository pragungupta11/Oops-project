package com.iitsm.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.persistence.entity.AppSettingEntity;
import com.iitsm.iitsm.repository.AppSettingRepository;
import com.iitsm.iitsm.repository.AuditLogRepository;
import com.iitsm.iitsm.strategy.assignment.LoadBasedStrategy;
import com.iitsm.iitsm.strategy.assignment.SkillBasedStrategy;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:springwiringtest")
class SpringWiringTest {

    @Autowired IncidentProcessingService processing;
    @Autowired AssignmentStrategyRegistry registry;
    @Autowired AppSettingRepository settings;
    @Autowired AuditLogRepository auditLogs;

    @Test
    void switchingIsSavedAndAudited() {
        registry.switchTo("loadBased");
        try {
            assertEquals("loadBased", registry.currentName());
            assertEquals("loadBased", settings.findById(AssignmentStrategyRegistry.SETTING_KEY).orElseThrow().getValue());
            assertTrue(auditLogs.findAll().stream()
                    .anyMatch(a -> a.getAction().equals("ASSIGNMENT_STRATEGY_CHANGED")));
        } finally {
            registry.switchTo("skillBased");
        }
    }

    @Test
    void savedChoiceIsRestoredOnStartup() {
        settings.save(new AppSettingEntity(AssignmentStrategyRegistry.SETTING_KEY, "loadBased"));
        try {
            registry.restoreSavedChoice();   // what happens when the app starts
            assertInstanceOf(LoadBasedStrategy.class, processing.getAssignmentStrategy());
        } finally {
            registry.switchTo("skillBased");
        }
    }

    @Test
    void unknownSavedChoiceIsIgnored() {
        settings.save(new AppSettingEntity(AssignmentStrategyRegistry.SETTING_KEY, "deletedStrategy"));
        try {
            registry.restoreSavedChoice();
            assertInstanceOf(SkillBasedStrategy.class, processing.getAssignmentStrategy());
        } finally {
            registry.switchTo("skillBased");
        }
    }

    @Test
    void defaultAssignmentStrategyIsSkillBased() {
        assertInstanceOf(SkillBasedStrategy.class, processing.getAssignmentStrategy());
    }

    @Test
    void registryListsBothStrategies() {
        assertTrue(registry.availableNames().contains("skillBased"));
        assertTrue(registry.availableNames().contains("loadBased"));
    }

    @Test
    void adminCanSwitchStrategyAtRuntime() {
        registry.switchTo("loadBased");
        assertInstanceOf(LoadBasedStrategy.class, processing.getAssignmentStrategy());
        registry.switchTo("skillBased");
    }

    @Test
    void unknownStrategyNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> registry.switchTo("random"));
    }
}