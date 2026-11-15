package com.iitsm.iitsm.state;

import java.util.Set;

import com.iitsm.iitsm.model.IncidentStatus;

public class VerifiedState extends AbstractIncidentState {
    @Override public IncidentStatus status() { return IncidentStatus.VERIFIED; }
    @Override public Set<IncidentStatus> allowedNext() { return Set.of(IncidentStatus.CLOSED); }
}