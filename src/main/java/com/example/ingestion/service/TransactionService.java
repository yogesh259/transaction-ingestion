package com.example.ingestion.service;

import com.example.ingestion.config.CategorisationProperties;
import com.example.ingestion.dto.request.TransactionRequest;
import com.example.ingestion.dto.response.BulkIngestResponse;
import com.example.ingestion.dto.response.BulkIngestResponse.RejectedItem;
import com.example.ingestion.dto.response.TransactionResponse;
import com.example.ingestion.entity.MerchantCategory;
import com.example.ingestion.entity.Source;
import com.example.ingestion.entity.Transaction;
import com.example.ingestion.exception.PayloadTooLargeException;
import com.example.ingestion.mapper.TransactionMapper;
import com.example.ingestion.repository.MerchantCategoryRepository;
import com.example.ingestion.repository.TransactionRepository;
import com.example.ingestion.spec.TransactionSpecifications;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles bulk ingestion (per-row validation with partial accept and a maximum
 * batch size) and the paginated, filterable query of transactions.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final MerchantCategoryRepository merchantCategoryRepository;
    private final Validator validator;
    private final int maxBulkSize;

    public TransactionService(TransactionRepository transactionRepository,
                              MerchantCategoryRepository merchantCategoryRepository,
                              Validator validator,
                              CategorisationProperties properties) {
        this.transactionRepository = transactionRepository;
        this.merchantCategoryRepository = merchantCategoryRepository;
        this.validator = validator;
        this.maxBulkSize = properties.maxBulkSize();
    }

    /**
     * Validates each row independently, persisting the valid ones and reporting
     * the rejected ones with their index and reason. Batches larger than the
     * configured maximum are rejected up front.
     */
    @Transactional
    public BulkIngestResponse ingestBulk(List<TransactionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new BulkIngestResponse(0, 0, List.of());
        }
        if (requests.size() > maxBulkSize) {
            throw new PayloadTooLargeException(
                    "Bulk batch of %d exceeds the maximum of %d".formatted(requests.size(), maxBulkSize));
        }

        List<Transaction> toPersist = new ArrayList<>();
        List<RejectedItem> errors = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            TransactionRequest request = requests.get(i);
            if (request == null) {
                errors.add(new RejectedItem(i, "transaction must not be null"));
                continue;
            }
            Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);
            if (violations.isEmpty()) {
                toPersist.add(TransactionMapper.toEntity(request));
            } else {
                errors.add(new RejectedItem(i, formatViolations(violations)));
            }
        }

        if (!toPersist.isEmpty()) {
            transactionRepository.saveAll(toPersist);
        }
        log.info("Bulk ingest: {} accepted, {} rejected", toPersist.size(), errors.size());
        return new BulkIngestResponse(toPersist.size(), errors.size(), errors);
    }

    /** Removes all transactions and merchant categories (rules are kept). Used by the demo reset. */
    @Transactional
    public void deleteAll() {
        merchantCategoryRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        log.info("All transactions and merchant categories deleted");
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> query(String category,
                                           Source source,
                                           LocalDate dateFrom,
                                           LocalDate dateTo,
                                           Pageable pageable) {
        Specification<Transaction> spec =
                TransactionSpecifications.withFilters(category, source, dateFrom, dateTo);
        Page<Transaction> page = transactionRepository.findAll(spec, pageable);

        List<String> merchantNames = page.getContent().stream()
                .map(Transaction::getMerchantName)
                .distinct()
                .toList();

        Map<String, MerchantCategory> categoriesByMerchant = merchantNames.isEmpty()
                ? Map.of()
                : merchantCategoryRepository.findByMerchantNameIn(merchantNames).stream()
                        .collect(Collectors.toMap(
                                MerchantCategory::getMerchantName,
                                Function.identity(),
                                (a, b) -> a));

        return page.map(tx ->
                TransactionMapper.toResponse(tx, categoriesByMerchant.get(tx.getMerchantName())));
    }

    private static String formatViolations(Set<ConstraintViolation<TransactionRequest>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
    }
}

