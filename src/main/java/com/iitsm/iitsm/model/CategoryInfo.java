package com.iitsm.iitsm.model;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** A category as configured by the admin: name, suggester keywords, active flag. */
public record CategoryInfo(String name, String keywords, boolean active, boolean builtIn) {

    /** Keywords as a set of lowercase words (for the category suggester). */
    public Set<String> keywordSet() {
        if (keywords == null || keywords.isBlank()) return Set.of();
        return Arrays.stream(keywords.toLowerCase().split("[,\\s]+"))
                .map(String::trim).filter(k -> !k.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
