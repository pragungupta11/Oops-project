package com.iitsm.iitsm.state;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;

class IncidentStateTest {
    private Incident incident() {
        return new Incident("I1", "d", "Network", 1, 1, 1, LocalDateTime.of(2026, 11, 12, 10, 0));
    }

    @Test void newIncidentStartsInNew() {
        assertEquals(IncidentStatus.NEW, incident().getStatus());
    }

    @Test void followsTheFullHappyPath() {
        Incident i = incident();
        for (IncidentStatus s : new IncidentStatus[]{
                IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED,
                IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED, IncidentStatus.VERIFIED,
                IncidentStatus.CLOSED}) {
            i.transitionTo(s);
        }
        assertEquals(IncidentStatus.CLOSED, i.getStatus());
    }

    @Test void cannotSkipFromNewToClosed() {
        assertThrows(InvalidStateTransitionException.class,
                () -> incident().transitionTo(IncidentStatus.CLOSED));
    }

    @Test void failedTransitionLeavesStatusUnchanged() {
        Incident i = incident();
        assertThrows(InvalidStateTransitionException.class,
                () -> i.transitionTo(IncidentStatus.RESOLVED));
        assertEquals(IncidentStatus.NEW, i.getStatus());
    }

    @Test void resolvedIncidentCanBeReopened() {
        Incident i = incident();
        i.setStatus(IncidentStatus.RESOLVED);
        i.transitionTo(IncidentStatus.IN_PROGRESS);
        assertEquals(IncidentStatus.IN_PROGRESS, i.getStatus());
    }

    @Test void closedIsTerminal() {
        Incident i = incident();
        i.setStatus(IncidentStatus.CLOSED);
        assertThrows(InvalidStateTransitionException.class,
                () -> i.transitionTo(IncidentStatus.NEW));
    }
}