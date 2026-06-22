package com.example.ingestion.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ingestion.entity.StrategyUsed;
import com.example.ingestion.llm.LlmCategorisation;
import com.example.ingestion.llm.LlmClient;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FallbackStrategyTest {

    @Mock
    private LlmClient llmClient;

    @InjectMocks
    private FallbackStrategy strategy;

    @Test
    void mapsLlmResponseToFallbackResult() {
        when(llmClient.categorise("ACME CORP"))
                .thenReturn(Optional.of(new LlmCategorisation("Shopping", "Online", BigDecimal.valueOf(0.82))));

        Optional<CategorisationResult> result = strategy.categorise("ACME CORP");

        assertThat(result).isPresent();
        assertThat(result.get().category()).isEqualTo("Shopping");
        assertThat(result.get().subCategory()).isEqualTo("Online");
        assertThat(result.get().strategyUsed()).isEqualTo(StrategyUsed.FALLBACK_LLM);
        assertThat(result.get().confidenceScore()).isEqualByComparingTo("0.82");
    }

    @Test
    void returnsEmptyWhenLlmHasNoAnswer() {
        when(llmClient.categorise("ACME CORP")).thenReturn(Optional.empty());

        assertThat(strategy.categorise("ACME CORP")).isEmpty();
    }

    @Test
    void blankMerchantNameShortCircuits() {
        assertThat(strategy.categorise("")).isEmpty();
        verifyNoInteractions(llmClient);
    }
}

