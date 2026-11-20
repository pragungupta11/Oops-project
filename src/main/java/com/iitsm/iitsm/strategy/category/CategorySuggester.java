package com.iitsm.iitsm.strategy.category;

import java.util.Optional;

import com.iitsm.iitsm.model.CategorySuggestion;

/** Suggests an incident category from its description. Empty when it has no confident guess. */
public interface CategorySuggester {
    Optional<CategorySuggestion> suggest(String description);
}
