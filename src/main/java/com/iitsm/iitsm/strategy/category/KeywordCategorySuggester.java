package com.iitsm.iitsm.strategy.category;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.CategoryInfo;
import com.iitsm.iitsm.model.CategorySuggestion;
import com.iitsm.iitsm.service.CategoryCatalog;

/**
 * Rule-based suggester: counts known keywords per category and picks the category with the
 * most hits. Works offline, is instant and predictable, so it is also the safety net when
 * an AI suggester is unavailable. If two categories tie, it suggests nothing rather than guess.
 * In the running application the keywords come from the admin's category catalogue, so new
 * categories are suggested too; without a catalogue (unit tests) the built-in list is used.
 */
@Component
public class KeywordCategorySuggester implements CategorySuggester {

    /** Keywords of the six built-in categories (also used to seed the catalogue). */
    private static final Map<String, Set<String>> BUILT_IN = new LinkedHashMap<>();

    static {
        BUILT_IN.put("Network", Set.of(
                "network", "wifi", "internet", "vpn", "router", "lan", "ethernet", "connection",
                "connectivity", "dns", "bandwidth", "ping", "latency", "disconnect", "disconnecting",
                "disconnected", "offline", "proxy", "switch", "cable"));
        BUILT_IN.put("Database", Set.of(
                "database", "db", "sql", "query", "table", "record", "deadlock", "mysql",
                "postgres", "postgresql", "oracle", "schema", "index", "duplicate", "corrupt", "corrupted"));
        BUILT_IN.put("Application", Set.of(
                "app", "application", "payment", "website", "site", "portal", "page", "button",
                "crash", "crashed", "crashing", "bug", "screen", "form", "software", "checkout",
                "erp", "crm", "freeze", "frozen", "hang", "hanging"));
        BUILT_IN.put("Security", Set.of(
                "security", "phishing", "virus", "malware", "ransomware", "hacked", "hack", "breach",
                "suspicious", "spam", "attack", "firewall", "antivirus", "leak", "leaked", "scam",
                "unauthorised", "unauthorized"));
        BUILT_IN.put("Authentication", Set.of(
                "login", "logon", "signin", "password", "otp", "mfa", "2fa", "locked", "lockout",
                "account", "credential", "sso", "authentication", "username"));
        BUILT_IN.put("Cloud", Set.of(
                "cloud", "aws", "azure", "gcp", "s3", "vm", "instance", "kubernetes", "docker",
                "storage", "drive", "onedrive", "bucket", "saas", "container"));
    }

    /** The built-in keywords of a category, or an empty set for categories added by the admin. */
    public static Set<String> builtInKeywords(String category) {
        return BUILT_IN.getOrDefault(category, Set.of());
    }

    private final CategoryCatalog catalog;

    /** Without a catalogue: the six built-in categories (used by unit tests). */
    public KeywordCategorySuggester() {
        this.catalog = null;
    }

    /** In the application: the admin's active categories and their keywords. */
    @Autowired
    public KeywordCategorySuggester(CategoryCatalog catalog) {
        this.catalog = catalog;
    }

    private Map<String, Set<String>> keywords() {
        if (catalog == null) return BUILT_IN;
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (CategoryInfo c : catalog.active()) {
            map.put(c.name(), c.keywordSet());
        }
        return map;
    }

    @Override
    public Optional<CategorySuggestion> suggest(String description) {
        if (description == null || description.isBlank()) return Optional.empty();
        Set<String> words = words(description);

        String best = null;
        List<String> bestHits = List.of();
        boolean tie = false;
        for (Map.Entry<String, Set<String>> entry : keywords().entrySet()) {
            List<String> hits = new ArrayList<>();
            for (String w : words) {
                if (entry.getValue().contains(w)) hits.add(w);
            }
            if (hits.size() > bestHits.size()) {
                best = entry.getKey();
                bestHits = hits;
                tie = false;
            } else if (!hits.isEmpty() && hits.size() == bestHits.size()) {
                tie = true;
            }
        }
        if (best == null || tie) return Optional.empty();
        return Optional.of(new CategorySuggestion(best, "keywords", "matched: " + String.join(", ", bestHits)));
    }

    private static Set<String> words(String text) {
        Set<String> result = new HashSet<>();
        for (String raw : text.toLowerCase().split("[^a-z0-9]+")) {
            if (raw.isEmpty()) continue;
            result.add(raw);
            // simple plural: "queries" -> "query", "records" -> "record"
            if (raw.length() > 4 && raw.endsWith("ies")) result.add(raw.substring(0, raw.length() - 3) + "y");
            else if (raw.length() > 3 && raw.endsWith("s") && !raw.endsWith("ss")) result.add(raw.substring(0, raw.length() - 1));
        }
        return result;
    }
}
