package com.example.ingestion.strategy;

import com.example.ingestion.entity.StrategyUsed;
import com.example.ingestion.llm.LlmClient;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Fallback strategy for merchants with no rule match: delegates to the LLM
 * client and maps its structured response to a {@link CategorisationResult}.
 */
@Component
public class FallbackStrategy implements CategorisationStrategy {

    private final LlmClient llmClient;

    public FallbackStrategy(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public Optional<CategorisationResult> categorise(String merchantName) {
        if (merchantName == null || merchantName.isBlank()) {
            return Optional.empty();
        }
        return llmClient.categorise(merchantName)
                .map(llm -> new CategorisationResult(
                        llm.category(),
                        llm.subCategory(),
                        llm.confidence(),
                        StrategyUsed.FALLBACK_LLM));
    }
}

