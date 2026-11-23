package com.example.ingestion.service;

import com.example.ingestion.config.CategorisationProperties;
import com.example.ingestion.entity.CategoryRule;
import com.example.ingestion.repository.MerchantCategoryRepository;
import com.example.ingestion.strategy.CategorisationResult;
import com.example.ingestion.strategy.FallbackStrategy;
import com.example.ingestion.strategy.RuleBasedStrategy;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Categorises a single merchant (rule-based first, then the optional LLM
 * fallback) and persists the result via an atomic upsert.
 */
@Service
public class MerchantCategoriser {

    private static final Logger log = LoggerFactory.getLogger(MerchantCategoriser.class);

    private final RuleBasedStrategy ruleBasedStrategy;
    private final FallbackStrategy fallbackStrategy;
    private final MerchantCategoryRepository merchantCategoryRepository;
    private final boolean fallbackEnabled;

    public MerchantCategoriser(RuleBasedStrategy ruleBasedStrategy,
                               FallbackStrategy fallbackStrategy,
                               MerchantCategoryRepository merchantCategoryRepository,
                               CategorisationProperties properties) {
        this.ruleBasedStrategy = ruleBasedStrategy;
        this.fallbackStrategy = fallbackStrategy;
        this.merchantCategoryRepository = merchantCategoryRepository;
        this.fallbackEnabled = properties.fallback().enabled();
    }

    /**
     * Resolve and upsert the category for one merchant using a pre-loaded set of
     * active rules. Returns {@code true} when a category was resolved.
     */
    @Transactional
    public boolean categorise(String merchantName, List<CategoryRule> activeRules) {
        return categorise(merchantName, activeRules, true);
    }

    /**
     * As {@link #categorise(String, List)}, but the LLM fallback is only used when
     * {@code allowFallback} is true (and it is enabled in configuration). Used by
     * re-categorisation to re-apply rules without re-querying the LLM.
     */
    @Transactional
    public boolean categorise(String merchantName, List<CategoryRule> activeRules, boolean allowFallback) {
        if (merchantName == null || merchantName.isBlank()) {
            log.debug("Skipping blank merchant name");
            return false;
        }

        Optional<CategorisationResult> result = ruleBasedStrategy.match(merchantName, activeRules);
        if (result.isEmpty() && fallbackEnabled && allowFallback) {
            result = fallbackStrategy.categorise(merchantName);
        }
        if (result.isEmpty()) {
            log.debug("No category resolved for '{}' (fallbackEnabled={})", merchantName, fallbackEnabled);
            return false;
        }

        CategorisationResult resolved = result.get();
        merchantCategoryRepository.upsert(
                merchantName,
                resolved.category(),
                resolved.subCategory(),
                resolved.confidenceScore(),
                resolved.strategyUsed().name());
        return true;
    }
}

