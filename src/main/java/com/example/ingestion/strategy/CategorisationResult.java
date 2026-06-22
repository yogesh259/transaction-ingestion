package com.example.ingestion.strategy;

import com.example.ingestion.entity.StrategyUsed;
import java.math.BigDecimal;

/**
 * Outcome of a categorisation attempt.
 */
public record CategorisationResult(
        String category,
        String subCategory,
        BigDecimal confidenceScore,
        StrategyUsed strategyUsed
) {
}

