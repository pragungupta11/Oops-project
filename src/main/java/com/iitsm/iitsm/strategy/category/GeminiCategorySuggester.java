package com.iitsm.iitsm.strategy.category;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.CategorySuggestion;
import com.iitsm.iitsm.service.CategoryCatalog;

/**
 * Third-party integration: asks Google's Gemini API which category an incident description belongs to.
 *
 * Adapter pattern: it adapts Gemini's REST API (HTTP + JSON) to our own CategorySuggester interface,
 * so the rest of the application never knows an external service is involved.
 *
 * It is deliberately cautious: Gemini may only answer with one of the active categories; any other
 * answer, a missing API key, a timeout or any error means "no suggestion", and the
 * FallbackCategorySuggester then uses the local keyword rules. The demo therefore always works.
 */
@Component
public class GeminiCategorySuggester implements RemoteCategorySuggester {
    private static final Logger log = LoggerFactory.getLogger(GeminiCategorySuggester.class);
    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int CACHE_SIZE = 100;

    private final CategoryCatalog categories;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient http;

    /** Recent answers, so typing does not send the same description to Google again and again. */
    private final Map<String, Optional<String>> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<String>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    @Autowired
    public GeminiCategorySuggester(CategoryCatalog categories,
                                   @Value("${app.ai.gemini.api-key:}") String apiKey,
                                   @Value("${app.ai.gemini.model:gemini-3.5-flash-lite}") String model,
                                   @Value("${app.ai.gemini.timeout-ms:3000}") long timeoutMs) {
        this(categories, apiKey, model, DEFAULT_BASE_URL, Duration.ofMillis(timeoutMs));
    }

    /** Also used by tests, e.g. with an unreachable base URL. */
    GeminiCategorySuggester(CategoryCatalog categories, String apiKey, String model, String baseUrl, Duration timeout) {
        this.categories = categories;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        if (!isEnabled()) {
            log.info("Gemini category suggestions are off (no GEMINI_API_KEY); keyword rules are used.");
        } else {
            log.info("Gemini category suggestions are on (model {}).", model);
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    @Override
    public Optional<CategorySuggestion> suggest(String description) {
        if (!isEnabled() || description == null || description.isBlank()) return Optional.empty();
        String text = description.trim();
        List<String> allowed = categories.activeNames();
        String cacheKey = String.join("|", allowed) + "#" + text;

        Optional<String> answer;
        synchronized (cache) {
            answer = cache.get(cacheKey);
        }
        if (answer == null) {
            answer = ask(text, allowed);
            synchronized (cache) {
                cache.put(cacheKey, answer);
            }
        }
        return answer.map(c -> new CategorySuggestion(c, "AI (Gemini)", "classified by " + model));
    }

    /** One call to Gemini; any problem becomes "no answer" (logged), never an error for the user. */
    private Optional<String> ask(String description, List<String> allowed) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + model + ":generateContent"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)   // the key travels in a header, never in the URL
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequest(description, allowed)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Gemini answered HTTP {}; using keyword rules instead", response.statusCode());
                return Optional.empty();
            }
            return parseCategory(response.body(), allowed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {   // timeout, no network, DNS, bad URL, ...
            log.warn("Gemini not reachable ({}); using keyword rules instead", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** The JSON body of a generateContent request, with the description safely escaped. */
    static String buildRequest(String description, List<String> allowed) {
        String prompt = "You classify IT incidents for a help desk. Choose exactly one category from this list: "
                + String.join(", ", allowed) + ". Reply with only the category name, nothing else. "
                + "If none fits, reply UNKNOWN. Incident description: " + description;
        return "{\"contents\":[{\"parts\":[{\"text\":\"" + escape(prompt) + "\"}]}],"
                + "\"generationConfig\":{\"temperature\":0}}";
    }

    private static final Pattern TEXT = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Reads the model's text from the response and accepts it only if it names an allowed category. */
    static Optional<String> parseCategory(String responseBody, List<String> allowed) {
        if (responseBody == null) return Optional.empty();
        Matcher m = TEXT.matcher(responseBody);
        if (!m.find()) return Optional.empty();
        String answer = unescape(m.group(1)).trim().replaceAll("^[\\s\"'*`.]+|[\\s\"'*`.!]+$", "");
        return allowed.stream().filter(c -> c.equalsIgnoreCase(answer)).findFirst();
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
