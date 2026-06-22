package com.example.ingestion.service;

import com.example.ingestion.dto.response.AnalyticsSummaryResponse;
import com.example.ingestion.dto.response.AnalyticsSummaryResponse.CategorySpend;
import com.example.ingestion.dto.response.AnalyticsSummaryResponse.MerchantSpend;
import com.example.ingestion.dto.response.AnalyticsSummaryResponse.MonthlyDelta;
import com.example.ingestion.repository.TransactionRepository;
import com.example.ingestion.repository.projection.MonthlySpendProjection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the analytics summary: total spend by category, top 10 merchants by
 * spend, and month-over-month spend delta.
 */
@Service
public class AnalyticsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TransactionRepository transactionRepository;

    public AnalyticsService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary() {
        List<CategorySpend> byCategory = transactionRepository.totalSpendByCategory().stream()
                .map(p -> new CategorySpend(p.getCategory(), p.getTotalSpend()))
                .toList();

        List<MerchantSpend> topMerchants = transactionRepository.topMerchantsBySpend().stream()
                .map(p -> new MerchantSpend(p.getMerchantName(), p.getTotalSpend()))
                .toList();

        List<MonthlyDelta> monthOverMonth = computeMonthOverMonth(transactionRepository.monthlySpend());

        return new AnalyticsSummaryResponse(byCategory, topMerchants, monthOverMonth);
    }

    /**
     * Computes the spend delta of each month against the immediately preceding
     * calendar month. When the preceding month has no transactions, the previous
     * spend and delta are left null.
     */
    List<MonthlyDelta> computeMonthOverMonth(List<MonthlySpendProjection> monthly) {
        Map<YearMonth, BigDecimal> spendByMonth = new LinkedHashMap<>();
        for (MonthlySpendProjection m : monthly) {
            spendByMonth.put(YearMonth.of(m.getTxYear(), m.getTxMonth()), m.getTotalSpend());
        }

        List<MonthlyDelta> result = new ArrayList<>();
        for (Map.Entry<YearMonth, BigDecimal> entry : spendByMonth.entrySet()) {
            YearMonth month = entry.getKey();
            BigDecimal total = entry.getValue();
            BigDecimal previous = spendByMonth.get(month.minusMonths(1));
            BigDecimal deltaPct = null;
            if (previous != null && previous.compareTo(BigDecimal.ZERO) != 0) {
                deltaPct = total.subtract(previous)
                        .divide(previous, 4, RoundingMode.HALF_UP)
                        .multiply(HUNDRED)
                        .setScale(2, RoundingMode.HALF_UP);
            }
            result.add(new MonthlyDelta(month.toString(), total, previous, deltaPct));
        }
        return result;
    }
}

