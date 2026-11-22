package com.iitsm.iitsm.model;

/**
 * A suggested category for an incident description. Only a suggestion: the employee decides.
 * category is the category's name (e.g. "Application"); source says who suggested it
 * ("keywords" or "AI"), reason says why, so it is explainable.
 */
public record CategorySuggestion(String category, String source, String reason) {}
