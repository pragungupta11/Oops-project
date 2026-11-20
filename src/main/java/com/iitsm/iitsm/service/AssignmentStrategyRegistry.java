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
import com.iitsm.iitsm.strategy.assignment.AssignmentStrategy;

/**
 * Lets the admin choose how new incidents are assigned. The choice is audited (who changed it,
 * when, from what to what) and saved, so it survives a restart.
 */
@Service
public class AssignmentStrategyRegistry {
    static final String SETTING_KEY = "assignment.strategy";
    private static final Logger log = LoggerFactory.getLogger(AssignmentStrategyRegistry.class);

    private final Map<String, AssignmentStrategy> strategies;
    private final IncidentProcessingService processing;
    private final AppSettingRepository settings;
    private final AuditService audit;

    public AssignmentStrategyRegistry(Map<String, AssignmentStrategy> strategies,
                                      IncidentProcessingService processing,
                                      AppSettingRepository settings,
                                      AuditService audit) {
        this.strategies = strategies;
        this.processing = processing;
        this.settings = settings;
        this.audit = audit;
    }

    /** Names the admin can choose from, e.g. skillBased, loadBased. */
    public Set<String> availableNames() {
        return strategies.keySet();
    }

    /** Name of the strategy in use right now. */
    public String currentName() {
        AssignmentStrategy active = processing.getAssignmentStrategy();
        return strategies.entrySet().stream()
                .filter(e -> e.getValue() == active)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("unknown");
    }

    /** Switches the live assignment strategy, saves the choice and records it in the audit log. */
    @Transactional
    public void switchTo(String name) {
        AssignmentStrategy chosen = strategies.get(name);
        if (chosen == null) {
            throw new IllegalArgumentException(
                    "Unknown assignment strategy: " + name + ". Choose from " + availableNames());
        }
        String previous = currentName();
        processing.setAssignmentStrategy(chosen);
        settings.save(new AppSettingEntity(SETTING_KEY, name));
        audit.record("ASSIGNMENT_STRATEGY_CHANGED", "SETTING", SETTING_KEY, previous + " -> " + name);
    }

    /** On startup, re-apply the strategy the admin chose last time (default: the @Primary one). */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreSavedChoice() {
        settings.findById(SETTING_KEY).map(AppSettingEntity::getValue).ifPresent(name -> {
            AssignmentStrategy saved = strategies.get(name);
            if (saved == null) {
                log.warn("Saved assignment strategy '{}' no longer exists; keeping {}", name, currentName());
                return;
            }
            processing.setAssignmentStrategy(saved);
            log.info("Assignment strategy restored from settings: {}", name);
        });
    }
}
