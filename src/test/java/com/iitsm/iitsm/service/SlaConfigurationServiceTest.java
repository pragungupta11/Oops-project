package com.iitsm.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.InvalidSlaConfigurationException;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaPolicy;
import com.iitsm.iitsm.persistence.service.SlaPolicyPersistenceService;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:slaconfigurationtest")
class SlaConfigurationServiceTest {

    @Autowired SlaConfigurationService config;
    @Autowired SlaService slaService;
    @Autowired SlaPolicyPersistenceService store;

    private final LocalDateTime created = LocalDateTime.of(2026, 11, 21, 10, 0);

    @AfterEach
    void restoreDefault() {
        config.update(Priority.P2, 15, 120);
    }

    @Test
    void adminChangeAppliesImmediatelyAndIsSaved() {
        config.update(Priority.P2, 10, 60);

        assertEquals(created.plusMinutes(60), slaService.resolutionDeadline(created, Priority.P2));
        assertEquals(Duration.ofMinutes(60), store.findByPriority(Priority.P2).resolutionTarget());
    }

    @Test
    void savedPoliciesAreLoadedOnStartup() {
        store.save(Priority.P2, new SlaPolicy(Duration.ofMinutes(20), Duration.ofMinutes(90)));

        config.loadFromDatabase();   // what happens when the application starts

        assertEquals(created.plusMinutes(90), slaService.resolutionDeadline(created, Priority.P2));
    }

    @Test
    void nonsenseTargetsAreRejected() {
        assertThrows(InvalidSlaConfigurationException.class, () -> config.update(Priority.P2, 0, 60));
        assertThrows(InvalidSlaConfigurationException.class, () -> config.update(Priority.P2, 90, 60));
        assertThrows(InvalidSlaConfigurationException.class,
                () -> config.update(Priority.P2, 10, SlaConfigurationService.MAX_MINUTES + 1));
    }
}
