package com.example.ingestion.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void payloadTooLargeMapsTo413() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions/bulk");

        ResponseEntity<ApiError> response =
                handler.handlePayloadTooLarge(new PayloadTooLargeException("batch too big"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.status()).isEqualTo(413);
        assertThat(body.error()).isEqualTo("Payload Too Large");
        assertThat(body.message()).isEqualTo("batch too big");
        assertThat(body.path()).isEqualTo("/api/v1/transactions/bulk");
    }

    @Test
    void genericExceptionIsSafe500() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/analytics/summary");

        ResponseEntity<ApiError> response =
                handler.handleGeneric(new RuntimeException("boom with secrets"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // Internal detail must not leak to the client.
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}

