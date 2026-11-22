package com.iitsm.iitsm.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.web.IncidentWorkflowService;

/** Every 5 minutes: recount technician workloads from the incidents and correct any drift. */
@Component
public class WorkloadReconcileJob {
    private final IncidentWorkflowService workflow;

    public WorkloadReconcileJob(IncidentWorkflowService workflow) {
        this.workflow = workflow;
    }

    @Scheduled(fixedRate = 300_000, initialDelay = 20_000)
    public void run() {
        workflow.reconcileWorkload();
    }
}
