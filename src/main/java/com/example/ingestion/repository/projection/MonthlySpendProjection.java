package com.example.ingestion.repository.projection;

import java.math.BigDecimal;

/**
 * Aggregated spend grouped by calendar year + month.
 */
public interface MonthlySpendProjection {
    Integer getTxYear();

    Integer getTxMonth();

    BigDecimal getTotalSpend();
}

