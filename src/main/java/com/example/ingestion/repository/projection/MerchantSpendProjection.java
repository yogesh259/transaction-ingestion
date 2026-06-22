package com.example.ingestion.repository.projection;

import java.math.BigDecimal;

/**
 * Aggregated spend grouped by merchant.
 */
public interface MerchantSpendProjection {
    String getMerchantName();

    BigDecimal getTotalSpend();
}

