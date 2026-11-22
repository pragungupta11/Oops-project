package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.security.UserAccountService;

/** Open incidents of an unavailable or deactivated technician are handed over, never left stuck. */
@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:handovertest", "app.max-reports-per-hour=1000", "app.max-active-incidents=1000"})
class TechnicianHandoverTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired UserAccountService accounts;

    @Test
    void openIncidentsMoveToAnotherSkilledTechnician() {
        // Network: tech1 and tech3 both have the skill
        Incident inc = workflow.report("employee1", "Floor two switch blinking amber", Category.NETWORK, 2, 2, "Intranet").incident();
        String original = inc.getAssignedTechnician().getId();
        workflow.acknowledge(inc.getId(), original);

        accounts.setAvailability(original, false);
        IncidentWorkflowService.HandoverResult result = workflow.reassignOpenIncidentsOf(original);

        Incident after = incidents.findById(inc.getId());
        assertEquals(1, result.moved());
        assertNotEquals(original, after.getAssignedTechnician().getId());
        assertEquals(IncidentStatus.ASSIGNED, after.getStatus());   // the new technician acknowledges again
        accounts.setAvailability(original, true);
    }

    @Test
    void incidentsNobodyElseCanTakeAreReportedNotLost() {
        // Database: only tech2 has the skill
        Incident inc = workflow.report("employee2", "Nightly backup job failed", Category.DATABASE, 2, 2, "Intranet").incident();
        assertEquals("tech2", inc.getAssignedTechnician().getId());

        IncidentWorkflowService.HandoverResult result = workflow.reassignOpenIncidentsOf("tech2");

        assertTrue(result.notMoved().contains(inc.getId()));
        assertEquals("tech2", incidents.findById(inc.getId()).getAssignedTechnician().getId());
    }

    @Test
    void adminCanHandOverManuallyWhenNobodyHasTheSkill() {
        Incident inc = workflow.report("employee1", "Report export query very slow", Category.DATABASE, 2, 2, "Intranet").incident();
        assertEquals("tech2", inc.getAssignedTechnician().getId());

        workflow.handOver(inc.getId(), "tech1");   // admin decision: closest match

        assertEquals("tech1", incidents.findById(inc.getId()).getAssignedTechnician().getId());
    }
}
