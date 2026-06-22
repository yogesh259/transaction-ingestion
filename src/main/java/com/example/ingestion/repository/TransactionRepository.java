package com.example.ingestion.repository;

import com.example.ingestion.entity.Transaction;
import com.example.ingestion.repository.projection.CategorySpendProjection;
import com.example.ingestion.repository.projection.MerchantSpendProjection;
import com.example.ingestion.repository.projection.MonthlySpendProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /**
     * Distinct merchant names that appear in transactions but have no row in
     * merchant_categories yet (i.e. still UNCATEGORISED).
     */
    @Query(value = """
            SELECT DISTINCT t.merchant_name
            FROM transactions t
            LEFT JOIN merchant_categories mc ON t.merchant_name = mc.merchant_name
            WHERE mc.id IS NULL
            """, nativeQuery = true)
    List<String> findUncategorisedMerchantNames();

    /**
     * Total spend grouped by resolved category, with an explicit
     * {@code "Uncategorised"} bucket for merchants that have no category yet.
     */
    @Query(value = """
            SELECT COALESCE(mc.category, 'Uncategorised') AS category, SUM(t.amount) AS totalSpend
            FROM transactions t
            LEFT JOIN merchant_categories mc ON t.merchant_name = mc.merchant_name
            GROUP BY COALESCE(mc.category, 'Uncategorised')
            ORDER BY totalSpend DESC
            """, nativeQuery = true)
    List<CategorySpendProjection> totalSpendByCategory();

    /**
     * Top 10 merchants by total spend.
     */
    @Query(value = """
            SELECT t.merchant_name AS merchantName, SUM(t.amount) AS totalSpend
            FROM transactions t
            GROUP BY t.merchant_name
            ORDER BY totalSpend DESC
            LIMIT 10
            """, nativeQuery = true)
    List<MerchantSpendProjection> topMerchantsBySpend();

    /**
     * Total spend grouped by calendar year and month, ordered chronologically.
     */
    @Query(value = """
            SELECT YEAR(t.transaction_date) AS tx_year, MONTH(t.transaction_date) AS tx_month,
                   SUM(t.amount) AS totalSpend
            FROM transactions t
            GROUP BY YEAR(t.transaction_date), MONTH(t.transaction_date)
            ORDER BY tx_year, tx_month
            """, nativeQuery = true)
    List<MonthlySpendProjection> monthlySpend();
}

