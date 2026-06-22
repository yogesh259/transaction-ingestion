package com.example.ingestion.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Admin payload to create or update a categorisation rule (upsert by keyword).
 */
public record RuleUpsertRequest(

        @NotBlank(message = "must not be blank")
        String keyword,

        @NotBlank(message = "must not be blank")
        String category,

        String subCategory,

        @NotNull(message = "is required")
        @PositiveOrZero(message = "must be zero or positive")
        Integer priority,

        @NotNull(message = "is required")
        Boolean isActive
) {
}

