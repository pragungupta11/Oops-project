package com.iitsm.iitsm.strategy.category;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.CategorySuggestion;

/**
 * The suggester the rest of the app uses. Tries every remote (AI) suggester first; if one
 * throws, times out or has no answer, it moves on, and finally asks the local keyword rules.
 * So the feature never breaks, even with no internet or no API key.
 */
@Component
@Primary
public class FallbackCategorySuggester implements CategorySuggester {
    private static final Logger log = LoggerFactory.getLogger(FallbackCategorySuggester.class);

    private final List<RemoteCategorySuggester> remotes;
    private final KeywordCategorySuggester keywords;

    /** Used by Spring: picks up any RemoteCategorySuggester beans (zero is fine). */
    @Autowired
    public FallbackCategorySuggester(ObjectProvider<RemoteCategorySuggester> remotes,
                                     KeywordCategorySuggester keywords) {
        this(remotes.orderedStream().toList(), keywords);
    }

    /** Used by tests, which pass fake remote suggesters directly. */
    public FallbackCategorySuggester(List<RemoteCategorySuggester> remotes, KeywordCategorySuggester keywords) {
        this.remotes = remotes;
        this.keywords = keywords;
    }

    @Override
    public Optional<CategorySuggestion> suggest(String description) {
        for (RemoteCategorySuggester remote : remotes) {
            try {
                Optional<CategorySuggestion> answer = remote.suggest(description);
                if (answer.isPresent()) return answer;
            } catch (RuntimeException e) {
                log.warn("{} failed ({}); falling back", remote.getClass().getSimpleName(), e.getMessage());
            }
        }
        return keywords.suggest(description);
    }

    public int remoteCount() {
        return remotes.size();
    }
}
