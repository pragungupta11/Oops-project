package com.iitsm.iitsm.strategy.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.CategorySuggestion;

class KeywordCategorySuggesterTest {
    private final KeywordCategorySuggester suggester = new KeywordCategorySuggester();

    private Category categoryFor(String text) {
        return suggester.suggest(text).map(CategorySuggestion::category).orElse(null);
    }

    @Test
    void paymentAppProblemIsApplication() {
        // the exact case from our demo, where the employee wrongly chose Network
        assertEquals(Category.APPLICATION, categoryFor("Payment app down"));
    }

    @Test
    void recognisesEachArea() {
        assertEquals(Category.NETWORK, categoryFor("Office wifi keeps disconnecting"));
        assertEquals(Category.AUTHENTICATION, categoryFor("Cannot login, password reset not working"));
        assertEquals(Category.SECURITY, categoryFor("Got a suspicious phishing email"));
        assertEquals(Category.DATABASE, categoryFor("SQL queries are hitting a deadlock"));
        assertEquals(Category.CLOUD, categoryFor("Files on the shared cloud drive are gone"));
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
