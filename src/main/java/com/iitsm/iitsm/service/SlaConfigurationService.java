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
import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.persistence.entity.ServiceSlaPolicyEntity;
import com.iitsm.iitsm.repository.ServiceSlaPolicyRepository;
import java.util.Comparator;
import java.util.List;

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
    private final ServiceSlaPolicyRepository serviceStore;
    private final AuditService audit;

    public SlaConfigurationService(SlaService slaService, SlaPolicyPersistenceService store,
                                   ServiceSlaPolicyRepository serviceStore,
                                   AuditService audit) {
        this.slaService = slaService;
        this.store = store;
        this.serviceStore = serviceStore;
        this.audit = audit;
    }

    /** On startup (after the default policies were seeded), load what is in the database. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadFromDatabase() {
        store.findAll().forEach(slaService::setPolicy);
        serviceStore.findAll().forEach(e -> slaService.setServicePolicy(e.getServiceName(), e.getPriority(),
                new SlaPolicy(Duration.ofMinutes(e.getResponseMinutes()), Duration.ofMinutes(e.getResolutionMinutes()))));
    }

    /** All service-specific SLAs (for the admin page). */
    public List<ServiceSlaPolicyEntity> serviceOverrides() {
        return serviceStore.findAll().stream()
                .sorted(Comparator.comparing(ServiceSlaPolicyEntity::getServiceName)
                        .thenComparing(ServiceSlaPolicyEntity::getPriority))
                .toList();
    }

    /** "Different IT services may have different SLAs": sets the SLA of one service + priority. */
    @Transactional
    public SlaPolicy setServiceOverride(String service, Priority priority, long responseMinutes, long resolutionMinutes) {
        if (service == null || service.isBlank()) throw new InvalidSlaConfigurationException("Choose a service");
        validate(responseMinutes, resolutionMinutes);
        var entity = serviceStore.findByServiceNameAndPriority(service, priority)
                .orElse(new ServiceSlaPolicyEntity(service, priority, responseMinutes, resolutionMinutes));
        entity.setTargets(responseMinutes, resolutionMinutes);
        serviceStore.save(entity);
        SlaPolicy policy = new SlaPolicy(Duration.ofMinutes(responseMinutes), Duration.ofMinutes(resolutionMinutes));
        slaService.setServicePolicy(service, priority, policy);
        audit.record("SERVICE_SLA_SAVED", "SERVICE", service, priority + ": respond " + responseMinutes
                + " min, resolve " + resolutionMinutes + " min");
        return policy;
    }

    /** Removes a service-specific SLA: that service falls back to the default for the priority. */
    @Transactional
    public void removeServiceOverride(String service, Priority priority) {
        serviceStore.findByServiceNameAndPriority(service, priority).ifPresent(serviceStore::delete);
        slaService.setServicePolicy(service, priority, null);
        audit.record("SERVICE_SLA_REMOVED", "SERVICE", service, priority + " back to the default SLA");
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
        validate(responseMinutes, resolutionMinutes);
        SlaPolicy policy = new SlaPolicy(Duration.ofMinutes(responseMinutes), Duration.ofMinutes(resolutionMinutes));
        store.save(priority, policy);
        slaService.setPolicy(priority, policy);
        return policy;
    }

    private static void validate(long responseMinutes, long resolutionMinutes) {
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
    }
}
