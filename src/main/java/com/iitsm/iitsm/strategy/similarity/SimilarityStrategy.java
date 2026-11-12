package com.iitsm.iitsm.strategy.similarity;

public interface SimilarityStrategy {
    /** Returns 0.0 (nothing in common) to 1.0 (identical meaning). */
    double score(String a, String b);
}