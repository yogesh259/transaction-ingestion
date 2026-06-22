package com.example.ingestion.spec;

import com.example.ingestion.entity.MerchantCategory;
import com.example.ingestion.entity.Source;
import com.example.ingestion.entity.Transaction;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable filters for querying {@link Transaction}. Category filtering joins
 * {@code merchant_categories} logically via a {@code merchant_name} subquery
 * (the two tables have no FK relationship).
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> withFilters(String category,
                                                         Source source,
                                                         LocalDate dateFrom,
                                                         LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), dateTo));
            }
            if (category != null && !category.isBlank() && query != null) {
                Subquery<String> sub = query.subquery(String.class);
                Root<MerchantCategory> mc = sub.from(MerchantCategory.class);
                sub.select(mc.get("merchantName"))
                        .where(cb.equal(mc.get("category"), category));
                predicates.add(root.get("merchantName").in(sub));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

