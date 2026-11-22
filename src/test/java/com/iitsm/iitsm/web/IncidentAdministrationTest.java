package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.repository.AuditLogRepository;
import com.iitsm.iitsm.service.SlaService;

/** Priority change, closure information and before/after states in the audit trail. */
@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:incidentadmintest", "app.max-reports-per-hour=1000", "app.max-active-incidents=1000"})
class IncidentAdministrationTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired AuditLogRepository auditLogs;
    @Autowired SlaService sla;

    private Incident report(String text) {
        return workflow.report("employee1", text, Category.NETWORK, 1, 1, "Intranet").incident();
    }

    @Test
    void adminRaisesPriorityAndDeadlinesFollow() {
        Incident inc = report("Printer queue stuck in room 12");
        Priority before = inc.getPriority();

        workflow.changePriority(inc.getId(), Priority.P1, "Whole floor affected");

        Incident after = incidents.findById(inc.getId());
        assertEquals(Priority.P1, after.getPriority());
        assertEquals(after.getCreatedAt().plusMinutes(30), sla.resolutionDeadline(after.getCreatedAt(), Priority.P1));
        assertTrue(auditLogs.findAll().stream().anyMatch(a -> a.getAction().equals("PRIORITY_CHANGED")
                && a.getEntityId().equals(inc.getId())
                && before.name().equals(a.getPreviousState()) && "P1".equals(a.getNewState())));
    }

    @Test
    void priorityChangeRules() {
        Incident inc = report("Desk phone has no dial tone");
        assertThrows(InvalidIncidentDataException.class, () -> workflow.changePriority(inc.getId(), Priority.P1, " "));
        assertThrows(InvalidIncidentDataException.class,
                () -> workflow.changePriority(inc.getId(), inc.getPriority(), "same as before"));
    }

    @Test
    void closureInformationIsStored() {
        Incident inc = report("Wall screen in lobby flickers");
        String tech = inc.getAssignedTechnician().getId();
        workflow.acknowledge(inc.getId(), tech);
        workflow.start(inc.getId(), tech);
        workflow.resolve(inc.getId(), tech, "Replaced HDMI cable");

        workflow.confirmResolution(inc.getId(), "employee1");

        Incident closed = incidents.findById(inc.getId());
        assertNotNull(closed.getClosedAt());
        assertEquals("employee1", closed.getClosedBy());
        assertThrows(InvalidIncidentDataException.class,
                () -> workflow.changePriority(inc.getId(), Priority.P1, "too late"));
    }

    @Test
    void autoClosedIncidentsRecordTheSystem() {
        Incident inc = report("Meeting room booking tablet frozen");
        String tech = inc.getAssignedTechnician().getId();
        workflow.acknowledge(inc.getId(), tech);
        workflow.start(inc.getId(), tech);
        workflow.resolve(inc.getId(), tech, "Restarted tablet");

        workflow.autoCloseResolvedBefore(LocalDateTime.now().plusMinutes(1));

        assertEquals("system (auto-close)", incidents.findById(inc.getId()).getClosedBy());
    }

    @Test
    void statusChangesStorePreviousAndNewState() {
        Incident inc = report("Badge printer offline");
        workflow.acknowledge(inc.getId(), inc.getAssignedTechnician().getId());

        assertTrue(auditLogs.findAll().stream().anyMatch(a -> a.getEntityId().equals(inc.getId())
                && "ASSIGNED".equals(a.getPreviousState()) && "ACKNOWLEDGED".equals(a.getNewState())));
    }
}
