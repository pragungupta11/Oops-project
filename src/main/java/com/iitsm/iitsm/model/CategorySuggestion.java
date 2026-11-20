package com.iitsm.iitsm.model;

/**
 * A suggested category for an incident description. Only a suggestion: the employee decides.
 * source says who suggested it ("keywords" or "AI"), reason says why, so it is explainable.
 */
public record CategorySuggestion(Category category, String source, String reason) {}
