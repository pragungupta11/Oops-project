package com.iitsm.iitsm.state;

import java.util.Set;

import com.iitsm.iitsm.model.IncidentStatus;

public class ClosedState extends AbstractIncidentState {
    @Override public IncidentStatus status() { return IncidentStatus.CLOSED; }
    @Override public Set<IncidentStatus> allowedNext() { return Set.of(); }
}