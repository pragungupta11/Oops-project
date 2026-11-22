package com.iitsm.iitsm.service;
import org.springframework.stereotype.Service ;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.iitsm.iitsm.exception.MissingSlaConfigurationException;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaPolicy;
import com.iitsm.iitsm.model.SlaStatus;

@Service

public class SlaService {
    private static final double RISK_THRESHOLD = 0.7;

    // Thread-safe: the admin may change a policy while the SLA job and web requests read them.
    // Starts with the PDF's defaults; SlaConfigurationService replaces them with the database values.
    private final Map<Priority, SlaPolicy> policies = new ConcurrentHashMap<>();

    public SlaService() {
        policies.put(Priority.P1, new SlaPolicy(Duration.ofMinutes(5), Duration.ofMinutes(30)));
        policies.put(Priority.P2, new SlaPolicy(Duration.ofMinutes(15), Duration.ofHours(2)));
        policies.put(Priority.P3, new SlaPolicy(Duration.ofHours(1), Duration.ofHours(8)));
        policies.put(Priority.P4, new SlaPolicy(Duration.ofHours(4), Duration.ofHours(24)));
    }

    /** Replaces one policy (null removes it, which makes that priority "not configured"). */
    public void setPolicy(Priority priority, SlaPolicy policy) {
        if (policy == null) {
            policies.remove(priority);
        } else {
            policies.put(priority, policy);
        }
    }

    public SlaPolicy getPolicy(Priority priority) {
        SlaPolicy policy = policies.get(priority);
        if (policy == null) {
            throw new MissingSlaConfigurationException("No SLA configured for " + priority);
        }
        return policy;
    }

    // The versions without a service use the default policy of the priority (service = null).

    public LocalDateTime responseDeadline(LocalDateTime created, Priority priority) {
        return responseDeadline(created, priority, null);
    }

    public LocalDateTime resolutionDeadline(LocalDateTime created, Priority priority) {
        return resolutionDeadline(created, priority, null);
    }

    /** OK, AT_RISK (70% of time used) or VIOLATED (100% used), against the resolution target. */
    public SlaStatus checkResolution(LocalDateTime created, Priority priority, LocalDateTime now) {
        return checkResolution(created, priority, null, now);
    }

    /** Response clock stops when the technician acknowledges. Until then it keeps running. */
    public SlaStatus checkResponse(LocalDateTime created, Priority priority,
                                   LocalDateTime acknowledgedAt, LocalDateTime now) {
        return checkResponse(created, priority, null, acknowledgedAt, now);
    }

    // ------------------------------------------------------------------ per-service SLAs

    /** Optional stricter/looser targets for one service and priority, e.g. Payment Gateway P1. */
    private final Map<String, Map<Priority, SlaPolicy>> serviceOverrides = new ConcurrentHashMap<>();

    /** Sets (or with null removes) the override of one service + priority. */
    public void setServicePolicy(String service, Priority priority, SlaPolicy policy) {
        if (service == null) return;
        if (policy == null) {
            Map<Priority, SlaPolicy> m = serviceOverrides.get(service);
            if (m != null) m.remove(priority);
        } else {
            serviceOverrides.computeIfAbsent(service, k -> new ConcurrentHashMap<>()).put(priority, policy);
        }
    }

    /** All overrides, for the admin page: service -> priority -> policy. */
    public Map<String, Map<Priority, SlaPolicy>> servicePolicies() {
        // a read-only copy: callers cannot change the live overrides behind the service's back
        Map<String, Map<Priority, SlaPolicy>> copy = new java.util.TreeMap<>();
        serviceOverrides.forEach((service, m) -> copy.put(service, Map.copyOf(m)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    /** The policy that applies to an incident: the service override if there is one, else the default. */
    public SlaPolicy policyFor(Priority priority, String service) {
        if (service != null) {
            Map<Priority, SlaPolicy> m = serviceOverrides.get(service);
            if (m != null && m.get(priority) != null) return m.get(priority);
        }
        return getPolicy(priority);
    }

    public LocalDateTime responseDeadline(LocalDateTime created, Priority priority, String service) {
        return created.plus(policyFor(priority, service).responseTarget());
    }

    public LocalDateTime resolutionDeadline(LocalDateTime created, Priority priority, String service) {
        return created.plus(policyFor(priority, service).resolutionTarget());
    }

    public SlaStatus checkResolution(LocalDateTime created, Priority priority, String service, LocalDateTime now) {
        return classify(Duration.between(created, now), policyFor(priority, service).resolutionTarget());
    }

    public SlaStatus checkResponse(LocalDateTime created, Priority priority, String service,
                                   LocalDateTime acknowledgedAt, LocalDateTime now) {
        LocalDateTime clockStop = (acknowledgedAt != null) ? acknowledgedAt : now;
        return classify(Duration.between(created, clockStop), policyFor(priority, service).responseTarget());
    }

    private SlaStatus classify(Duration elapsed, Duration target) {
        double used = (double) elapsed.toSeconds() / target.toSeconds();
        if (used >= 1.0) return SlaStatus.VIOLATED;
        if (used >= RISK_THRESHOLD) return SlaStatus.AT_RISK;
        return SlaStatus.OK;
    }
}