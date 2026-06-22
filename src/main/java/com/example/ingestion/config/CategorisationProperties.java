package com.example.ingestion.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated binding for the {@code categorization.*} configuration.
 */
@Validated
@ConfigurationProperties(prefix = "categorization")
public record CategorisationProperties(
        @Valid Fallback fallback,
        @Valid Async async,
        @Valid Llm llm,
        @Positive int maxBulkSize
) {

    /** LLM fallback strategy toggle. */
    public record Fallback(boolean enabled) {
    }

    /** Async thread-pool sizing for categorisation work. */
    public record Async(
            @Positive int coreSize,
            @Positive int maxSize,
            @PositiveOrZero int queueCapacity,
            @NotBlank String threadNamePrefix
    ) {
        @AssertTrue(message = "categorization.async.max-size must be >= core-size")
        public boolean isPoolSizingValid() {
            return maxSize >= coreSize;
        }
    }

    /** External LLM client settings. A blank api-key disables outbound LLM calls. */
    public record Llm(
            @NotBlank String baseUrl,
            String apiKey,
            @NotBlank String model,
            @Min(1) int timeoutSeconds
    ) {
    }
}

