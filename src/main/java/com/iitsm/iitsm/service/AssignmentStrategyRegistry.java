package com.iitsm.iitsm.service;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.iitsm.iitsm.strategy.assignment.AssignmentStrategy;

@Service
public class AssignmentStrategyRegistry {
    private final Map<String, AssignmentStrategy> strategies;
    private final IncidentProcessingService processing;

    public AssignmentStrategyRegistry(Map<String, AssignmentStrategy> strategies,
                                      IncidentProcessingService processing) {
        this.strategies = strategies;
        this.processing = processing;
    }

    /** Names the admin can choose from, e.g. skillBased, loadBased. */
    public Set<String> availableNames() {
        return strategies.keySet();
    }

    /** Switches the live assignment strategy. Throws if the name is unknown. */
    public void switchTo(String name) {
        AssignmentStrategy chosen = strategies.get(name);
        if (chosen == null) {
            throw new IllegalArgumentException(
                    "Unknown assignment strategy: " + name + ". Choose from " + availableNames());
        }
        processing.setAssignmentStrategy(chosen);
    }
}