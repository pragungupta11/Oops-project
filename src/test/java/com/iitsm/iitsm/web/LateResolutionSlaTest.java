package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.service.SlaService;

/** A resolved incident keeps its SLA result: MET when resolved in time, VIOLATED when late. */
class LateResolutionSlaTest {
    private final SlaService sla = new SlaService();
    private final LocalDateTime created = LocalDateTime.of(2026, 11, 21, 10, 0);

    private Incident resolvedAfter(int minutes) {
        Incident i = new Incident("INC1", "Payment app down", "Application", 3, 3, 3, created);
        i.setPriority(Priority.P1);                 // resolution target 30 min
        i.setStatus(IncidentStatus.RESOLVED);
        i.setResolvedAt(created.plusMinutes(minutes));
        return i;
    }

    @Test
    void resolvedInTimeIsMet() {
        assertEquals("MET", IncidentView.of(resolvedAfter(20), sla).sla());
    }

    @Test
    void resolvedLateCountsAsViolated() {
        assertEquals("VIOLATED", IncidentView.of(resolvedAfter(45), sla).sla());
    }
}
