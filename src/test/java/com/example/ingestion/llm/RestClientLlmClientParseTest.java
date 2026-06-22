package com.example.ingestion.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RestClientLlmClientParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Build an OpenAI-style envelope: {choices:[{message:{content:"<json>"}}]}. */
    private com.fasterxml.jackson.databind.JsonNode envelope(String contentJson) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode message = root.putArray("choices").addObject().putObject("message");
        message.put("content", contentJson);
        return root;
    }

    @Test
    void parsesValidContent() {
        Optional<LlmCategorisation> result = RestClientLlmClient.parseChatCompletion(
                envelope("{\"category\":\"Shopping\",\"sub_category\":\"Online\",\"confidence\":0.83}"), mapper);

        assertThat(result).isPresent();
        assertThat(result.get().category()).isEqualTo("Shopping");
        assertThat(result.get().subCategory()).isEqualTo("Online");
        assertThat(result.get().confidence()).isEqualByComparingTo("0.83");
    }

    @Test
    void clampsOutOfRangeConfidence() {
        assertThat(RestClientLlmClient.parseChatCompletion(
                envelope("{\"category\":\"X\",\"confidence\":1.5}"), mapper)
                .orElseThrow().confidence()).isEqualByComparingTo("1.00");

        assertThat(RestClientLlmClient.parseChatCompletion(
                envelope("{\"category\":\"X\",\"confidence\":-0.2}"), mapper)
                .orElseThrow().confidence()).isEqualByComparingTo("0.00");
    }

    @Test
    void missingConfidenceUsesDefault() {
        assertThat(RestClientLlmClient.parseChatCompletion(
                envelope("{\"category\":\"X\"}"), mapper)
                .orElseThrow().confidence()).isEqualByComparingTo("0.50");
    }

    @Test
    void missingCategoryReturnsEmpty() {
        assertThat(RestClientLlmClient.parseChatCompletion(
                envelope("{\"sub_category\":\"Online\"}"), mapper)).isEmpty();
    }

    @Test
    void malformedContentJsonReturnsEmpty() {
        assertThat(RestClientLlmClient.parseChatCompletion(envelope("not-json"), mapper)).isEmpty();
    }

    @Test
    void missingMessageContentReturnsEmpty() {
        assertThat(RestClientLlmClient.parseChatCompletion(mapper.createObjectNode(), mapper)).isEmpty();
        assertThat(RestClientLlmClient.parseChatCompletion(null, mapper)).isEmpty();
    }

    @Test
    void clampConfidenceRoundsToTwoDecimals() {
        assertThat(RestClientLlmClient.clampConfidence(new BigDecimal("0.8275"))).isEqualByComparingTo("0.83");
        assertThat(RestClientLlmClient.clampConfidence(null)).isEqualByComparingTo("0.50");
    }
}

