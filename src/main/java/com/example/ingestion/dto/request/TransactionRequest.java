package com.example.ingestion.dto.request;

import com.example.ingestion.entity.Source;
import com.example.ingestion.validation.IsoCurrency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound transaction payload for bulk ingestion. Validated per-row so a single
 * bad entry does not reject the whole batch.
 */
public record TransactionRequest(

        @NotBlank(message = "must not be blank")
        String merchantName,

        @NotNull(message = "is required")
        @Positive(message = "must be positive")
        @Digits(integer = 15, fraction = 4, message = "must have <=15 integer and <=4 fraction digits")
        BigDecimal amount,

        @NotBlank(message = "is required")
        @IsoCurrency
        String currency,

        @NotNull(message = "is required")
        @PastOrPresent(message = "must not be in the future")
        LocalDate transactionDate,

        @NotNull(message = "must be one of BANK_FEED, MANUAL, IMPORT")
        Source source
) {
}

