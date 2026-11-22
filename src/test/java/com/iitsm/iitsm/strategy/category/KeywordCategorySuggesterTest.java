package com.iitsm.iitsm.strategy.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.model.CategorySuggestion;

class KeywordCategorySuggesterTest {
    private final KeywordCategorySuggester suggester = new KeywordCategorySuggester();

    private String categoryFor(String text) {
        return suggester.suggest(text).map(CategorySuggestion::category).orElse(null);
    }

    @Test
    void paymentAppProblemIsApplication() {
        // the exact case from our demo, where the employee wrongly chose Network
        assertEquals("Application", categoryFor("Payment app down"));
    }

    @Test
    void recognisesEachArea() {
        assertEquals("Network", categoryFor("Office wifi keeps disconnecting"));
        assertEquals("Authentication", categoryFor("Cannot login, password reset not working"));
        assertEquals("Security", categoryFor("Got a suspicious phishing email"));
        assertEquals("Database", categoryFor("SQL queries are hitting a deadlock"));
        assertEquals("Cloud", categoryFor("Files on the shared cloud drive are gone"));
    }

    @Test
    void explainsWhy() {
        CategorySuggestion s = suggester.suggest("Payment app down").orElseThrow();
        assertEquals("keywords", s.source());
        assertTrue(s.reason().contains("payment"));
    }

    @Test
    void noKnownWordsMeansNoSuggestion() {
        assertEquals(Optional.empty(), suggester.suggest("Something is weird today"));
        assertEquals(Optional.empty(), suggester.suggest("   "));
        assertEquals(Optional.empty(), suggester.suggest(null));
    }

    @Test
    void aTieMeansNoSuggestionRatherThanAGuess() {
        // one Application word (payment) and one Database word (database)
        assertEquals(Optional.empty(), suggester.suggest("payment database"));
    }
}
