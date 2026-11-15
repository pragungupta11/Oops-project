package com.iitsm.iitsm.state;

import com.iitsm.iitsm.model.IncidentStatus;

public final class IncidentStates {
    private IncidentStates() {}

    public static IncidentState of(IncidentStatus status) {
        return switch (status) {
            case NEW -> new NewState();
            case CLASSIFIED -> new ClassifiedState();
            case ASSIGNED -> new AssignedState();
            case ACKNOWLEDGED -> new AcknowledgedState();
            case IN_PROGRESS -> new InProgressState();
            case RESOLVED -> new ResolvedState();
            case VERIFIED -> new VerifiedState();
            case CLOSED -> new ClosedState();
        };
    }
}