package com.example.ingestion.dto.response;

import com.example.ingestion.entity.Source;
import com.example.ingestion.entity.StrategyUsed;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A transaction enriched with its resolved category (null when uncategorised).
 */
public record TransactionResponse(
        Long id,
        String merchantName,
        String description,
        BigDecimal amount,
        String currency,
        LocalDate transactionDate,
        Source source,
        Instant createdAt,
        String category,
        String subCategory,
        BigDecimal confidenceScore,
        StrategyUsed strategyUsed
) {
}

