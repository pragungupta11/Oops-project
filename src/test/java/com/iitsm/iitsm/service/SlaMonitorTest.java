package com.iitsm.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.model.AlertType;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaAlert;
import com.iitsm.iitsm.model.SlaStatus;

class SlaMonitorTest {
    private final LocalDateTime created = LocalDateTime.of(2026, 11, 12, 10, 0);
    private final List<SlaAlert> heard = new ArrayList<>();
    private final SlaMonitor monitor = new SlaMonitor(new SlaService(), List.of(heard::add));

    private Incident p1Incident(boolean acknowledged) {
        Incident i = new Incident("INC1", "Payment down", "Application", 3, 3, 3, created);
        i.setPriority(Priority.P1);
        i.setStatus(IncidentStatus.ASSIGNED);
        if (acknowledged) i.setAcknowledgedAt(created.plusMinutes(1));
        return i;
    }

    @Test
    void noAlertWhileWellWithinTarget() {
        List<SlaAlert> raised = monitor.scan(List.of(p1Incident(true)), created.plusMinutes(10));
        assertTrue(raised.isEmpty());
    }

    @Test
    void warnsWhenResolutionTimeIsRunningOut() {
        List<SlaAlert> raised = monitor.scan(List.of(p1Incident(true)), created.plusMinutes(25));
        assertEquals(1, raised.size());
        assertEquals(AlertType.RESOLUTION, raised.get(0).type());
        assertEquals(SlaStatus.AT_RISK, raised.get(0).status());
    }

    @Test
    void doesNotRepeatTheSameAlert() {
        Incident i = p1Incident(true);
        monitor.scan(List.of(i), created.plusMinutes(25));
        List<SlaAlert> second = monitor.scan(List.of(i), created.plusMinutes(26));
        assertTrue(second.isEmpty());
    }

    @Test
    void escalatesToViolatedAfterDeadline() {
        Incident i = p1Incident(true);
        monitor.scan(List.of(i), created.plusMinutes(25));
        List<SlaAlert> raised = monitor.scan(List.of(i), created.plusMinutes(31));
        assertEquals(SlaStatus.VIOLATED, raised.get(0).status());
    }

    @Test
    void unacknowledgedIncidentViolatesResponseTarget() {
        List<SlaAlert> raised = monitor.scan(List.of(p1Incident(false)), created.plusMinutes(6));
        assertEquals(AlertType.RESPONSE, raised.get(0).type());
        assertEquals(SlaStatus.VIOLATED, raised.get(0).status());
    }

    @Test
    void closedIncidentsAreIgnored() {
        Incident i = p1Incident(true);
        i.setStatus(IncidentStatus.CLOSED);
        assertTrue(monitor.scan(List.of(i), created.plusHours(5)).isEmpty());
    }

    @Test
    void listenersAreNotified() {
        monitor.scan(List.of(p1Incident(true)), created.plusMinutes(25));
        assertEquals(1, heard.size());
    }
}