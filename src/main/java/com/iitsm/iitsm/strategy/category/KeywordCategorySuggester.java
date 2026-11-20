package com.iitsm.iitsm.strategy.category;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.CategorySuggestion;

/**
 * Rule-based suggester: counts known keywords per category and picks the category with the
 * most hits. Works offline, is instant and predictable, so it is also the safety net when
 * an AI suggester is unavailable. If two categories tie, it suggests nothing rather than guess.
 */
@Component
public class KeywordCategorySuggester implements CategorySuggester {

    private static final Map<Category, Set<String>> KEYWORDS = new EnumMap<>(Category.class);

    static {
        KEYWORDS.put(Category.NETWORK, Set.of(
                "network", "wifi", "internet", "vpn", "router", "lan", "ethernet", "connection",
                "connectivity", "dns", "bandwidth", "ping", "latency", "disconnect", "disconnecting",
                "disconnected", "offline", "proxy", "switch", "cable"));
        KEYWORDS.put(Category.DATABASE, Set.of(
                "database", "db", "sql", "query", "table", "record", "deadlock", "mysql",
                "postgres", "postgresql", "oracle", "schema", "index", "duplicate", "corrupt", "corrupted"));
        KEYWORDS.put(Category.APPLICATION, Set.of(
                "app", "application", "payment", "website", "site", "portal", "page", "button",
                "crash", "crashed", "crashing", "bug", "screen", "form", "software", "checkout",
                "erp", "crm", "freeze", "frozen", "hang", "hanging"));
        KEYWORDS.put(Category.SECURITY, Set.of(
                "security", "phishing", "virus", "malware", "ransomware", "hacked", "hack", "breach",
                "suspicious", "spam", "attack", "firewall", "antivirus", "leak", "leaked", "scam",
                "unauthorised", "unauthorized"));
        KEYWORDS.put(Category.AUTHENTICATION, Set.of(
                "login", "logon", "signin", "password", "otp", "mfa", "2fa", "locked", "lockout",
                "account", "credential", "sso", "authentication", "username"));
        KEYWORDS.put(Category.CLOUD, Set.of(
                "cloud", "aws", "azure", "gcp", "s3", "vm", "instance", "kubernetes", "docker",
                "storage", "drive", "onedrive", "bucket", "saas", "container"));
    }

    @Override
    public Optional<CategorySuggestion> suggest(String description) {
        if (description == null || description.isBlank()) return Optional.empty();
        Set<String> words = words(description);

        Category best = null;
        List<String> bestHits = List.of();
        boolean tie = false;
        for (Map.Entry<Category, Set<String>> entry : KEYWORDS.entrySet()) {
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
