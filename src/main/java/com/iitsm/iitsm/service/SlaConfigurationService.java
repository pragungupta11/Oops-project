package com.iitsm.iitsm.service;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.iitsm.exception.InvalidSlaConfigurationException;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaPolicy;
import com.iitsm.iitsm.persistence.service.SlaPolicyPersistenceService;

/**
 * Admin-configurable SLA policies. The database is the source of truth; SlaService keeps a
 * fast in-memory copy that every deadline calculation uses.
 */
@Service
public class SlaConfigurationService {
    /** One week: anything longer is almost certainly a typo. */
    static final long MAX_MINUTES = 7 * 24 * 60;

    private final SlaService slaService;
    private final SlaPolicyPersistenceService store;

    public SlaConfigurationService(SlaService slaService, SlaPolicyPersistenceService store) {
        this.slaService = slaService;
        this.store = store;
    }

    /** On startup (after the default policies were seeded), load what is in the database. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadFromDatabase() {
        store.findAll().forEach(slaService::setPolicy);
    }

    /** The policies currently in force, P1 to P4. */
    public Map<Priority, SlaPolicy> current() {
        Map<Priority, SlaPolicy> result = new EnumMap<>(Priority.class);
        for (Priority p : Priority.values()) {
            result.put(p, slaService.getPolicy(p));
        }
        return result;
    }

    /** Saves a new policy (audited by the persistence layer) and applies it immediately. */
    @Transactional
    public SlaPolicy update(Priority priority, long responseMinutes, long resolutionMinutes) {
        if (responseMinutes <= 0 || resolutionMinutes <= 0) {
            throw new InvalidSlaConfigurationException("Targets must be at least 1 minute");
        }
        if (responseMinutes > MAX_MINUTES || resolutionMinutes > MAX_MINUTES) {
            throw new InvalidSlaConfigurationException("Targets cannot be longer than one week");
        }
        if (responseMinutes > resolutionMinutes) {
            throw new InvalidSlaConfigurationException(
                    "The response target cannot be longer than the resolution target");
        }
        SlaPolicy policy = new SlaPolicy(Duration.ofMinutes(responseMinutes), Duration.ofMinutes(resolutionMinutes));
        store.save(priority, policy);
        slaService.setPolicy(priority, policy);
        return policy;
    }
}
