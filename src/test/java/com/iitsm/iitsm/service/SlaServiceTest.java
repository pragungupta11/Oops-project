package com.iitsm.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.exception.MissingSlaConfigurationException;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaStatus;

class SlaServiceTest {
    private final SlaService sla = new SlaService();
    private final LocalDateTime created = LocalDateTime.of(2026, 11, 7, 10, 0);

    @Test
    void p1ResolutionDeadlineIs30MinutesAfterCreation() {
        assertEquals(created.plusMinutes(30), sla.resolutionDeadline(created, Priority.P1));
    }

    @Test
    void statusIsOkEarlyOn() {
        assertEquals(SlaStatus.OK, sla.checkResolution(created, Priority.P1, created.plusMinutes(10)));
    }

    @Test
    void statusIsAtRiskAfter70Percent() {
        assertEquals(SlaStatus.AT_RISK, sla.checkResolution(created, Priority.P1, created.plusMinutes(25)));
    }

    @Test
    void statusIsViolatedAfterDeadline() {
        assertEquals(SlaStatus.VIOLATED, sla.checkResolution(created, Priority.P1, created.plusMinutes(31)));
    }

    @Test
    void missingPolicyThrows() {
        SlaService empty = new SlaService();
        empty.setPolicy(Priority.P3, null);
        assertThrows(MissingSlaConfigurationException.class, () -> empty.getPolicy(Priority.P3));
    }
    @Test
void statusBecomesAtRiskAtExactly70Percent() {
    LocalDateTime now = created.plusMinutes(21);

    assertEquals(
        SlaStatus.AT_RISK,
        sla.checkResolution(created, Priority.P1, now)
    );
}
}