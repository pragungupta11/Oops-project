package com.iitsm.iitsm.state;

import java.util.Set;

import com.iitsm.iitsm.model.IncidentStatus;

public class ClassifiedState extends AbstractIncidentState {
    @Override public IncidentStatus status() { return IncidentStatus.CLASSIFIED; }
    @Override public Set<IncidentStatus> allowedNext() { return Set.of(IncidentStatus.ASSIGNED); }
}