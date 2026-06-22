package com.example.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ingestion.config.CategorisationProperties;
import com.example.ingestion.entity.CategoryRule;
import com.example.ingestion.entity.StrategyUsed;
import com.example.ingestion.repository.MerchantCategoryRepository;
import com.example.ingestion.strategy.CategorisationResult;
import com.example.ingestion.strategy.FallbackStrategy;
import com.example.ingestion.strategy.RuleBasedStrategy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantCategoriserTest {

    private static final List<CategoryRule> RULES = List.of();

    @Mock
    private RuleBasedStrategy ruleBasedStrategy;
    @Mock
    private FallbackStrategy fallbackStrategy;
    @Mock
    private MerchantCategoryRepository merchantCategoryRepository;

    private MerchantCategoriser categoriser(boolean fallbackEnabled) {
        CategorisationProperties props = new CategorisationProperties(
                new CategorisationProperties.Fallback(fallbackEnabled), null, null, 1000);
        return new MerchantCategoriser(ruleBasedStrategy, fallbackStrategy, merchantCategoryRepository, props);
    }

    @Test
    void emptyMerchantName_isSkipped_withoutInvokingStrategiesOrUpsert() {
        boolean result = categoriser(true).categorise("   ", RULES);

        assertThat(result).isFalse();
        verifyNoInteractions(ruleBasedStrategy, fallbackStrategy, merchantCategoryRepository);
    }

    @Test
    void ruleMatch_upsertsRuleBased_andSkipsFallback() {
        when(ruleBasedStrategy.match("STARBUCKS NYC", RULES))
                .thenReturn(Optional.of(new CategorisationResult(
                        "Food & Drink", "Coffee", BigDecimal.ONE, StrategyUsed.RULE_BASED)));

        boolean result = categoriser(true).categorise("STARBUCKS NYC", RULES);

        assertThat(result).isTrue();
        verify(fallbackStrategy, never()).categorise(anyString());
        verify(merchantCategoryRepository)
                .upsert("STARBUCKS NYC", "Food & Drink", "Coffee", BigDecimal.ONE, "RULE_BASED");
    }

    @Test
    void noRuleMatch_withFallbackEnabled_upsertsLlmResult() {
        when(ruleBasedStrategy.match("ACME CORP", RULES)).thenReturn(Optional.empty());
        when(fallbackStrategy.categorise("ACME CORP"))
                .thenReturn(Optional.of(new CategorisationResult(
                        "Misc", "General", new BigDecimal("0.70"), StrategyUsed.FALLBACK_LLM)));

        boolean result = categoriser(true).categorise("ACME CORP", RULES);

        assertThat(result).isTrue();
        verify(merchantCategoryRepository)
                .upsert("ACME CORP", "Misc", "General", new BigDecimal("0.70"), "FALLBACK_LLM");
    }

    @Test
    void noRuleMatch_withFallbackDisabled_leavesMerchantUncategorised() {
        when(ruleBasedStrategy.match("ACME CORP", RULES)).thenReturn(Optional.empty());

        boolean result = categoriser(false).categorise("ACME CORP", RULES);

        assertThat(result).isFalse();
        verify(fallbackStrategy, never()).categorise(anyString());
        verify(merchantCategoryRepository, never())
                .upsert(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void usesProvidedRuleList_ratherThanQueryingPerMerchant() {
        List<CategoryRule> rules = List.of(CategoryRule.builder()
                .keyword("UBER").category("Transport").subCategory("Rideshare")
                .priority(20).isActive(true).build());
        when(ruleBasedStrategy.match(eq("UBER TRIP"), eq(rules)))
                .thenReturn(Optional.of(new CategorisationResult(
                        "Transport", "Rideshare", BigDecimal.ONE, StrategyUsed.RULE_BASED)));

        assertThat(categoriser(true).categorise("UBER TRIP", rules)).isTrue();
        verify(ruleBasedStrategy).match("UBER TRIP", rules);
    }
}

