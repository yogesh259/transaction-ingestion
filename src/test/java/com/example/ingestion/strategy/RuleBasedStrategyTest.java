package com.example.ingestion.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ingestion.entity.CategoryRule;
import com.example.ingestion.entity.StrategyUsed;
import com.example.ingestion.repository.CategoryRuleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleBasedStrategyTest {

    @Mock
    private CategoryRuleRepository ruleRepository;

    @InjectMocks
    private RuleBasedStrategy strategy;

    private static CategoryRule rule(String keyword, String category, String sub, int priority) {
        return CategoryRule.builder()
                .keyword(keyword).category(category).subCategory(sub)
                .priority(priority).isActive(true).build();
    }

    @Test
    void matchesCaseInsensitiveSubstring() {
        when(ruleRepository.findByIsActiveTrueOrderByPriorityAsc())
                .thenReturn(List.of(rule("STARBUCKS", "Food & Drink", "Coffee", 10)));

        Optional<CategorisationResult> result = strategy.categorise("starbucks london #42");

        assertThat(result).isPresent();
        assertThat(result.get().category()).isEqualTo("Food & Drink");
        assertThat(result.get().strategyUsed()).isEqualTo(StrategyUsed.RULE_BASED);
        assertThat(result.get().confidenceScore()).isEqualByComparingTo("1.0");
    }

    @Test
    void respectsPriorityOrdering() {
        // Repository returns priority-ascending; "UBER EATS" (5) must win over "UBER" (20).
        when(ruleRepository.findByIsActiveTrueOrderByPriorityAsc())
                .thenReturn(List.of(
                        rule("UBER EATS", "Food & Drink", "Delivery", 5),
                        rule("UBER", "Transport", "Rideshare", 20)));

        Optional<CategorisationResult> result = strategy.categorise("UBER EATS LONDON");

        assertThat(result).isPresent();
        assertThat(result.get().category()).isEqualTo("Food & Drink");
        assertThat(result.get().subCategory()).isEqualTo("Delivery");
    }

    @Test
    void returnsEmptyWhenNoRuleMatches() {
        when(ruleRepository.findByIsActiveTrueOrderByPriorityAsc())
                .thenReturn(List.of(rule("STARBUCKS", "Food & Drink", "Coffee", 10)));

        assertThat(strategy.categorise("ACME CORP")).isEmpty();
    }

    @Test
    void blankMerchantNameShortCircuits() {
        assertThat(strategy.categorise("  ")).isEmpty();
        verifyNoInteractions(ruleRepository);
    }

    @Test
    void match_usesPreloadedRules_withoutQueryingRepository() {
        List<CategoryRule> rules = List.of(rule("AMAZON", "Shopping", "Online", 30));

        Optional<CategorisationResult> result = strategy.match("AMAZON MKTP US", rules);

        assertThat(result).isPresent();
        assertThat(result.get().category()).isEqualTo("Shopping");
        verifyNoInteractions(ruleRepository);
    }
}

