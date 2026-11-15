package com.iitsm.iitsm.state;

import java.util.Set;

import com.iitsm.iitsm.model.IncidentStatus;

public class NewState extends AbstractIncidentState {
    @Override public IncidentStatus status() { return IncidentStatus.NEW; }
    @Override public Set<IncidentStatus> allowedNext() { return Set.of(IncidentStatus.CLASSIFIED); }
}