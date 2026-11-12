package com.iitsm.iitsm.model;

import java.time.LocalDateTime;

public record SlaAlert(String incidentId, AlertType type, SlaStatus status, LocalDateTime detectedAt) {}