package com.example.ingestion.exception;

/**
 * Thrown when a request payload exceeds an allowed bound (e.g. bulk batch size).
 * Mapped to HTTP 413 Payload Too Large.
 */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}

