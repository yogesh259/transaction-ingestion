package com.example.ingestion.mapper;

import com.example.ingestion.dto.request.TransactionRequest;
import com.example.ingestion.dto.response.TransactionResponse;
import com.example.ingestion.entity.MerchantCategory;
import com.example.ingestion.entity.Transaction;

/**
 * Mapping helpers between {@link Transaction} entities and their DTOs.
 */
public final class TransactionMapper {

    private TransactionMapper() {
    }

    public static Transaction toEntity(TransactionRequest request) {
        return Transaction.builder()
                .merchantName(request.merchantName())
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
                tx.getAmount(),
                tx.getCurrency(),
                tx.getTransactionDate(),
                tx.getSource(),
                tx.getCreatedAt(),
                category != null ? category.getCategory() : null,
                category != null ? category.getSubCategory() : null
        );
    }
}

