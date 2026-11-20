package com.iitsm.iitsm.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.persistence.entity.NotificationEntity;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.repository.EmailOutboxRepository;
import com.iitsm.iitsm.repository.NotificationRepository;
import com.iitsm.iitsm.service.SlaMonitorJob;
import com.iitsm.iitsm.web.IncidentWorkflowService;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:notificationflowtest")
class NotificationFlowTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired NotificationRepository inApp;
    @Autowired EmailOutboxRepository outbox;
    @Autowired NotificationService notificationService;
    @Autowired SlaMonitorJob slaJob;

    private boolean inAppHas(String user, String incidentId, String subjectPart) {
        List<NotificationEntity> mine = inApp.findByRecipientOrderByCreatedAtDesc(user);
        return mine.stream().anyMatch(n -> incidentId.equals(n.getIncidentId()) && n.getSubject().contains(subjectPart));
    }

    private boolean emailed(String user, String subjectPart) {
        return outbox.findByToAddress(user + SimulatedEmailChannel.DOMAIN).stream()
                .anyMatch(e -> e.getSubject().contains(subjectPart));
    }

    @Test
    void twoChannelsAreActive() {
        assertEquals(2, notificationService.channels().size());
    }

    @Test
    void reportingNotifiesTheEmployeeAndTheAssignedTechnicianOnBothChannels() {
        Incident inc = workflow.report("employee1", "Badge reader at main gate rejects cards",
                Category.NETWORK, 2, 2, "Intranet").incident();
        String tech = inc.getAssignedTechnician().getId();

        assertTrue(inAppHas("employee1", inc.getId(), "received"));
        assertTrue(inAppHas(tech, inc.getId(), "New"));
        assertTrue(emailed("employee1", inc.getId() + " received"));
        assertTrue(emailed(tech, inc.getId()));
    }

    @Test
    void resolvingNotifiesTheEmployeeAndAsksAdminsToVerify() {
        Incident inc = workflow.report("employee2", "Meeting room speaker crackles loudly",
                Category.NETWORK, 2, 2, "Intranet").incident();
        String tech = inc.getAssignedTechnician().getId();
        workflow.acknowledge(inc.getId(), tech);
        workflow.start(inc.getId(), tech);
        workflow.resolve(inc.getId(), tech, "Replaced the speaker cable");

        assertTrue(inAppHas("employee2", inc.getId(), "resolved"));
        assertTrue(notificationService.adminUsernames().stream()
                .allMatch(admin -> inAppHas(admin, inc.getId(), "verification")));
    }

    @Test
    void slaViolationAndEscalationReachTheAdmins() {
        Incident inc = new Incident("NOTIF-SLA-1", "Payment app down", "Application",
                3, 3, 3, LocalDateTime.now().minusMinutes(12));
        inc.setPriority(Priority.P1);
        inc.setStatus(IncidentStatus.ASSIGNED);
        incidents.save(inc);

        slaJob.run();

        String admin = notificationService.adminUsernames().get(0);
        assertTrue(inAppHas(admin, "NOTIF-SLA-1", "SLA violated"));
        assertTrue(inAppHas(admin, "NOTIF-SLA-1", "Escalated"));
    }
}
