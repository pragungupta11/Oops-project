package com.iitsm.iitsm.model;

import java.time.LocalDateTime;

public record EscalationEvent(String incidentId, EscalationLevel from,
                              EscalationLevel to, LocalDateTime detectedAt) {}
