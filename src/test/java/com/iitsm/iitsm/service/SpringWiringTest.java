package com.iitsm.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.iitsm.iitsm.strategy.assignment.LoadBasedStrategy;
import com.iitsm.iitsm.strategy.assignment.SkillBasedStrategy;

@SpringBootTest
class SpringWiringTest {

    @Autowired IncidentProcessingService processing;
    @Autowired AssignmentStrategyRegistry registry;

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