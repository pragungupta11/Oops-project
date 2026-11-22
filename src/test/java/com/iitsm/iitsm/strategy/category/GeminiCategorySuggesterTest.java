package com.iitsm.iitsm.strategy.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.service.CategoryCatalog;

/** The Gemini adapter: never calls Google here; checks the request, the parsing and the fallback. */
class GeminiCategorySuggesterTest {
    private final List<String> allowed = List.of("Network", "Database", "Application", "Security", "Authentication", "Cloud");
    private final CategoryCatalog catalog = mock(CategoryCatalog.class);

    GeminiCategorySuggesterTest() {
        when(catalog.activeNames()).thenReturn(allowed);
    }

    private static String geminiResponse(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + text + "\"}],\"role\":\"model\"},"
                + "\"finishReason\":\"STOP\"}]}";
    }

    @Test
    void withoutAnApiKeyItIsOffAndAsksNothing() {
        GeminiCategorySuggester gemini = new GeminiCategorySuggester(catalog, "", "any-model",
                GeminiCategorySuggester.DEFAULT_BASE_URL, Duration.ofSeconds(1));
        assertFalse(gemini.isEnabled());
        assertEquals(Optional.empty(), gemini.suggest("Payment app down"));
    }

    @Test
    void acceptsOnlyAnAllowedCategory() {
        assertEquals(Optional.of("Network"), GeminiCategorySuggester.parseCategory(geminiResponse("Network\\n"), allowed));
        assertEquals(Optional.of("Database"), GeminiCategorySuggester.parseCategory(geminiResponse("**database**."), allowed));
        assertEquals(Optional.empty(), GeminiCategorySuggester.parseCategory(geminiResponse("Printer"), allowed));
        assertEquals(Optional.empty(), GeminiCategorySuggester.parseCategory(geminiResponse("UNKNOWN"), allowed));
        assertEquals(Optional.empty(), GeminiCategorySuggester.parseCategory("not json at all", allowed));
    }

    @Test
    void theDescriptionIsSafelyEscapedInTheRequest() {
        String body = GeminiCategorySuggester.buildRequest("He said \"VPN down\"\nagain \\ now", allowed);
        assertTrue(body.contains("\\\"VPN down\\\""));
        assertTrue(body.contains("\\nagain"));
        assertTrue(body.contains("\\\\ now"));
        assertTrue(body.contains("Network, Database, Application"));
    }

    @Test
    void unreachableServiceMeansNoSuggestionAndTheKeywordsTakeOver() {
        GeminiCategorySuggester gemini = new GeminiCategorySuggester(catalog, "test-key", "any-model",
                "http://127.0.0.1:9/v1beta/models/", Duration.ofMillis(500));   // nothing listens on port 9
        assertEquals(Optional.empty(), gemini.suggest("Payment app down"));

        FallbackCategorySuggester chain = new FallbackCategorySuggester(List.of(gemini), new KeywordCategorySuggester());
        assertEquals("keywords", chain.suggest("Payment app down").orElseThrow().source());
        assertEquals("Application", chain.suggest("Payment app down").orElseThrow().category());
    }
}
