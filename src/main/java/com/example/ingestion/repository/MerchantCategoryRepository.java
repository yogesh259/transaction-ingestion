package com.example.ingestion.repository;

import com.example.ingestion.entity.MerchantCategory;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, Long> {

    List<MerchantCategory> findByMerchantNameIn(Collection<String> merchantNames);

    /**
     * Inserts a category for a merchant, or updates the existing row, in a single
     * atomic statement keyed on the unique {@code merchant_name}. Uses VALUES()
     * rather than the MySQL 8.0.19+ row alias so it also runs on TiDB.
     */
    @Modifying
    @Query(value = """
            INSERT INTO merchant_categories
                (merchant_name, category, sub_category, confidence_score, categorised_at, strategy_used)
            VALUES (:merchantName, :category, :subCategory, :confidence, CURRENT_TIMESTAMP(6), :strategyUsed)
            ON DUPLICATE KEY UPDATE
                category = VALUES(category),
                sub_category = VALUES(sub_category),
                confidence_score = VALUES(confidence_score),
                categorised_at = CURRENT_TIMESTAMP(6),
                strategy_used = VALUES(strategy_used)
            """, nativeQuery = true)
    void upsert(@Param("merchantName") String merchantName,
                @Param("category") String category,
                @Param("subCategory") String subCategory,
                @Param("confidence") BigDecimal confidence,
                @Param("strategyUsed") String strategyUsed);
}

