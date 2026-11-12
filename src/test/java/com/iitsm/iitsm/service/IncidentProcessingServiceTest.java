package com.iitsm.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.ProcessingResult;
import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.strategy.assignment.SkillBasedStrategy;
import com.iitsm.iitsm.strategy.priority.ScoreBasedPriorityStrategy;

class IncidentProcessingServiceTest {
    private final LocalDateTime created = LocalDateTime.of(2026, 11, 11, 10, 0);

    private List<Technician> technicians() {
        return List.of(
            new Technician("T1", "Asha", Set.of("Network"), 5, true),
            new Technician("T2", "Ravi", Set.of("Application"), 2, true));
    }

    private IncidentProcessingService service() {
        return new IncidentProcessingService(
            new ScoreBasedPriorityStrategy(), new SkillBasedStrategy(), new SlaService());
    }

    @Test
    void paymentOutageIsPrioritisedAssignedAndGivenDeadlines() {
        Incident incident = new Incident("INC1", "Payment app down", "Application", 3, 3, 3, created);

        ProcessingResult result = service().process(incident, technicians());

        assertEquals(Priority.P1, result.priority());
        assertEquals("Ravi", result.technician().getName());
        assertEquals(created.plusMinutes(30), result.resolutionDeadline());
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
    }

    @Test
    void noMatchingTechnicianLeavesIncidentClassified() {
        Incident incident = new Incident("INC2", "Suspicious login", "Security", 2, 2, 2, created);

        assertThrows(TechnicianUnavailableException.class,
                () -> service().process(incident, technicians()));
        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());
    }
}