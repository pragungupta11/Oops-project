package com.iitsm.iitsm.state;

import java.util.Set;

import com.iitsm.iitsm.model.IncidentStatus;

public class InProgressState extends AbstractIncidentState {
    @Override public IncidentStatus status() { return IncidentStatus.IN_PROGRESS; }
    @Override public Set<IncidentStatus> allowedNext() { return Set.of(IncidentStatus.RESOLVED, IncidentStatus.ASSIGNED); }
}