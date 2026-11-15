package com.iitsm.iitsm.state;

import java.util.Set;

import com.iitsm.iitsm.model.IncidentStatus;

public class AssignedState extends AbstractIncidentState {
    @Override public IncidentStatus status() { return IncidentStatus.ASSIGNED; }
    @Override public Set<IncidentStatus> allowedNext() { return Set.of(IncidentStatus.ACKNOWLEDGED); }
}