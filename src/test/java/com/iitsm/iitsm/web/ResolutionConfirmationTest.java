package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;
import com.iitsm.iitsm.repository.AuditLogRepository;

/** The employee confirms the fix (closes it) or reopens it; unanswered ones close automatically. */
@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:resolutionconfirmationtest", "app.max-reports-per-hour=1000", "app.max-active-incidents=1000"})
class ResolutionConfirmationTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired TechnicianPersistenceService technicians;
    @Autowired IncidentConversationService conversation;
    @Autowired AuditLogRepository auditLogs;

    private Incident resolved(String description) {
        Incident inc = workflow.report("employee1", description, Category.APPLICATION, 2, 2, "Payment Gateway").incident();
        String tech = inc.getAssignedTechnician().getId();
        workflow.acknowledge(inc.getId(), tech);
        workflow.start(inc.getId(), tech);
        return workflow.resolve(inc.getId(), tech, "Restarted the service");
    }

    @Test
    void confirmingClosesTheIncident() {
        Incident inc = resolved("Receipt printer page blank");

        workflow.confirmResolution(inc.getId(), "employee1");

        assertEquals(IncidentStatus.CLOSED, incidents.findById(inc.getId()).getStatus());
        assertTrue(auditLogs.findAll().stream()
                .anyMatch(a -> a.getAction().equals("RESOLUTION_CONFIRMED") && a.getEntityId().equals(inc.getId())));
    }

    @Test
    void stillBrokenReopensItForTheSameTechnician() {
        Incident inc = resolved("Refund button greyed out");
        String tech = inc.getAssignedTechnician().getId();
        int loadBefore = technicians.findById(tech).getActiveIncidents();

        workflow.reopen(inc.getId(), "employee1", "Still greyed out for refunds over 1000");

        Incident after = incidents.findById(inc.getId());
        assertEquals(IncidentStatus.IN_PROGRESS, after.getStatus());
        assertNull(after.getResolution());
        assertEquals(tech, after.getAssignedTechnician().getId());
        assertEquals(loadBefore + 1, technicians.findById(tech).getActiveIncidents());
        assertTrue(conversation.thread(inc.getId(), true).stream()
                .anyMatch(c -> c.text().contains("Still greyed out")));
        workflow.resolve(inc.getId(), tech, "Fixed the limit check");   // the technician can resolve again
    }

    @Test
    void reopeningNeedsAReasonAndOnlyTheReporterCanDoIt() {
        Incident inc = resolved("Coupon field rejects valid codes");
        assertThrows(InvalidIncidentDataException.class, () -> workflow.reopen(inc.getId(), "employee1", "  "));
        assertThrows(AccessDeniedException.class, () -> workflow.confirmResolution(inc.getId(), "employee2"));
    }

    @Test
    void onlyResolvedIncidentsCanBeConfirmed() {
        Incident open = workflow.report("employee1", "Wishlist page loads forever", Category.APPLICATION, 2, 2,
                "Payment Gateway").incident();
        assertThrows(InvalidIncidentDataException.class, () -> workflow.confirmResolution(open.getId(), "employee1"));
    }

    @Test
    void unansweredResolvedIncidentsCloseAutomatically() {
        Incident inc = resolved("Gift card balance shows zero");

        workflow.autoCloseResolvedBefore(LocalDateTime.now().minusDays(3));     // too recent: stays resolved
        assertEquals(IncidentStatus.RESOLVED, incidents.findById(inc.getId()).getStatus());

        workflow.autoCloseResolvedBefore(LocalDateTime.now().plusMinutes(1));   // waited long enough
        assertEquals(IncidentStatus.CLOSED, incidents.findById(inc.getId()).getStatus());
        assertTrue(auditLogs.findAll().stream()
                .anyMatch(a -> a.getAction().equals("AUTO_CLOSED") && a.getEntityId().equals(inc.getId())));
    }

    @Test
    void adminOverrideNeedsAReasonAndOnlyWorksOnResolvedIncidents() {
        Incident inc = resolved("Loyalty points not credited");
        assertThrows(InvalidIncidentDataException.class, () -> workflow.adminClose(inc.getId(), " "));

        workflow.adminClose(inc.getId(), "Employee on leave, fix confirmed by team lead");
        assertEquals(IncidentStatus.CLOSED, incidents.findById(inc.getId()).getStatus());

        Incident open = workflow.report("employee1", "Order history tab empty", Category.APPLICATION, 2, 2,
                "Payment Gateway").incident();
        assertThrows(com.iitsm.iitsm.exception.InvalidStateTransitionException.class,
                () -> workflow.adminClose(open.getId(), "trying to skip the work"));
    }
}
