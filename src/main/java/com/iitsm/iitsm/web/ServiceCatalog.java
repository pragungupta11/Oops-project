package com.iitsm.iitsm.web;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.exception.CatalogException;
import com.iitsm.iitsm.model.ItService;
import com.iitsm.iitsm.persistence.entity.ItServiceEntity;
import com.iitsm.iitsm.repository.ItServiceRepository;

/**
 * The IT services an incident can affect (criticality 1 = low, 3 = high), managed by the admin and
 * stored in the database. Seeded with the original six services on first use.
 */
@Component
public class ServiceCatalog {
    private final ItServiceRepository repository;
    private final AuditService audit;
    private final org.springframework.transaction.support.TransactionTemplate ownTransaction;
    private volatile boolean seeded;

    public ServiceCatalog(ItServiceRepository repository, AuditService audit,
                          org.springframework.transaction.PlatformTransactionManager transactions) {
        this.repository = repository;
        this.audit = audit;
        // Seeding runs in its OWN transaction, so a failing action cannot roll the defaults back.
        this.ownTransaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Active services, by name (offered when reporting). */
    public List<ItService> all() {
        return entities().stream().filter(ItServiceEntity::isActive).map(ServiceCatalog::toModel).toList();
    }

    /** Every service, including deactivated ones (admin page, filters over old incidents). */
    public List<ItServiceEntity> allIncludingInactive() {
        return entities();
    }

    /** An active service by name; unknown or deactivated services cannot be reported against. */
    public ItService byName(String name) {
        return entities().stream()
                .filter(s -> s.isActive() && s.getName().equals(name))
                .findFirst()
                .map(ServiceCatalog::toModel)
                .orElseThrow(() -> new IllegalArgumentException("Unknown service: " + name));
    }

    @Transactional
    public void add(String name, int criticality, Set<String> dependsOn) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 2 || n.length() > 60) throw new CatalogException("Service name: 2-60 characters");
        if (entities().stream().anyMatch(s -> s.getName().equalsIgnoreCase(n))) {
            throw new CatalogException("A service called " + n + " already exists");
        }
        checkCriticality(criticality);
        String deps = checkDependencies(n, dependsOn);
        repository.save(new ItServiceEntity(n, criticality, deps));
        audit.record("SERVICE_ADDED", "SERVICE", n, "Criticality " + criticality + (deps.isEmpty() ? "" : ", depends on " + deps));
    }

    @Transactional
    public void update(String name, int criticality, Set<String> dependsOn, boolean active) {
        ItServiceEntity e = repository.findById(name).orElseThrow(() -> new CatalogException("No such service: " + name));
        checkCriticality(criticality);
        if (!active && entities().stream().filter(ItServiceEntity::isActive).count() <= 1 && e.isActive()) {
            throw new CatalogException("At least one service must stay active");
        }
        String deps = checkDependencies(name, dependsOn);
        e.setCriticality(criticality);
        e.setDependsOn(deps);
        e.setActive(active);
        repository.save(e);
        audit.record("SERVICE_UPDATED", "SERVICE", name, "Criticality " + criticality + ", active " + active
                + (deps.isEmpty() ? "" : ", depends on " + deps));
    }

    // ------------------------------------------------------------------ helpers

    private List<ItServiceEntity> entities() {
        ensureSeeded();
        return repository.findAll().stream().sorted(Comparator.comparing(ItServiceEntity::getName)).toList();
    }

    private synchronized void ensureSeeded() {
        if (seeded) return;
        ownTransaction.executeWithoutResult(status -> {
            if (repository.count() == 0) {
                repository.save(new ItServiceEntity("Payment Gateway", 3, "Customer Portal"));
                repository.save(new ItServiceEntity("Customer Portal", 3, ""));
                repository.save(new ItServiceEntity("Email", 2, ""));
                repository.save(new ItServiceEntity("VPN", 2, ""));
                repository.save(new ItServiceEntity("HR Portal", 1, "Intranet"));
                repository.save(new ItServiceEntity("Intranet", 1, ""));
            }
        });
        seeded = true;   // only after the seed transaction has committed
    }

    private static void checkCriticality(int criticality) {
        if (criticality < 1 || criticality > 3) throw new CatalogException("Criticality must be 1 (low) to 3 (high)");
    }

    private String checkDependencies(String self, Set<String> dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty()) return "";
        Set<String> known = entities().stream().map(ItServiceEntity::getName).collect(Collectors.toSet());
        Set<String> result = new LinkedHashSet<>();
        for (String d : dependsOn) {
            String t = d == null ? "" : d.trim();
            if (t.isEmpty()) continue;
            if (t.equals(self)) throw new CatalogException("A service cannot depend on itself");
            if (!known.contains(t)) throw new CatalogException("Unknown service in dependencies: " + t);
            result.add(t);
        }
        return String.join(",", result);
    }

    private static ItService toModel(ItServiceEntity e) {
        Set<String> deps = e.getDependsOn().isEmpty() ? Set.of()
                : Arrays.stream(e.getDependsOn().split(",")).map(String::trim).filter(x -> !x.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ItService(e.getName(), e.getCriticality(), deps);
    }
}
