package com.iitsm.iitsm.state;

import java.util.Set;

import com.iitsm.iitsm.model.IncidentStatus;

public class ResolvedState extends AbstractIncidentState {
    @Override public IncidentStatus status() { return IncidentStatus.RESOLVED; }
    @Override public Set<IncidentStatus> allowedNext() {
        return Set.of(IncidentStatus.VERIFIED, IncidentStatus.IN_PROGRESS);
    }
}