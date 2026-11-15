package com.iitsm.iitsm.state;

import java.util.Set;

import com.iitsm.iitsm.model.IncidentStatus;

public interface IncidentState {
    IncidentStatus status();
    Set<IncidentStatus> allowedNext();

    /** Returns the state for the target status, or throws if the move is illegal. */
    IncidentState transitionTo(IncidentStatus target);
}