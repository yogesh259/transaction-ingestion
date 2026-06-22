package com.example.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.ingestion.dto.response.AnalyticsSummaryResponse.MonthlyDelta;
import com.example.ingestion.repository.TransactionRepository;
import com.example.ingestion.repository.projection.MonthlySpendProjection;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    private final AnalyticsService service = new AnalyticsService(mock(TransactionRepository.class));

    private static MonthlySpendProjection month(int year, int month, String spend) {
        return new MonthlySpendProjection() {
            @Override
            public Integer getTxYear() {
                return year;
            }

            @Override
            public Integer getTxMonth() {
                return month;
            }

            @Override
            public BigDecimal getTotalSpend() {
                return new BigDecimal(spend);
            }
        };
    }

    private static MonthlyDelta find(List<MonthlyDelta> deltas, String month) {
        return deltas.stream().filter(d -> d.month().equals(month)).findFirst().orElseThrow();
    }

    @Test
    void firstMonthHasNoPreviousOrDelta() {
        List<MonthlyDelta> result = service.computeMonthOverMonth(
                List.of(month(2026, 1, "100.00"), month(2026, 2, "150.00")));

        assertThat(result).hasSize(2);
        assertThat(find(result, "2026-01").previousMonthSpend()).isNull();
        assertThat(find(result, "2026-01").deltaPct()).isNull();
    }

    @Test
    void consecutiveMonthsComputePercentageDelta() {
        List<MonthlyDelta> result = service.computeMonthOverMonth(
                List.of(month(2026, 1, "100.00"), month(2026, 2, "150.00")));

        MonthlyDelta february = find(result, "2026-02");
        assertThat(february.previousMonthSpend()).isEqualByComparingTo("100.00");
        assertThat(february.deltaPct()).isEqualByComparingTo("50.00");
    }

    @Test
    void gapMonthLeavesPreviousNull_notComparedToNonAdjacentMonth() {
        // May then July (no June rows): July must NOT be compared to May.
        List<MonthlyDelta> result = service.computeMonthOverMonth(
                List.of(month(2026, 5, "200.00"), month(2026, 7, "260.00")));

        MonthlyDelta july = find(result, "2026-07");
        assertThat(july.previousMonthSpend()).isNull();
        assertThat(july.deltaPct()).isNull();
    }

    @Test
    void yearBoundaryIsTreatedAsAdjacent() {
        List<MonthlyDelta> result = service.computeMonthOverMonth(
                List.of(month(2025, 12, "80.00"), month(2026, 1, "120.00")));

        MonthlyDelta january = find(result, "2026-01");
        assertThat(january.previousMonthSpend()).isEqualByComparingTo("80.00");
        assertThat(january.deltaPct()).isEqualByComparingTo("50.00");
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertThat(service.computeMonthOverMonth(List.of())).isEmpty();
    }
}

