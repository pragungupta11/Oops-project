package com.iitsm.iitsm.service;
import org.springframework.stereotype.Service ;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import com.iitsm.iitsm.exception.MissingSlaConfigurationException;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaPolicy;
import com.iitsm.iitsm.model.SlaStatus;

@Service

public class SlaService {
    private static final double RISK_THRESHOLD = 0.7;

    private final Map<Priority, SlaPolicy> policies = new EnumMap<>(Priority.class);

    public SlaService() {
        policies.put(Priority.P1, new SlaPolicy(Duration.ofMinutes(5), Duration.ofMinutes(30)));
        policies.put(Priority.P2, new SlaPolicy(Duration.ofMinutes(15), Duration.ofHours(2)));
        policies.put(Priority.P3, new SlaPolicy(Duration.ofHours(1), Duration.ofHours(8)));
        policies.put(Priority.P4, new SlaPolicy(Duration.ofHours(4), Duration.ofHours(24)));
    }

    /** Lets an admin change a policy later. */
    public void setPolicy(Priority priority, SlaPolicy policy) {
        policies.put(priority, policy);
    }

    public SlaPolicy getPolicy(Priority priority) {
        SlaPolicy policy = policies.get(priority);
        if (policy == null) {
            throw new MissingSlaConfigurationException("No SLA configured for " + priority);
        }
        return policy;
    }

    public LocalDateTime responseDeadline(LocalDateTime created, Priority priority) {
        return created.plus(getPolicy(priority).responseTarget());
    }

    public LocalDateTime resolutionDeadline(LocalDateTime created, Priority priority) {
        return created.plus(getPolicy(priority).resolutionTarget());
    }

    /** OK, AT_RISK (70% of time used) or VIOLATED (100% used), against the resolution target. */
    public SlaStatus checkResolution(LocalDateTime created, Priority priority, LocalDateTime now) {
        Duration target = getPolicy(priority).resolutionTarget();
        return classify(Duration.between(created, now), target);
    }

    /** Response clock stops when the technician acknowledges. Until then it keeps running. */
    public SlaStatus checkResponse(LocalDateTime created, Priority priority,
                                   LocalDateTime acknowledgedAt, LocalDateTime now) {
        Duration target = getPolicy(priority).responseTarget();
        LocalDateTime clockStop = (acknowledgedAt != null) ? acknowledgedAt : now;
        return classify(Duration.between(created, clockStop), target);
    }

    private SlaStatus classify(Duration elapsed, Duration target) {
        double used = (double) elapsed.toSeconds() / target.toSeconds();
        if (used >= 1.0) return SlaStatus.VIOLATED;
        if (used >= RISK_THRESHOLD) return SlaStatus.AT_RISK;
        return SlaStatus.OK;
    }
}