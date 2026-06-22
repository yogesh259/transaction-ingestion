package com.example.ingestion.strategy;

import com.example.ingestion.entity.CategoryRule;
import com.example.ingestion.entity.StrategyUsed;
import com.example.ingestion.repository.CategoryRuleRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Matches a merchant name against the active {@code category_rules} by keyword
 * (case-insensitive substring match) in priority order, lowest priority value
 * first. A rule match is deterministic, so the confidence score is 1.0.
 */
@Component
public class RuleBasedStrategy implements CategorisationStrategy {

    private final CategoryRuleRepository ruleRepository;

    public RuleBasedStrategy(CategoryRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    public Optional<CategorisationResult> categorise(String merchantName) {
        if (merchantName == null || merchantName.isBlank()) {
            return Optional.empty();
        }
        return match(merchantName, ruleRepository.findByIsActiveTrueOrderByPriorityAsc());
    }

    /**
     * Matches a merchant name against a pre-loaded, priority-ascending list of
     * active rules, returning the first matching rule's category.
     */
    public Optional<CategorisationResult> match(String merchantName, List<CategoryRule> activeRules) {
        if (merchantName == null || merchantName.isBlank() || activeRules == null) {
            return Optional.empty();
        }
        String haystack = merchantName.toUpperCase(Locale.ROOT);
        return activeRules.stream()
                .filter(rule -> haystack.contains(rule.getKeyword().toUpperCase(Locale.ROOT)))
                .findFirst()
                .map(rule -> new CategorisationResult(
                        rule.getCategory(),
                        rule.getSubCategory(),
                        BigDecimal.ONE,
                        StrategyUsed.RULE_BASED));
    }
}

