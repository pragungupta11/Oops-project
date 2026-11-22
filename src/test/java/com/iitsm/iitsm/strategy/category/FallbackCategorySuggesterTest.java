package com.iitsm.iitsm.strategy.category;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.model.CategorySuggestion;

class FallbackCategorySuggesterTest {
    private final KeywordCategorySuggester keywords = new KeywordCategorySuggester();

    /** Stands in for an AI suggester, so tests never call a real API. */
    private static RemoteCategorySuggester ai(String answer) {
        return d -> Optional.of(new CategorySuggestion(answer, "AI", "model answer"));
    }

    private static final RemoteCategorySuggester DOWN = d -> { throw new IllegalStateException("API unavailable"); };
    private static final RemoteCategorySuggester UNSURE = d -> Optional.empty();

    @Test
    void usesTheAiAnswerWhenAvailable() {
        FallbackCategorySuggester s = new FallbackCategorySuggester(List.of(ai("Cloud")), keywords);
        CategorySuggestion result = s.suggest("Payment app down").orElseThrow();
        assertEquals("Cloud", result.category());
        assertEquals("AI", result.source());
    }

    @Test
    void fallsBackToKeywordsWhenTheAiFails() {
        FallbackCategorySuggester s = new FallbackCategorySuggester(List.of(DOWN), keywords);
        CategorySuggestion result = s.suggest("Payment app down").orElseThrow();
        assertEquals("Application", result.category());
        assertEquals("keywords", result.source());
    }

    @Test
    void fallsBackToKeywordsWhenTheAiHasNoAnswer() {
        FallbackCategorySuggester s = new FallbackCategorySuggester(List.of(UNSURE), keywords);
        assertEquals("keywords", s.suggest("Payment app down").orElseThrow().source());
    }

    @Test
    void triesTheNextRemoteBeforeGivingUp() {
        FallbackCategorySuggester s = new FallbackCategorySuggester(List.of(DOWN, ai("Security")), keywords);
        assertEquals("Security", s.suggest("anything").orElseThrow().category());
    }

    @Test
    void withNoRemotesItIsPurelyRuleBased() {
        FallbackCategorySuggester s = new FallbackCategorySuggester(List.of(), keywords);
        assertEquals("Application", s.suggest("Payment app down").orElseThrow().category());
    }
}
