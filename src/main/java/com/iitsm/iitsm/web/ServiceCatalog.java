package com.iitsm.iitsm.web;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.ItService;

/** Fixed list of IT services an incident can affect (criticality 1 = low, 3 = high). */
@Component
public class ServiceCatalog {
    private final List<ItService> services = List.of(
            new ItService("Payment Gateway", 3, Set.of("Customer Portal")),
            new ItService("Customer Portal", 3, Set.of()),
            new ItService("Email", 2, Set.of()),
            new ItService("VPN", 2, Set.of()),
            new ItService("HR Portal", 1, Set.of("Intranet")),
            new ItService("Intranet", 1, Set.of()));

    public List<ItService> all() {
        return services;
    }

    public ItService byName(String name) {
        return services.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown service: " + name));
    }
}
