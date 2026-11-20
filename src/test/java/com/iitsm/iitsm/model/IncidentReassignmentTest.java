package com.iitsm.iitsm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;

class IncidentReassignmentTest {
    private final Technician arjun = new Technician("tech3", "Arjun", Set.of("Network"), 1, true);
    private final Technician meera = new Technician("tech2", "Meera", Set.of("Application"), 0, true);

    private Incident assignedIncident(IncidentStatus status) {
        Incident i = new Incident("INC1", "Payment app down", "Network", 3, 3, 3,
                LocalDateTime.of(2026, 11, 20, 9, 0));
        i.setPriority(Priority.P1);
        i.setAssignedTechnician(arjun);
        i.setStatus(status);
        return i;
    }

    @Test
    void technicianCanRequestReroutingOfAnActiveIncident() {
        Incident i = assignedIncident(IncidentStatus.ACKNOWLEDGED);
        i.requestReassignment("Application", "This is the payment app, not the network");

        assertTrue(i.hasPendingReassignment());
        assertEquals("Network", i.getCategory());          // unchanged until the admin approves
        assertSame(arjun, i.getAssignedTechnician());
    }

    @Test
    void approvalCorrectsCategoryAndSendsItBackToAssigned() {
        Incident i = assignedIncident(IncidentStatus.IN_PROGRESS);
        i.requestReassignment("Application", "Wrong category");

        i.approveReassignment(meera);

        assertEquals("Application", i.getCategory());
        assertSame(meera, i.getAssignedTechnician());
        assertEquals(IncidentStatus.ASSIGNED, i.getStatus());
        assertFalse(i.hasPendingReassignment());
    }

    @Test
    void rejectionLeavesEverythingAsItWas() {
        Incident i = assignedIncident(IncidentStatus.ASSIGNED);
        i.requestReassignment("Application", "Wrong category");

        i.rejectReassignment();

        assertEquals("Network", i.getCategory());
        assertSame(arjun, i.getAssignedTechnician());
        assertFalse(i.hasPendingReassignment());
    }

    @Test
    void sameCategoryIsRejected() {
        Incident i = assignedIncident(IncidentStatus.ASSIGNED);
        assertThrows(InvalidIncidentDataException.class,
                () -> i.requestReassignment("Network", "No reason"));
    }

    @Test
    void reasonIsRequired() {
        Incident i = assignedIncident(IncidentStatus.ASSIGNED);
        assertThrows(InvalidIncidentDataException.class,
                () -> i.requestReassignment("Application", "  "));
    }

    @Test
    void resolvedIncidentCannotBeRerouted() {
        Incident i = assignedIncident(IncidentStatus.RESOLVED);
        assertThrows(InvalidIncidentDataException.class,
                () -> i.requestReassignment("Application", "Too late"));
    }

    @Test
    void onlyOnePendingRequestAtATime() {
        Incident i = assignedIncident(IncidentStatus.ASSIGNED);
        i.requestReassignment("Application", "Wrong category");
        assertThrows(InvalidIncidentDataException.class,
                () -> i.requestReassignment("Database", "Changed my mind"));
    }
}
