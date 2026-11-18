package com.iitsm.iitsm.strategy.priority;

import com.iitsm.iitsm.model.Priority;

import org.springframework.stereotype.Component;

@Component("weighted")

public class WeightedPriorityStrategy implements PriorityStrategy {
    @Override
    public Priority calculate(int impact, int urgency, int criticality) {
        if (impact < 1 || impact > 3 || urgency < 1 || urgency > 3
                || criticality < 1 || criticality > 3) {
            throw new IllegalArgumentException("Each value must be 1 to 3");
        }
        double score = impact * 0.5 + urgency * 0.3 + criticality * 0.2;
        if (score >= 2.6) return Priority.P1;
        if (score >= 2.0) return Priority.P2;
        if (score >= 1.5) return Priority.P3;
        return Priority.P4;
    }
}