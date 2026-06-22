package com.example.ingestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The resolved category for a merchant. Linked to {@link Transaction} logically
 * by {@code merchant_name} (no FK). One categorisation per merchant (unique).
 */
@Entity
@Table(name = "merchant_categories", uniqueConstraints = {
        @UniqueConstraint(name = "uq_mc_merchant_name", columnNames = "merchant_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_name", nullable = false)
    private String merchantName;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "sub_category", length = 100)
    private String subCategory;

    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "categorised_at")
    private Instant categorisedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_used", length = 20)
    private StrategyUsed strategyUsed;
}

