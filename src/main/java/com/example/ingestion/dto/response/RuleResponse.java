package com.example.ingestion.dto.response;

/**
 * Persisted categorisation rule view.
 */
public record RuleResponse(
        Long id,
        String keyword,
        String category,
        String subCategory,
        Integer priority,
        Boolean isActive
) {
}

