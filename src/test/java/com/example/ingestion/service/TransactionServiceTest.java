package com.example.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ingestion.config.CategorisationProperties;
import com.example.ingestion.dto.request.TransactionRequest;
import com.example.ingestion.dto.response.BulkIngestResponse;
import com.example.ingestion.entity.Source;
import com.example.ingestion.exception.PayloadTooLargeException;
import com.example.ingestion.repository.MerchantCategoryRepository;
import com.example.ingestion.repository.TransactionRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TransactionServiceTest {

    private static final CategorisationProperties PROPS = new CategorisationProperties(
            new CategorisationProperties.Fallback(true), null, null, 1000);

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final MerchantCategoryRepository merchantCategoryRepository = mock(MerchantCategoryRepository.class);
    private final TransactionService service =
            new TransactionService(transactionRepository, merchantCategoryRepository, validator, PROPS);

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    private static TransactionRequest request(String merchant, String currency) {
        return new TransactionRequest(merchant, new BigDecimal("10.0000"), currency,
                LocalDate.of(2026, 1, 1), Source.MANUAL);
    }

    @Test
    void unknownCurrencyIsRejected_perRow() {
        when(transactionRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        BulkIngestResponse response = service.ingestBulk(List.of(
                request("ACME", "XYZ"),   // invalid ISO-4217 code
                request("ACME", "USD")));

        assertThat(response.acceptedCount()).isEqualTo(1);
        assertThat(response.rejectedCount()).isEqualTo(1);
        assertThat(response.errors().get(0).index()).isEqualTo(0);
        assertThat(response.errors().get(0).reason()).contains("currency");
    }

    @Test
    void emptyMerchantNameIsRejected() {
        BulkIngestResponse response = service.ingestBulk(List.of(request("", "USD")));

        assertThat(response.acceptedCount()).isZero();
        assertThat(response.rejectedCount()).isEqualTo(1);
        assertThat(response.errors().get(0).reason()).contains("merchantName");
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void duplicateValidRows_areBothAccepted() {
        when(transactionRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        TransactionRequest duplicate = request("ACME", "USD");
        BulkIngestResponse response = service.ingestBulk(List.of(duplicate, duplicate));

        assertThat(response.acceptedCount()).isEqualTo(2);
        assertThat(response.rejectedCount()).isZero();
    }

    @Test
    void emptyPayloadReturnsEmptySummary() {
        BulkIngestResponse response = service.ingestBulk(List.of());

        assertThat(response.acceptedCount()).isZero();
        assertThat(response.rejectedCount()).isZero();
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void oversizedBatchIsRejected() {
        CategorisationProperties smallCap = new CategorisationProperties(
                new CategorisationProperties.Fallback(true), null, null, 2);
        TransactionService capped =
                new TransactionService(transactionRepository, merchantCategoryRepository, validator, smallCap);

        List<TransactionRequest> threeRows = List.of(
                request("A", "USD"), request("B", "USD"), request("C", "USD"));

        assertThatThrownBy(() -> capped.ingestBulk(threeRows))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("exceeds the maximum");
        verify(transactionRepository, never()).saveAll(anyList());
    }
}

