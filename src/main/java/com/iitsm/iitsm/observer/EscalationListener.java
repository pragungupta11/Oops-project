package com.iitsm.iitsm.observer;

import com.iitsm.iitsm.model.EscalationEvent;

public interface EscalationListener {
    void onEscalation(EscalationEvent event);
}