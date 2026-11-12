package com.iitsm.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.model.EscalationEvent;
import com.iitsm.iitsm.model.EscalationLevel;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;

class EscalationServiceTest {
    private final LocalDateTime created = LocalDateTime.of(2026, 11, 12, 10, 0);
    private final List<EscalationEvent> heard = new ArrayList<>();
    private final EscalationService service =
            new EscalationService(new SlaService(), List.of(heard::add));

    private Incident incident(Priority priority) {
        Incident i = new Incident("INC1", "Payment down", "Application", 3, 3, 3, created);
        i.setPriority(priority);
        i.setStatus(IncidentStatus.ASSIGNED);
        return i;
    }

    @Test
    void noEscalationBeforeResponseTargetPasses() {
        assertTrue(service.evaluate(List.of(incident(Priority.P1)), created.plusMinutes(4)).isEmpty());
    }

    @Test
    void escalatesToTeamLeadAfterFirstPeriod() {
        Incident i = incident(Priority.P1);
        List<EscalationEvent> events = service.evaluate(List.of(i), created.plusMinutes(6));

        assertEquals(1, events.size());
        assertEquals(EscalationLevel.TECHNICIAN, events.get(0).from());
        assertEquals(EscalationLevel.TEAM_LEAD, events.get(0).to());
        assertEquals(EscalationLevel.TEAM_LEAD, i.getEscalationLevel());
    }

    @Test
    void doesNotRepeatTheSameEscalation() {
        Incident i = incident(Priority.P1);
        service.evaluate(List.of(i), created.plusMinutes(6));
        assertTrue(service.evaluate(List.of(i), created.plusMinutes(7)).isEmpty());
    }

    @Test
    void climbsThroughTheWholeChainAndStopsAtTheTop() {
        Incident i = incident(Priority.P1);
        service.evaluate(List.of(i), created.plusMinutes(6));
        service.evaluate(List.of(i), created.plusMinutes(11));
        service.evaluate(List.of(i), created.plusMinutes(16));
        assertEquals(EscalationLevel.IT_MANAGER, i.getEscalationLevel());

        assertTrue(service.evaluate(List.of(i), created.plusHours(3)).isEmpty());
    }

    @Test
    void acknowledgedIncidentIsNotEscalated() {
        Incident i = incident(Priority.P1);
        i.setAcknowledgedAt(created.plusMinutes(2));
        assertTrue(service.evaluate(List.of(i), created.plusMinutes(20)).isEmpty());
    }

    @Test
    void nonCriticalIncidentsAreNotEscalated() {
        assertTrue(service.evaluate(List.of(incident(Priority.P3)), created.plusHours(5)).isEmpty());
    }

    @Test
    void listenersAreNotified() {
        service.evaluate(List.of(incident(Priority.P1)), created.plusMinutes(6));
        assertEquals(1, heard.size());
    }
}