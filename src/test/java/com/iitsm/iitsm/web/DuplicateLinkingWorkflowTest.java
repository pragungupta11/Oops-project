package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;

/** Each test uses its own vocabulary so that incidents from other tests never look similar. */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:duplicatelinkingtest")
class DuplicateLinkingWorkflowTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired TechnicianPersistenceService technicians;

    private Incident report(String user, String description) {
        return workflow.report(user, description, Category.NETWORK, 2, 2, "Intranet").incident();
    }

    private int load(String technicianId) {
        return technicians.findById(technicianId).getActiveIncidents();
    }

    @Test
    void suspectedDuplicateGoesToTheSameTechnicianWithAHint() {
        Incident first = report("employee1", "Shared printer jams on floor three");
        String tech = first.getAssignedTechnician().getId();
        int loadBefore = load(tech);

        Incident second = report("employee2", "Shared printer jammed on floor three");

        assertEquals(tech, second.getAssignedTechnician().getId());   // not spread to a second technician
        assertEquals(first.getId(), second.getSuggestedParentId());
        assertFalse(second.isLinked());                                  // a hint only, never automatic
        assertEquals(loadBefore + 1, load(tech));                        // unconfirmed: still real work
    }

    @Test
    void linkedReportFreesTheSlotAndIsResolvedWithItsParent() {
        Incident first = report("employee1", "Warehouse scanner barcode unreadable");
        Incident second = report("employee2", "Warehouse scanner barcodes unreadable");
        String tech = second.getAssignedTechnician().getId();
        int loadBefore = load(tech);

        workflow.linkToSuggestedParent(second.getId(), tech);
        assertEquals(loadBefore - 1, load(tech));                        // same work, counted once

        String parentTech = incidents.findById(first.getId()).getAssignedTechnician().getId();
        workflow.acknowledge(first.getId(), parentTech);
        workflow.start(first.getId(), parentTech);
        workflow.resolve(first.getId(), parentTech, "Replaced the scanner cable");

        Incident child = incidents.findById(second.getId());
        assertEquals(IncidentStatus.RESOLVED, child.getStatus());
        assertEquals("Replaced the scanner cable", child.getResolution());
        assertEquals(first.getId(), child.getParentId());
    }

    @Test
    void linkedReportCannotBeWorkedOnSeparately() {
        report("employee1", "Kitchen television remote missing");
        Incident second = report("employee2", "Kitchen television remote missing again");
        String tech = second.getAssignedTechnician().getId();
        workflow.linkToSuggestedParent(second.getId(), tech);

        String parentTech = incidents.findById(second.getId()).getAssignedTechnician().getId();
        assertThrows(InvalidIncidentDataException.class,
                () -> workflow.acknowledge(second.getId(), parentTech));
    }

    @Test
    void notTheSameKeepsItSeparate() {
        report("employee1", "Conference room projector flickers");
        Incident second = report("employee2", "Conference room projector flickering");
        String tech = second.getAssignedTechnician().getId();
        int loadBefore = load(tech);

        workflow.dismissDuplicateSuggestion(second.getId(), tech);

        Incident after = incidents.findById(second.getId());
        assertFalse(after.hasDuplicateSuggestion());
        assertFalse(after.isLinked());
        assertEquals(loadBefore, load(tech));
    }

    @Test
    void wrongLinkCanBeUndoneByTheAdmin() {
        report("employee1", "Parking gate sensor stuck");
        Incident second = report("employee2", "Parking gate sensors stuck");
        workflow.linkToSuggestedParent(second.getId(), second.getAssignedTechnician().getId());

        Incident unlinked = workflow.unlink(second.getId(), null);

        assertNull(unlinked.getParentId());
        assertEquals(IncidentStatus.ASSIGNED, unlinked.getStatus());
        assertNull(unlinked.getAcknowledgedAt());
        assertTrue(unlinked.getAssignedTechnician() != null);
    }
}
