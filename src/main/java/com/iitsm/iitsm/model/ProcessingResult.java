package com.iitsm.iitsm.model;

import java.time.LocalDateTime;

public record ProcessingResult(Priority priority, Technician technician,
                               LocalDateTime responseDeadline,
                               LocalDateTime resolutionDeadline) {}