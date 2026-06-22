package com.example.ingestion.exception;

import java.time.Instant;

/**
 * Structured error body returned by {@link GlobalExceptionHandler}:
 * {@code { timestamp, status, error, message, path }}.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}

