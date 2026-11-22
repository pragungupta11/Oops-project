package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.repository.AuditLogRepository;

/** Incidents have a subject; "I don't know" for the service is handled; technicians can correct the service. */
@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:subjectservicetest",
        "app.max-reports-per-hour=1000", "app.max-active-incidents=1000", "app.ai.gemini.api-key="})
class SubjectAndServiceTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired AuditLogRepository auditLogs;

    @Test
    void subjectIsStoredAndRequired() {
        Incident inc = workflow.report("employee1", "Laptop will not charge", "The charger light stays off since this morning",
                "Application", 1, 1, "Intranet").incident();
        assertEquals("Laptop will not charge", incidents.findById(inc.getId()).getSubject());

        assertThrows(InvalidIncidentDataException.class, () -> workflow.report("employee1", "  ", "details",
                "Application", 1, 1, "Intranet"));
        assertThrows(InvalidIncidentDataException.class, () -> workflow.report("employee1", "x".repeat(121), "details",
                "Application", 1, 1, "Intranet"));
    }

    @Test
    void incidentsWithoutASubjectShowTheStartOfTheirDescription() {
        Incident inc = workflow.report("employee2", "Monitor flickers when the docking station is used",
                "Application", 1, 1, "Intranet").incident();   // older API without a subject
        assertEquals("Monitor flickers when the docking station is used", inc.getSubject());
    }

    @Test
    void unknownServiceIsIdentifiedFromTheText() {
        Incident inc = workflow.report("employee1", "VPN keeps disconnecting", "Drops every few minutes from home",
                "Network", 2, 2, "").incident();
        assertEquals("VPN", inc.getServiceName());
        assertTrue(auditLogs.findAll().stream().anyMatch(a -> a.getAction().equals("SERVICE_IDENTIFIED")
                && a.getEntityId().equals(inc.getId())));
    }

    @Test
    void unknownServiceWithoutCluesIsNotSpecifiedWithMediumCriticality() {
        Incident inc = workflow.report("employee1", "Strange beeping", "My computer beeps twice at start",
                "Application", 2, 2, "").incident();
        assertEquals(ServiceCatalog.NOT_SPECIFIED, inc.getServiceName());
        assertEquals(2, inc.getCriticality());
    }

    @Test
    void technicianCorrectsTheServiceAndThePriorityFollows() {
        // Not specified (criticality 2): 2 x 3 x 2 = 12 -> P2
        Incident inc = workflow.report("employee2", "Checkout fails", "Buyers get an error at the last step",
                "Application", 2, 3, "").incident();
        assertEquals(Priority.P2, inc.getPriority());
        String tech = inc.getAssignedTechnician().getId();

        // Payment Gateway (criticality 3): 2 x 3 x 3 = 18 -> P1
        Incident fixed = workflow.changeService(inc.getId(), "Payment Gateway", tech);

        assertEquals("Payment Gateway", fixed.getServiceName());
        assertEquals(3, fixed.getCriticality());
        assertEquals(Priority.P1, fixed.getPriority());
        assertTrue(auditLogs.findAll().stream().anyMatch(a -> a.getAction().equals("SERVICE_CHANGED")
                && a.getEntityId().equals(inc.getId()) && "Payment Gateway".equals(a.getNewState())));
        assertThrows(InvalidIncidentDataException.class,
                () -> workflow.changeService(inc.getId(), "Payment Gateway", tech));   // already set
    }
}
