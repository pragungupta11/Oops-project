package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;
import com.iitsm.iitsm.repository.AuditLogRepository;

@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:reassignmenttest", "app.max-reports-per-hour=1000", "app.max-active-incidents=1000"})
class ReassignmentWorkflowTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired TechnicianPersistenceService technicians;
    @Autowired AuditLogRepository auditLogs;

    @Test
    void wronglyCategorisedIncidentIsReroutedToASkilledTechnician() {
        // The employee picks NETWORK for a payment-app problem, so a network technician gets it.
        Incident reported = workflow.report("employee1", "Payment app down", Category.NETWORK,
                3, 3, "Payment Gateway").incident();
        String firstTech = reported.getAssignedTechnician().getId();
        int firstTechLoad = technicians.findById(firstTech).getActiveIncidents();

        workflow.requestReassignment(reported.getId(), firstTech, "Application", "This is the payment app");
        assertTrue(incidents.findById(reported.getId()).hasPendingReassignment());

        workflow.approveReassignment(reported.getId(), "auto");

        Incident after = incidents.findById(reported.getId());
        assertEquals("Application", after.getCategory());
        assertEquals("tech2", after.getAssignedTechnician().getId());   // Meera has the Application skill
        assertNotEquals(firstTech, after.getAssignedTechnician().getId());
        assertEquals(IncidentStatus.ASSIGNED, after.getStatus());
        assertFalse(after.hasPendingReassignment());
        assertEquals(firstTechLoad - 1, technicians.findById(firstTech).getActiveIncidents());
        assertTrue(auditLogs.findAll().stream()
                .anyMatch(a -> a.getAction().equals("REASSIGNMENT_APPROVED")
                        && a.getEntityId().equals(reported.getId())));
    }

    @Test
    void rejectedRequestKeepsTheOriginalTechnician() {
        Incident reported = workflow.report("employee2", "VPN keeps dropping", Category.NETWORK,
                2, 2, "Payment Gateway").incident();
        String tech = reported.getAssignedTechnician().getId();

        workflow.requestReassignment(reported.getId(), tech, "Cloud", "Might be cloud");
        workflow.rejectReassignment(reported.getId());

        Incident after = incidents.findById(reported.getId());
        assertEquals("Network", after.getCategory());
        assertEquals(tech, after.getAssignedTechnician().getId());
        assertFalse(after.hasPendingReassignment());
    }
}
