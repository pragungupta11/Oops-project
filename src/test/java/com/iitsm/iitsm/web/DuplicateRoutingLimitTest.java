package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;

/** Own database, because it deliberately overloads a technician. */
@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:duplicateroutinglimittest", "app.max-reports-per-hour=1000"})
class DuplicateRoutingLimitTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired TechnicianPersistenceService technicians;

    @Test
    void busyTechnicianIsNotGivenMoreWorkJustBecauseOfAHint() {
        Incident first = workflow.report("employee1", "Lab wifi drops constantly",
                Category.NETWORK, 2, 2, "Intranet").incident();
        Technician busy = technicians.findById(first.getAssignedTechnician().getId());
        technicians.save(new Technician(busy.getId(), busy.getName(), busy.getSkills(),
                IncidentWorkflowService.ROUTING_LOAD_LIMIT, true));

        Incident second = workflow.report("employee2", "Lab wifi dropping constantly",
                Category.NETWORK, 2, 2, "Intranet").incident();

        assertNotEquals(busy.getId(), second.getAssignedTechnician().getId());  // load balancing wins
        assertEquals(first.getId(), second.getSuggestedParentId());             // but the hint is kept
    }
}
