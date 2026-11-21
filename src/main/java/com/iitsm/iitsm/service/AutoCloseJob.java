package com.iitsm.iitsm.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.web.IncidentWorkflowService;

/** Every 5 minutes: close resolved incidents that waited longer than app.auto-close-after for the employee. */
@Component
public class AutoCloseJob {
    private static final Logger log = LoggerFactory.getLogger(AutoCloseJob.class);

    private final IncidentWorkflowService workflow;
    private final Duration autoCloseAfter;

    public AutoCloseJob(IncidentWorkflowService workflow,
                        @Value("${app.auto-close-after:3d}") Duration autoCloseAfter) {
        this.workflow = workflow;
        this.autoCloseAfter = autoCloseAfter;
    }

    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void run() {
        int closed = workflow.autoCloseResolvedBefore(LocalDateTime.now().minus(autoCloseAfter));
        if (closed > 0) {
            log.info("Auto-closed {} resolved incident(s) older than {}", closed, autoCloseAfter);
        }
    }

    public Duration autoCloseAfter() {
        return autoCloseAfter;
    }
}
