package com.iitsm.iitsm.service;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.persistence.entity.AppSettingEntity;
import com.iitsm.iitsm.repository.AppSettingRepository;
import com.iitsm.iitsm.strategy.priority.PriorityStrategy;

/**
 * Lets the admin choose how the priority of NEW incidents is calculated (score-based or weighted).
 * Existing incidents keep their priority, so their SLA deadlines do not jump. The choice is audited
 * and saved, so it survives a restart. Any new PriorityStrategy bean appears here automatically.
 */
@Service
public class PriorityStrategyRegistry {
    static final String SETTING_KEY = "priority.strategy";
    private static final Logger log = LoggerFactory.getLogger(PriorityStrategyRegistry.class);

    private final Map<String, PriorityStrategy> strategies;
    private final IncidentProcessingService processing;
    private final AppSettingRepository settings;
    private final AuditService audit;

    public PriorityStrategyRegistry(Map<String, PriorityStrategy> strategies,
                                    IncidentProcessingService processing,
                                    AppSettingRepository settings,
                                    AuditService audit) {
        this.strategies = strategies;
        this.processing = processing;
        this.settings = settings;
        this.audit = audit;
    }

    /** Names the admin can choose from: scoreBased, weighted. */
    public Set<String> availableNames() {
        return strategies.keySet();
    }

    /** Name of the strategy in use right now. */
    public String currentName() {
        PriorityStrategy active = processing.getPriorityStrategy();
        return strategies.entrySet().stream()
                .filter(e -> e.getValue() == active)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("unknown");
    }

    /** Switches the priority calculation for new incidents, saves the choice and audits it. */
    @Transactional
    public void switchTo(String name) {
        PriorityStrategy chosen = strategies.get(name);
        if (chosen == null) {
            throw new IllegalArgumentException(
                    "Unknown priority strategy: " + name + ". Choose from " + availableNames());
        }
        String previous = currentName();
        processing.setPriorityStrategy(chosen);
        settings.save(new AppSettingEntity(SETTING_KEY, name));
        audit.record("PRIORITY_STRATEGY_CHANGED", "SETTING", SETTING_KEY, previous + " -> " + name);
    }

    /** On startup, re-apply the strategy the admin chose last time (default: the @Primary one). */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreSavedChoice() {
        settings.findById(SETTING_KEY).map(AppSettingEntity::getValue).ifPresent(name -> {
            PriorityStrategy saved = strategies.get(name);
            if (saved == null) {
                log.warn("Saved priority strategy '{}' no longer exists; keeping {}", name, currentName());
                return;
            }
            processing.setPriorityStrategy(saved);
            log.info("Priority strategy restored from settings: {}", name);
        });
    }
}
