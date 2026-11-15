package com.iitsm.iitsm.state;

import com.iitsm.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.iitsm.model.IncidentStatus;

public abstract class AbstractIncidentState implements IncidentState {
    @Override
    public IncidentState transitionTo(IncidentStatus target) {
        if (!allowedNext().contains(target)) {
            throw new InvalidStateTransitionException(
                "Illegal transition: " + status() + " -> " + target);
        }
        return IncidentStates.of(target);
    }
}