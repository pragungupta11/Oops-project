package com.iitsm.iitsm.strategy.priority;

import com.iitsm.iitsm.model.Priority;

public interface PriorityStrategy {
    Priority calculate(int impact, int urgency, int criticality);
}