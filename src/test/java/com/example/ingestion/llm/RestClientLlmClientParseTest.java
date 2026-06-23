package com.example.ingestion.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RestClientLlmClientParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Build a Gemini-style envelope: {candidates:[{content:{parts:[{text:"<json>"}]}}]}. */
    private com.fasterxml.jackson.databind.JsonNode envelope(String textContent) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode part = root.putArray("candidates").addObject()
                .putObject("content")
                .putArray("parts").addObject();
        part.put("text", textContent);
        return root;
    }

    @Test
    void parsesValidContent() {
        Optional<LlmCategorisation> result = RestClientLlmClient.parseGeminiResponse(
                envelope("{\"category\":\"Shopping\",\"sub_category\":\"Online\",\"confidence\":0.83}"), mapper);

        assertThat(result).isPresent();
        assertThat(result.get().category()).isEqualTo("Shopping");
        assertThat(result.get().subCategory()).isEqualTo("Online");
        assertThat(result.get().confidence()).isEqualByComparingTo("0.83");
    }

    @Test
    void parsesValidContentWithMarkdown() {
        Optional<LlmCategorisation> result = RestClientLlmClient.parseGeminiResponse(
                envelope("```json\n{\"category\":\"Shopping\",\"sub_category\":\"Online\",\"confidence\":0.83}\n```"), mapper);

        assertThat(result).isPresent();
        assertThat(result.get().category()).isEqualTo("Shopping");
        assertThat(result.get().subCategory()).isEqualTo("Online");
        assertThat(result.get().confidence()).isEqualByComparingTo("0.83");
    }

    @Test
    void clampsOutOfRangeConfidence() {
        assertThat(RestClientLlmClient.parseGeminiResponse(
                envelope("{\"category\":\"X\",\"confidence\":1.5}"), mapper)
                .orElseThrow().confidence()).isEqualByComparingTo("1.00");

        assertThat(RestClientLlmClient.parseGeminiResponse(
                envelope("{\"category\":\"X\",\"confidence\":-0.2}"), mapper)
                .orElseThrow().confidence()).isEqualByComparingTo("0.00");
    }

    @Test
    void missingCategoryReturnsEmpty() {
        assertThat(RestClientLlmClient.parseGeminiResponse(
                envelope("{\"sub_category\":\"Online\",\"confidence\":0.83}"), mapper)).isEmpty();
    }

    @Test
    void missingSubCategoryIsTolerated() {
        Optional<LlmCategorisation> result = RestClientLlmClient.parseGeminiResponse(
                envelope("{\"category\":\"Shopping\",\"confidence\":0.83}"), mapper);
        assertThat(result).isPresent();
        assertThat(result.get().subCategory()).isNull();
    }

    @Test
    void missingConfidenceUsesDefault() {
        Optional<LlmCategorisation> result = RestClientLlmClient.parseGeminiResponse(
                envelope("{\"category\":\"Shopping\",\"sub_category\":\"Online\"}"), mapper);
        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isEqualTo(RestClientLlmClient.DEFAULT_CONFIDENCE);
    }

    @Test
    void malformedJsonReturnsEmpty() {
        assertThat(RestClientLlmClient.parseGeminiResponse(envelope("not json"), mapper)).isEmpty();
        assertThat(RestClientLlmClient.parseGeminiResponse(envelope("{\"category\":\"X"), mapper)).isEmpty();
    }

    @Test
    void emptyResponseReturnsEmpty() {
        assertThat(RestClientLlmClient.parseGeminiResponse(null, mapper)).isEmpty();
        assertThat(RestClientLlmClient.parseGeminiResponse(mapper.createObjectNode(), mapper)).isEmpty();
    }

    @Test
    void clampConfidenceRoundsToTwoDecimals() {
        assertThat(RestClientLlmClient.clampConfidence(new BigDecimal("0.8275"))).isEqualByComparingTo("0.83");
        assertThat(RestClientLlmClient.clampConfidence(new BigDecimal("0.451"))).isEqualByComparingTo("0.45");
        assertThat(RestClientLlmClient.clampConfidence(new BigDecimal("0.995"))).isEqualByComparingTo("1.00");
    }

    @Test
    void clampConfidenceHandlesNull() {
        assertThat(RestClientLlmClient.clampConfidence(null)).isEqualByComparingTo("0.50");
    }
}
