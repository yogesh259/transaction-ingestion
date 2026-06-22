package com.example.ingestion.llm;

import com.example.ingestion.config.CategorisationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Default {@link LlmClient} backed by an OpenAI-style chat-completions API.
 * Sends a structured prompt, parses {@code { category, sub_category, confidence }}
 * from the response, and normalises the confidence to {@code [0,1]} with scale 2.
 * Any failure (missing key, timeout, malformed response) resolves to
 * {@link Optional#empty()}.
 */
@Component
public class RestClientLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientLlmClient.class);

    /** Confidence used when the model omits the field. */
    static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.50");

    private static final String SYSTEM_PROMPT = """
            You are a financial transaction categorisation assistant.
            Given a merchant name, respond ONLY with a compact JSON object using the keys
            "category", "sub_category" and "confidence" (a number between 0 and 1).
            Do not include any prose outside the JSON.""";

    private final CategorisationProperties.Llm properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RestClientLlmClient(CategorisationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties.llm();
        this.objectMapper = objectMapper;

        int timeoutMs = (int) Duration.ofSeconds(Math.max(1, this.properties.timeoutSeconds())).toMillis();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(this.properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Optional<LlmCategorisation> categorise(String merchantName) {
        if (merchantName == null || merchantName.isBlank()) {
            return Optional.empty();
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.debug("LLM api-key not configured; fallback skipped for '{}'", merchantName);
            return Optional.empty();
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(merchantName))
                    .retrieve()
                    .body(JsonNode.class);
            return parseChatCompletion(response, objectMapper);
        } catch (Exception ex) {
            log.warn("LLM fallback failed for '{}': {}", merchantName, ex.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> buildRequestBody(String merchantName) {
        return Map.of(
                "model", properties.model(),
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", "Categorise the merchant: " + merchantName)
                )
        );
    }

    /**
     * Extracts {@code { category, sub_category, confidence }} from an OpenAI-style
     * chat-completion response, or empty when the shape is unexpected or no
     * category is present.
     */
    static Optional<LlmCategorisation> parseChatCompletion(JsonNode response, ObjectMapper objectMapper) {
        if (response == null) {
            return Optional.empty();
        }
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            log.warn("Unexpected LLM response shape (no message content)");
            return Optional.empty();
        }
        try {
            JsonNode json = objectMapper.readTree(content.asText());
            String category = text(json, "category");
            if (category == null || category.isBlank()) {
                return Optional.empty();
            }
            BigDecimal confidence = json.hasNonNull("confidence")
                    ? clampConfidence(BigDecimal.valueOf(json.get("confidence").asDouble()))
                    : DEFAULT_CONFIDENCE;
            return Optional.of(new LlmCategorisation(category, text(json, "sub_category"), confidence));
        } catch (Exception ex) {
            log.warn("Failed to parse LLM JSON content: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** Normalise any model-provided confidence to {@code [0.00, 1.00]}. */
    static BigDecimal clampConfidence(BigDecimal raw) {
        if (raw == null) {
            return DEFAULT_CONFIDENCE;
        }
        return raw.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}

