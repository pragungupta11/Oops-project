package com.iitsm.iitsm.factory;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.ItService;
import com.iitsm.iitsm.model.User;

@Component
public class IncidentFactory {
    private final AtomicInteger counter = new AtomicInteger(1000);

    public Incident create(User reporter, String description, Category category,
                           int impact, int urgency, ItService service) {
        return create(reporter, description, category, impact, urgency, service,
                      LocalDateTime.now());
    }

    public Incident create(User reporter, String description, Category category,
                           int impact, int urgency, ItService service,
                           LocalDateTime createdAt) {
        if (reporter == null) throw new InvalidIncidentDataException("Reporter is required");
        if (description == null || description.isBlank())
            throw new InvalidIncidentDataException("Description is required");
        if (category == null) throw new InvalidIncidentDataException("Category is required");
        if (service == null) throw new InvalidIncidentDataException("Affected service is required");
        if (impact < 1 || impact > 3 || urgency < 1 || urgency > 3)
            throw new InvalidIncidentDataException("Impact and urgency must be 1 to 3");

        Incident incident = new Incident(
            "INC" + counter.incrementAndGet(), description.trim(), category.label(),
            impact, urgency, service.getCriticality(), createdAt);
        incident.setReporter(reporter);
        incident.setAffectedService(service);
        return incident;
    }
}