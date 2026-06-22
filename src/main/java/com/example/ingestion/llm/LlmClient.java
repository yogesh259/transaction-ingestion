package com.example.ingestion.llm;

import java.util.Optional;

/**
 * Abstraction over the external LLM API so the fallback strategy is fully
 * testable without making real network calls.
 */
public interface LlmClient {

    Optional<LlmCategorisation> categorise(String merchantName);
}

