package com.example.ingestion.llm;

import java.math.BigDecimal;

/**
 * Parsed structured response from the LLM: {@code { category, sub_category, confidence }}.
 */
public record LlmCategorisation(
        String category,
        String subCategory,
        BigDecimal confidence
) {
}

