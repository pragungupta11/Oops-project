package com.iitsm.iitsm.strategy.priority;

import com.iitsm.iitsm.model.Priority;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component("scoreBased")
@Primary

public class ScoreBasedPriorityStrategy implements PriorityStrategy {
    @Override
    public Priority calculate(int impact, int urgency, int criticality) {
        if (impact < 1 || impact > 3 || urgency < 1 || urgency > 3
                || criticality < 1 || criticality > 3) {
            throw new IllegalArgumentException("Each value must be 1 to 3");
        }
        int score = impact * urgency * criticality;
        if (score >= 18) return Priority.P1;
        if (score >= 9)  return Priority.P2;
        if (score >= 4)  return Priority.P3;
        return Priority.P4;
    }
}