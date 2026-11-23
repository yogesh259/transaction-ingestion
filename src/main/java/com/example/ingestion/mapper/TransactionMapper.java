package com.example.ingestion.mapper;

import com.example.ingestion.dto.request.TransactionRequest;
import com.example.ingestion.dto.response.TransactionResponse;
import com.example.ingestion.entity.MerchantCategory;
import com.example.ingestion.entity.Transaction;
import com.example.ingestion.normalise.MerchantNameNormaliser;

/**
 * Mapping helpers between {@link Transaction} entities and their DTOs.
 */
public final class TransactionMapper {

    private TransactionMapper() {
    }

    /**
     * The incoming {@code merchantName} may be a raw bank narration; it is
     * normalised to a stable merchant key and the original is kept as the
     * description when they differ.
     */
    public static Transaction toEntity(TransactionRequest request) {
        String raw = request.merchantName();
        String merchant = MerchantNameNormaliser.normalise(raw);
        String trimmedRaw = raw == null ? null : raw.trim();
        return Transaction.builder()
                .merchantName(merchant)
                .description(trimmedRaw != null && !trimmedRaw.equals(merchant) ? trimmedRaw : null)
                .amount(request.amount())
                .currency(request.currency())
                .transactionDate(request.transactionDate())
                .source(request.source())
                .build();
    }

    public static TransactionResponse toResponse(Transaction tx, MerchantCategory category) {
        return new TransactionResponse(
                tx.getId(),
                tx.getMerchantName(),
                tx.getDescription(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getTransactionDate(),
                tx.getSource(),
                tx.getCreatedAt(),
                category != null ? category.getCategory() : null,
                category != null ? category.getSubCategory() : null,
                category != null ? category.getConfidenceScore() : null,
                category != null ? category.getStrategyUsed() : null
        );
    }
}

