package com.example.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.ingestion.dto.response.AnalyticsSummaryResponse;
import com.example.ingestion.dto.response.BulkIngestResponse;
import com.example.ingestion.dto.response.PagedResponse;
import com.example.ingestion.dto.response.TransactionResponse;
import com.example.ingestion.dto.response.TriggerResponse;
import com.example.ingestion.entity.MerchantCategory;
import com.example.ingestion.entity.StrategyUsed;
import com.example.ingestion.llm.LlmClient;
import com.example.ingestion.repository.MerchantCategoryRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end flow against a real MySQL (Testcontainers):
 * POST /bulk -> POST /categorise/trigger -> GET /transactions + /analytics/summary.
 * The LLM client is mocked so categorisation relies on the seeded rules only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TransactionFlowIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("transactions_db");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @MockitoBean
    private LlmClient llmClient;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MerchantCategoryRepository merchantCategoryRepository;

    @Test
    void bulkIngest_thenTrigger_thenQueryAndAnalytics() {
        when(llmClient.categorise(anyString())).thenReturn(Optional.empty());

        List<Map<String, Object>> payload = List.of(
                Map.of("merchantName", "STARBUCKS NYC", "amount", 5.50, "currency", "USD",
                        "transactionDate", "2026-05-01", "source", "BANK_FEED"),
                Map.of("merchantName", "UBER TRIP 88", "amount", 20.00, "currency", "USD",
                        "transactionDate", "2026-06-02", "source", "IMPORT"),
                Map.of("merchantName", "MYSTERY SHOP", "amount", 9.99, "currency", "USD",
                        "transactionDate", "2026-06-03", "source", "MANUAL"));

        // 1) POST /bulk
        ResponseEntity<BulkIngestResponse> bulk = rest.postForEntity(
                "/api/v1/transactions/bulk", payload, BulkIngestResponse.class);
        assertThat(bulk.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bulk.getBody()).isNotNull();
        assertThat(bulk.getBody().acceptedCount()).isEqualTo(3);
        assertThat(bulk.getBody().rejectedCount()).isZero();

        // 2) POST /categorise/trigger -> 202 Accepted
        ResponseEntity<TriggerResponse> trigger = rest.postForEntity(
                "/api/v1/categorise/trigger", null, TriggerResponse.class);
        assertThat(trigger.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // 3) Await async categorisation, then assert the persisted category (typed)
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<MerchantCategory> uber =
                    merchantCategoryRepository.findByMerchantNameIn(List.of("UBER TRIP 88"));
            assertThat(uber).hasSize(1);
            assertThat(uber.get(0).getCategory()).isEqualTo("Transport");
            assertThat(uber.get(0).getStrategyUsed()).isEqualTo(StrategyUsed.RULE_BASED);
            assertThat(uber.get(0).getConfidenceScore()).isEqualByComparingTo("1.00");
        });

        // 4) GET /transactions?category=Transport returns UBER (typed PagedResponse)
        PagedResponse<TransactionResponse> transport = rest.exchange(
                "/api/v1/transactions?category=Transport",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() {
                }).getBody();
        assertThat(transport).isNotNull();
        assertThat(transport.content())
                .extracting(TransactionResponse::merchantName)
                .contains("UBER TRIP 88")
                .doesNotContain("MYSTERY SHOP"); // rule-less merchant not in Transport

        // 5) GET /analytics/summary (typed) — uncategorised spend is bucketed, not dropped
        AnalyticsSummaryResponse summary = rest.getForObject(
                "/api/v1/analytics/summary", AnalyticsSummaryResponse.class);
        assertThat(summary.totalSpendByCategory())
                .extracting(AnalyticsSummaryResponse.CategorySpend::category)
                .contains("Transport", "Uncategorised");
        assertThat(summary.topMerchants()).isNotEmpty();
    }
}

