package com.iitsm.iitsm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;

class IncidentLinkingTest {
    private final Technician arjun = new Technician("tech3", "Arjun", Set.of("Network"), 1, true);
    private final Technician ravi = new Technician("tech1", "Ravi", Set.of("Network"), 1, true);

    private Incident incident(String id, Technician tech, IncidentStatus status) {
        Incident i = new Incident(id, "Payment app down", "Network", 3, 3, 3,
                LocalDateTime.of(2026, 11, 20, 9, 0));
        i.setPriority(Priority.P1);
        i.setAssignedTechnician(tech);
        i.setStatus(status);
        return i;
    }

    @Test
    void linkingMovesTheReportToTheParentsTechnicianAndClearsTheHint() {
        Incident parent = incident("INC1", arjun, IncidentStatus.IN_PROGRESS);
        Incident report = incident("INC2", ravi, IncidentStatus.ASSIGNED);
        report.suggestDuplicateOf("INC1", 55);

        report.linkTo(parent);

        assertEquals("INC1", report.getParentId());
        assertSame(arjun, report.getAssignedTechnician());
        assertFalse(report.hasDuplicateSuggestion());
    }

    @Test
    void cannotLinkToItself() {
        Incident i = incident("INC1", arjun, IncidentStatus.ASSIGNED);
        assertThrows(InvalidIncidentDataException.class, () -> i.linkTo(i));
    }

    @Test
    void cannotLinkToAReportThatIsItselfLinked() {
        Incident root = incident("INC1", arjun, IncidentStatus.ASSIGNED);
        Incident middle = incident("INC2", arjun, IncidentStatus.ASSIGNED);
        middle.linkTo(root);
        Incident report = incident("INC3", ravi, IncidentStatus.ASSIGNED);

        assertThrows(InvalidIncidentDataException.class, () -> report.linkTo(middle));
    }

    @Test
    void cannotLinkToAFinishedIncident() {
        Incident closed = incident("INC1", arjun, IncidentStatus.CLOSED);
        Incident report = incident("INC2", ravi, IncidentStatus.ASSIGNED);
        assertThrows(InvalidIncidentDataException.class, () -> report.linkTo(closed));
    }

    @Test
    void unlinkMakesItIndependentAgain() {
        Incident parent = incident("INC1", arjun, IncidentStatus.ASSIGNED);
        Incident report = incident("INC2", ravi, IncidentStatus.ASSIGNED);
        report.linkTo(parent);

        report.unlink();

        assertNull(report.getParentId());
        assertFalse(report.isLinked());
    }

    @Test
    void unlinkingAnUnlinkedIncidentIsRejected() {
        Incident i = incident("INC1", arjun, IncidentStatus.ASSIGNED);
        assertThrows(InvalidIncidentDataException.class, i::unlink);
    }

    @Test
    void aLinkedReportCannotBeReroutedOnItsOwn() {
        Incident parent = incident("INC1", arjun, IncidentStatus.ASSIGNED);
        Incident report = incident("INC2", ravi, IncidentStatus.ASSIGNED);
        report.linkTo(parent);
        assertThrows(InvalidIncidentDataException.class,
                () -> report.requestReassignment("Application", "wrong"));
    }

    @Test
    void dismissingTheHintRemovesIt() {
        Incident i = incident("INC2", ravi, IncidentStatus.ASSIGNED);
        i.suggestDuplicateOf("INC1", 50);
        assertTrue(i.hasDuplicateSuggestion());

        i.dismissDuplicateSuggestion();

        assertFalse(i.hasDuplicateSuggestion());
    }
}
