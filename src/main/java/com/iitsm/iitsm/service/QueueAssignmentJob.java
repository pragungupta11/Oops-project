package com.iitsm.iitsm.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.web.IncidentWorkflowService;

/** Every minute: give queued (unassigned) incidents to technicians who have become free. */
@Component
public class QueueAssignmentJob {
    private final IncidentWorkflowService workflow;

    public QueueAssignmentJob(IncidentWorkflowService workflow) {
        this.workflow = workflow;
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 30_000)
    public void run() {
        workflow.assignQueuedIncidents();
    }
}
