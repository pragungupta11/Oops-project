package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;
import com.iitsm.iitsm.security.ActiveAccountFilter;
import com.iitsm.iitsm.security.AppUserDetailsService;
import com.iitsm.iitsm.security.UserAccountService;

@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:edgecasetest",
        "app.max-reports-per-hour=1000", "app.max-active-incidents=1000"})
class EdgeCaseTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentPersistenceService incidents;
    @Autowired TechnicianPersistenceService technicians;
    @Autowired UserAccountService accounts;
    @Autowired AppUserDetailsService login;
    @Autowired ActiveAccountFilter activeAccountFilter;

    @AfterEach
    void everyoneAvailableAgain() {
        for (String id : List.of("tech1", "tech2", "tech3")) accounts.setAvailability(id, true);
        SecurityContextHolder.clearContext();
    }

    private int load(String id) {
        return technicians.findById(id).getActiveIncidents();
    }

    private Incident network(String text) {
        return workflow.report("employee1", text, Category.NETWORK, 2, 2, "Intranet").incident();
    }

    @Test
    void reassigningMovesExactlyOneUnitOfWorkload() {
        Incident inc = network("Conference wifi drops every ten minutes");
        String from = inc.getAssignedTechnician().getId();
        String to = from.equals("tech1") ? "tech3" : "tech1";
        workflow.reconcileWorkload();
        int fromBefore = load(from), toBefore = load(to);

        accounts.setAvailability(from, false);
        workflow.reassignOpenIncidentsOf(from);

        assertEquals(fromBefore - 1, load(from));
        assertEquals(toBefore + 1, load(to));
        assertEquals(0, workflow.reconcileWorkload());   // counters already match reality
    }

    @Test
    void driftedWorkloadCounterIsCorrected() {
        Technician t = technicians.findById("tech2");
        technicians.save(new Technician(t.getId(), t.getName(), t.getSkills(), 42, t.isAvailable()));   // corrupt it

        assertTrue(workflow.reconcileWorkload() >= 1);
        assertNotEquals(42, load("tech2"));
    }

    @Test
    void reopenGoesToAnAvailableTechnicianWhenTheOriginalIsAway() {
        Incident inc = network("Office printer network share unreachable");
        String original = inc.getAssignedTechnician().getId();
        workflow.acknowledge(inc.getId(), original);
        workflow.start(inc.getId(), original);
        workflow.resolve(inc.getId(), original, "Restarted the print server");

        accounts.setAvailability(original, false);
        workflow.reopen(inc.getId(), "employee1", "Still cannot print");

        Incident after = incidents.findById(inc.getId());
        assertNotEquals(original, after.getAssignedTechnician().getId());
        assertEquals(IncidentStatus.ASSIGNED, after.getStatus());
    }

    @Test
    void priorityOfALinkedReportCannotBeChanged() {
        network("Lobby kiosk keeps rebooting");
        Incident second = network("Lobby kiosk keeps rebooting again");
        workflow.linkToSuggestedParent(second.getId(), second.getAssignedTechnician().getId());

        assertThrows(InvalidIncidentDataException.class,
                () -> workflow.changePriority(second.getId(), Priority.P1, "trying anyway"));
    }

    @Test
    void usernamesAreCaseInsensitiveAtLogin() {
        assertEquals("employee1", login.loadUserByUsername("Employee1").getUsername());
        assertEquals("employee1", login.loadUserByUsername("  EMPLOYEE1 ").getUsername());
    }

    @Test
    void deactivatedUserIsLoggedOutOnTheirNextClick() throws Exception {
        accounts.registerEmployee("Temp Worker", "temp.worker", "temp.worker@company.com", "secret123", "secret123");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "temp.worker", null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));

        accounts.setEnabled("temp.worker", false, "admin");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/employee/incidents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        activeAccountFilter.doFilter(request, response, new MockFilterChain());

        assertTrue(response.getRedirectedUrl().contains("/login?deactivated"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertFalse(activeAccountFilter.isStillActive("temp.worker"));
    }

    @Test
    void linkedReportGoesBackToAssignedWithItsParent() {
        Incident parent = network("Meeting room projector shows no input");
        String tech = parent.getAssignedTechnician().getId();
        workflow.acknowledge(parent.getId(), tech);
        workflow.start(parent.getId(), tech);
        Incident child = network("Meeting room projector shows no input again");
        workflow.linkToSuggestedParent(child.getId(), child.getAssignedTechnician().getId());
        assertEquals(IncidentStatus.IN_PROGRESS, incidents.findById(child.getId()).getStatus());

        accounts.setAvailability(tech, false);
        workflow.reassignOpenIncidentsOf(tech);       // parent goes back to ASSIGNED for the new technician

        assertEquals(IncidentStatus.ASSIGNED, incidents.findById(parent.getId()).getStatus());
        assertEquals(IncidentStatus.ASSIGNED, incidents.findById(child.getId()).getStatus());
    }
}
