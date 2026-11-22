package com.iitsm.iitsm.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.iitsm.audit.AuditService;
import com.iitsm.iitsm.exception.CatalogException;
import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.CategoryInfo;
import com.iitsm.iitsm.persistence.entity.IncidentCategoryEntity;
import com.iitsm.iitsm.repository.IncidentCategoryRepository;
import com.iitsm.iitsm.strategy.category.KeywordCategorySuggester;

/**
 * Incident categories, configured by the admin and stored in the database. Seeded with the six
 * built-in categories. New categories need no code change: report form, filters, charts, technician
 * skills, re-routing and the category suggester all read from here.
 */
@Component
public class CategoryCatalog {
    private final IncidentCategoryRepository repository;
    private final AuditService audit;
    private final org.springframework.transaction.support.TransactionTemplate ownTransaction;
    private volatile boolean seeded;

    public CategoryCatalog(IncidentCategoryRepository repository, AuditService audit,
                           org.springframework.transaction.PlatformTransactionManager transactions) {
        this.repository = repository;
        this.audit = audit;
        // Seeding runs in its OWN transaction: if the action that triggered it fails and rolls back,
        // the built-in categories must still be saved.
        this.ownTransaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Built-in categories first (in their usual order), then the admin's own, alphabetically. */
    public List<CategoryInfo> all() {
        ensureSeeded();
        return repository.findAll().stream()
                .map(e -> new CategoryInfo(e.getName(), e.getKeywords(), e.isActive(), e.isBuiltIn()))
                .sorted(Comparator.comparing((CategoryInfo c) -> !c.builtIn())
                        .thenComparing(c -> c.builtIn() ? builtInOrder(c.name()) : 0)
                        .thenComparing(CategoryInfo::name))
                .toList();
    }

    public List<CategoryInfo> active() {
        return all().stream().filter(CategoryInfo::active).toList();
    }

    public List<String> activeNames() {
        return active().stream().map(CategoryInfo::name).toList();
    }

    public List<String> allNames() {
        return all().stream().map(CategoryInfo::name).toList();
    }

    /** Returns the exact name of an active category, or throws when it is unknown or deactivated. */
    public String require(String name) {
        String n = name == null ? "" : name.trim();
        return active().stream().map(CategoryInfo::name).filter(c -> c.equalsIgnoreCase(n)).findFirst()
                .orElseThrow(() -> new InvalidIncidentDataException("Unknown or inactive category: " + name));
    }

    @Transactional
    public void add(String name, String keywords) {
        String n = name == null ? "" : name.trim();
        if (!n.matches("[A-Za-z][A-Za-z0-9 &-]{1,39}")) {
            throw new CatalogException("Category name: 2-40 characters (letters, digits, spaces, & or -), starting with a letter");
        }
        if (allNames().stream().anyMatch(c -> c.equalsIgnoreCase(n))) {
            throw new CatalogException("A category called " + n + " already exists");
        }
        repository.save(new IncidentCategoryEntity(n, clean(keywords), false));
        audit.record("CATEGORY_ADDED", "CATEGORY", n, "Keywords: " + clean(keywords));
    }

    @Transactional
    public void update(String name, String keywords, boolean active) {
        ensureSeeded();
        IncidentCategoryEntity e = repository.findById(name)
                .orElseThrow(() -> new CatalogException("No such category: " + name));
        if (!active && e.isActive() && active().size() <= 1) {
            throw new CatalogException("At least one category must stay active");
        }
        e.setKeywords(clean(keywords));
        e.setActive(active);
        repository.save(e);
        audit.record("CATEGORY_UPDATED", "CATEGORY", name, "Active " + active + ", keywords: " + clean(keywords));
    }

    // ------------------------------------------------------------------ helpers

    private synchronized void ensureSeeded() {
        if (seeded) return;
        ownTransaction.executeWithoutResult(status -> {
            if (repository.count() == 0) {
                for (Category c : Category.values()) {
                    repository.save(new IncidentCategoryEntity(c.label(),
                            String.join(", ", KeywordCategorySuggester.builtInKeywords(c.label())), true));
                }
            }
        });
        seeded = true;   // only after the seed transaction has committed
    }

    private static int builtInOrder(String name) {
        for (Category c : Category.values()) {
            if (c.label().equals(name)) return c.ordinal();
        }
        return Integer.MAX_VALUE;
    }

    private static String clean(String keywords) {
        if (keywords == null) return "";
        String k = keywords.trim().toLowerCase();
        if (k.length() > 1000) throw new CatalogException("Keywords: at most 1000 characters");
        return k;
    }
}
