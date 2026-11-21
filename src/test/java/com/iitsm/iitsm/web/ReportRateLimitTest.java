package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.Category;

/** Guardrail: one account cannot flood the queue with reports. */
@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:ratelimittest", "app.max-reports-per-hour=2"})
class ReportRateLimitTest {

    @Autowired IncidentWorkflowService workflow;

    @Test
    void thirdReportWithinAnHourIsRefused() {
        workflow.report("employee1", "Scanner jams on page two", Category.NETWORK, 1, 1, "Intranet");
        workflow.report("employee1", "Projector has no signal", Category.NETWORK, 1, 1, "Intranet");

        assertThrows(InvalidIncidentDataException.class, () ->
                workflow.report("employee1", "Third report in one hour", Category.NETWORK, 1, 1, "Intranet"));

        workflow.report("employee2", "Someone else can still report", Category.NETWORK, 1, 1, "Intranet");
    }
}
