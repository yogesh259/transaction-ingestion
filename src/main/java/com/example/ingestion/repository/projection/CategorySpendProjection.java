package com.example.ingestion.repository.projection;

import java.math.BigDecimal;

/**
 * Aggregated spend grouped by resolved category.
 */
public interface CategorySpendProjection {
    String getCategory();

    BigDecimal getTotalSpend();
}

