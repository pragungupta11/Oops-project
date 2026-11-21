package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.CommentKind;
import com.iitsm.iitsm.model.Incident;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:conversationtest")
class IncidentConversationServiceTest {

    @Autowired IncidentWorkflowService workflow;
    @Autowired IncidentConversationService conversation;

    private Incident report(String description) {
        return workflow.report("employee1", description, Category.APPLICATION, 2, 2, "Payment Gateway").incident();
    }

    @Test
    void informationRequestIsAnsweredByTheEmployee() {
        Incident inc = report("Invoice screen shows error 500");
        String tech = inc.getAssignedTechnician().getId();

        conversation.addTechnicianEntry(inc.getId(), tech, CommentKind.INFO_REQUEST, "Which browser are you using?");
        assertTrue(conversation.isAwaitingInfo(inc.getId()));

        conversation.addEmployeeMessage(inc.getId(), "employee1", "Chrome on Windows");
        assertFalse(conversation.isAwaitingInfo(inc.getId()));
        assertEquals(2, conversation.thread(inc.getId(), false).size());
    }

    @Test
    void internalNotesAreHiddenFromTheEmployee() {
        Incident inc = report("Expense form freezes on submit");
        String tech = inc.getAssignedTechnician().getId();

        conversation.addTechnicianEntry(inc.getId(), tech, CommentKind.NOTE, "Suspect the new release");

        assertEquals(1, conversation.thread(inc.getId(), true).size());
        assertEquals(0, conversation.thread(inc.getId(), false).size());
    }

    @Test
    void othersCannotWriteOnSomeoneElsesIncident() {
        Incident inc = report("Leave portal button does nothing");
        assertThrows(AccessDeniedException.class,
                () -> conversation.addEmployeeMessage(inc.getId(), "employee2", "Me too"));
        assertThrows(AccessDeniedException.class,
                () -> conversation.addTechnicianEntry(inc.getId(), "not-my-tech", CommentKind.NOTE, "x"));
    }

    @Test
    void feedbackOnlyAfterResolutionAndOnlyOnce() {
        Incident inc = report("Timesheet page crashes on save");
        String tech = inc.getAssignedTechnician().getId();
        assertThrows(InvalidIncidentDataException.class,
                () -> conversation.giveFeedback(inc.getId(), "employee1", 5, "too early"));

        workflow.acknowledge(inc.getId(), tech);
        workflow.start(inc.getId(), tech);
        workflow.resolve(inc.getId(), tech, "Rolled back the release");

        assertThrows(InvalidIncidentDataException.class,
                () -> conversation.giveFeedback(inc.getId(), "employee1", 6, "out of range"));
        conversation.giveFeedback(inc.getId(), "employee1", 4, "Quick fix");
        assertEquals(4, conversation.feedbackFor(inc.getId()).orElseThrow().rating());
        assertThrows(InvalidIncidentDataException.class,
                () -> conversation.giveFeedback(inc.getId(), "employee1", 5, "again"));
        assertTrue(conversation.averageRating().isPresent());
    }

    @Test
    void emptyMessagesAreRejected() {
        Incident inc = report("Payslip download link broken");
        assertThrows(InvalidIncidentDataException.class,
                () -> conversation.addEmployeeMessage(inc.getId(), "employee1", "   "));
    }

    @Test
    void afterResolutionNotesAreAllowedButClosedIsReadOnly() {
        Incident inc = report("Travel booking page times out");
        String tech = inc.getAssignedTechnician().getId();
        workflow.acknowledge(inc.getId(), tech);
        workflow.start(inc.getId(), tech);
        workflow.resolve(inc.getId(), tech, "Increased the gateway timeout");

        conversation.addTechnicianEntry(inc.getId(), tech, CommentKind.NOTE, "Root cause: gateway timeout too low");
        assertThrows(InvalidIncidentDataException.class,
                () -> conversation.addTechnicianEntry(inc.getId(), tech, CommentKind.INFO_REQUEST, "Still broken?"));

        workflow.verify(inc.getId());
        workflow.close(inc.getId());
        assertThrows(InvalidIncidentDataException.class,
                () -> conversation.addTechnicianEntry(inc.getId(), tech, CommentKind.NOTE, "Too late"));
    }
}
