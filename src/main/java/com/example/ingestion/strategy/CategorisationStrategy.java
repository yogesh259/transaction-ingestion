package com.example.ingestion.strategy;

import java.util.Optional;

/**
 * Strategy contract for resolving a merchant name to a category.
 * Implementations return {@link Optional#empty()} when they cannot categorise.
 */
public interface CategorisationStrategy {

    Optional<CategorisationResult> categorise(String merchantName);
}

