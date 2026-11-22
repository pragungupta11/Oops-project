package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.TechnicianWorkloadExceededException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.repository.AuditLogRepository;

/**
 * Limit of 2 active incidents per technician. Database incidents can only go to tech2 (the only
 * technician with that skill in the demo data), so the third one has to wait in the queue.
 */
@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:workloadqueuetest",
        "app.max-active-incidents=2", "app.max-reports-per-hour=1000"})
class WorkloadLimitAndQueueTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired AuditLogRepository auditLogs;

    private Incident report(String text) {
        return workflow.report("employee1", text, Category.DATABASE, 2, 2, "Intranet").incident();
    }

    @Test
    void fullTechnicianMeansQueueNotErrorAndQueueDrainsWhenFree() {
        Incident a = report("Index rebuild stuck on orders table");
        report("Replication lag on reporting replica");
        assertEquals("tech2", a.getAssignedTechnician().getId());

        Incident third = report("Nightly vacuum job never finishes");          // tech2 is at the limit
        assertNull(third.getAssignedTechnician());
        assertEquals(IncidentStatus.CLASSIFIED, third.getStatus());
        assertTrue(auditLogs.findAll().stream().anyMatch(l -> l.getAction().equals("QUEUED_UNASSIGNED")
                && l.getEntityId().equals(third.getId()) && l.getDetails().contains("active incidents")));

        assertEquals(0, workflow.assignQueuedIncidents());                     // still full

        workflow.acknowledge(a.getId(), "tech2");
        workflow.start(a.getId(), "tech2");
        workflow.resolve(a.getId(), "tech2", "Rebuilt the index offline");    // frees one slot

        assertEquals(1, workflow.assignQueuedIncidents());
        Incident after = incidents.findById(third.getId());
        assertEquals("tech2", after.getAssignedTechnician().getId());
        assertEquals(IncidentStatus.ASSIGNED, after.getStatus());
    }

    @Test
    void adminCannotHandOverToSomeoneAtTheLimit() {
        // fill tech1 (Network) to the limit
        workflow.report("employee2", "Core switch port flapping", Category.NETWORK, 2, 2, "Intranet");
        workflow.report("employee2", "Guest wifi captive portal down", Category.NETWORK, 2, 2, "Intranet");
        workflow.report("employee2", "Firewall rule blocks payroll export", Category.NETWORK, 2, 2, "Intranet");
        workflow.report("employee2", "DNS lookups timing out on floor four", Category.NETWORK, 2, 2, "Intranet");
        // tech1 and tech3 both have Network; with 4 reports both are now at 2

        // an Authentication incident (tech3's skill; never touches tech2, so test order does not matter)
        Incident other = workflow.report("employee2", "Password reset email never arrives",
                Category.AUTHENTICATION, 2, 2, "Intranet").incident();
        assertThrows(TechnicianWorkloadExceededException.class, () -> workflow.handOver(other.getId(), "tech1"));
    }
}
