package com.example.ingestion.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated analytics: total spend by category, top 10 merchants by spend,
 * and month-over-month spend delta.
 */
public record AnalyticsSummaryResponse(
        List<CategorySpend> totalSpendByCategory,
        List<MerchantSpend> topMerchants,
        List<MonthlyDelta> monthOverMonthDelta
) {
    public record CategorySpend(String category, BigDecimal totalSpend) {
    }

    public record MerchantSpend(String merchantName, BigDecimal totalSpend) {
    }

    public record MonthlyDelta(String month, BigDecimal totalSpend, BigDecimal previousMonthSpend, BigDecimal deltaPct) {
    }
}

