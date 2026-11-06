package com.iitsm.iitsm.model;

import java.time.Duration;

public record SlaPolicy(Duration responseTarget, Duration resolutionTarget) {}
