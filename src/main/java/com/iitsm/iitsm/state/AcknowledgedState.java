package com.iitsm.iitsm.state;

import java.util.Set;

import com.iitsm.iitsm.model.IncidentStatus;

public class AcknowledgedState extends AbstractIncidentState {
    @Override public IncidentStatus status() { return IncidentStatus.ACKNOWLEDGED; }
    @Override public Set<IncidentStatus> allowedNext() { return Set.of(IncidentStatus.IN_PROGRESS); }
}