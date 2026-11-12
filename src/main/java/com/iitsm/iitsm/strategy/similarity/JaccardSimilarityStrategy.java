package com.iitsm.iitsm.strategy.similarity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class JaccardSimilarityStrategy implements SimilarityStrategy {

    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "to", "is", "are", "was", "in", "on", "of", "for",
        "and", "or", "my", "we", "i", "it", "be", "can", "with", "make", "at", "from");

    // Different words for the same idea map to one canonical word.
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
        Map.entry("unavailable", "fail"), Map.entry("down", "fail"),
        Map.entry("failing", "fail"),     Map.entry("failed", "fail"),
        Map.entry("unable", "fail"),      Map.entry("outage", "fail"),
        Map.entry("error", "fail"),       Map.entry("broken", "fail"),
        Map.entry("crashed", "fail"),     Map.entry("crash", "fail"),
        Map.entry("app", "application"));

    @Override
    public double score(String a, String b) {
        Set<String> x = tokens(a);
        Set<String> y = tokens(b);
        if (x.isEmpty() || y.isEmpty()) return 0.0;

        Set<String> common = new HashSet<>(x);
        common.retainAll(y);
        Set<String> all = new HashSet<>(x);
        all.addAll(y);
        return (double) common.size() / all.size();
    }

    private Set<String> tokens(String text) {
        Set<String> result = new HashSet<>();
        for (String raw : text.toLowerCase().split("[^a-z0-9]+")) {
            if (raw.isEmpty() || STOP_WORDS.contains(raw)) continue;
            String word = singular(raw);
            result.add(SYNONYMS.getOrDefault(word, word));
        }
        return result;
    }

    private String singular(String word) {
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}