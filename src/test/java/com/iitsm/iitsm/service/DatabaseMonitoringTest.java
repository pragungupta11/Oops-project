package com.iitsm.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.model.EscalationLevel;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.repository.AuditLogRepository;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:monitoringtest")
class DatabaseMonitoringTest {

    @Autowired SlaMonitorJob job;
    @Autowired IncidentPersistenceService incidents;
    @Autowired AuditLogRepository auditLogs;

    @Test
    void overdueCriticalIncidentIsEscalatedSavedAndAudited() {
        Incident incident = new Incident("TEST-ESC-1", "Payment app down", "Application",
                3, 3, 3, LocalDateTime.now().minusMinutes(12));
        incident.setPriority(Priority.P1);
        incident.setStatus(IncidentStatus.ASSIGNED);
        incidents.save(incident);

        job.run();

        Incident reloaded = incidents.findById("TEST-ESC-1");
        assertEquals(EscalationLevel.SYSTEM_ADMIN, reloaded.getEscalationLevel());
        assertTrue(auditLogs.findAll().stream()
                .anyMatch(a -> a.getAction().equals("INCIDENT_ESCALATED")
                        && a.getEntityId().equals("TEST-ESC-1")));
        assertTrue(auditLogs.findAll().stream()
                .anyMatch(a -> a.getAction().equals("SLA_VIOLATED")
                        && a.getEntityId().equals("TEST-ESC-1")));
    }
}