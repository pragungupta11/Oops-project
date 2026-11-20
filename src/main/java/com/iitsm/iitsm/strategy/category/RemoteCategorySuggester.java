package com.iitsm.iitsm.strategy.category;

/**
 * A suggester that depends on an outside service (for example an AI model over HTTP) and may
 * therefore be slow, unavailable or wrong. The FallbackCategorySuggester tries these first and
 * falls back to the local keyword rules whenever one fails.
 */
public interface RemoteCategorySuggester extends CategorySuggester {
}
