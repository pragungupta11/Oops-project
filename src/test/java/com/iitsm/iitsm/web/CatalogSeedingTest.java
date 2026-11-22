package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.service.CategoryCatalog;

/**
 * Regression test: the built-in categories and services must survive even when the very first
 * use of the catalogues happens inside an action that fails and is rolled back.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:catalogseedingtest")
class CatalogSeedingTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired CategoryCatalog categories;
    @Autowired ServiceCatalog services;

    @Test
    void defaultsSurviveAFailedFirstAction() {
        // first ever use of both catalogues, inside a report that fails (unknown service) and rolls back
        assertThrows(IllegalArgumentException.class, () ->
                workflow.report("employee1", "Something broke", "Network", 1, 1, "No Such Service"));

        assertTrue(categories.activeNames().contains("Network"));
        assertTrue(services.all().stream().anyMatch(s -> s.getName().equals("Email")));
    }
}
