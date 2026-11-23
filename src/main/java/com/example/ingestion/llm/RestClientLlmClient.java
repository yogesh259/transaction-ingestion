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
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * Default {@link LlmClient} backed by a Google Gemini API.
 * Sends a structured prompt, extracts the JSON object from the response,
 * and parses {@code { category, sub_category, confidence }}.
 * The client is resilient to the model wrapping the JSON in markdown or other text.
 * Any failure (missing key, timeout, malformed response) resolves to
 * {@link Optional#empty()}.
 */
@Component
public class RestClientLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientLlmClient.class);

    static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.50");
    private static final int MAX_ATTEMPTS = 4;
    private static final long RETRY_BASE_DELAY_MS = 2_000;

    private static final String PROMPT_TEMPLATE = """
            You are an expert financial transaction categorisation assistant. Your task is to categorise a merchant name into a relevant category and sub-category.

            Respond ONLY with a compact JSON object with three keys: "category", "sub_category", and "confidence".
            - "category": A broad category, like "Groceries", "Transport", or "Entertainment".
            - "sub_category": A more specific sub-category, like "Supermarket", "Ride-sharing", or "Movies".
            - "confidence": A number between 0.0 and 1.0 indicating your confidence in the categorisation.

            Prefer one of these categories when it fits: "Food & Drink", "Groceries", "Shopping", "Transport",
            "Travel", "Bills & Utilities", "Entertainment", "Health", "Investments", "Insurance", "Cash",
            "Transfers", "Education", "Personal Care". A merchant that looks like an individual person's name
            is a peer-to-peer payment: use "Transfers" / "Personal Transfer".

            Do not include any prose, markdown, or any text outside of the JSON object.

            Here are some examples:

            Merchant: "UBER TRIP"
            {
              "category": "Transport",
              "sub_category": "Ride-sharing",
              "confidence": 0.9
            }

            Merchant: "COLES SUPERMARKET"
            {
              "category": "Groceries",
              "sub_category": "Supermarket",
              "confidence": 0.95
            }

            Merchant: "NETFLIX"
            {
              "category": "Entertainment",
              "sub_category": "Streaming Services",
              "confidence": 1.0
            }

            Now, categorise the following merchant.

            Merchant: "%s"
            """;

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
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("LLM api-key not configured; fallback skipped for '{}'", merchantName);
            return Optional.empty();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                JsonNode response = restClient.post()
                        .uri("/models/{model}:generateContent", properties.model())
                        // Header rather than query string, so the key never lands in access logs.
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildRequestBody(merchantName))
                        .retrieve()
                        .body(JsonNode.class);
                return parseGeminiResponse(response, objectMapper);
            } catch (HttpStatusCodeException ex) {
                // Free-tier quotas answer 429 (and overloads 503): back off and retry a few times.
                boolean retryable = ex.getStatusCode().value() == 429 || ex.getStatusCode().value() == 503;
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    log.warn("LLM fallback failed for '{}' after {} attempt(s): HTTP {}",
                            merchantName, attempt, ex.getStatusCode().value());
                    return Optional.empty();
                }
                if (!sleep(RETRY_BASE_DELAY_MS * (1L << (attempt - 1)))) {
                    return Optional.empty();
                }
            } catch (Exception ex) {
                log.warn("LLM fallback failed for '{}': {}", merchantName, ex.getMessage());
                return Optional.empty();
            }
        }
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Map<String, Object> buildRequestBody(String merchantName) {
        return Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", String.format(PROMPT_TEMPLATE, merchantName))
                        ))
                )
        );
    }

    /**
     * Extracts {@code { category, sub_category, confidence }} from a Gemini
     * response, or empty when the shape is unexpected or no category is present.
     * This method is designed to be resilient to the model wrapping the JSON
     * in markdown or other surrounding text.
     */
    static Optional<LlmCategorisation> parseGeminiResponse(JsonNode response, ObjectMapper objectMapper) {
        if (response == null) {
            return Optional.empty();
        }
        JsonNode content = response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (!content.isTextual()) {
            log.warn("Unexpected LLM response shape (no text content)");
            return Optional.empty();
        }
        try {
            String textContent = content.asText();
            
            // The model sometimes wraps the JSON in markdown or other text, so we extract it.
            int firstBrace = textContent.indexOf('{');
            int lastBrace = textContent.lastIndexOf('}');
            if (firstBrace == -1 || lastBrace == -1 || lastBrace < firstBrace) {
                log.warn("Could not find a valid JSON object in the LLM response.");
                return Optional.empty();
            }
            String jsonSubstring = textContent.substring(firstBrace, lastBrace + 1);

            JsonNode json = objectMapper.readTree(jsonSubstring);
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