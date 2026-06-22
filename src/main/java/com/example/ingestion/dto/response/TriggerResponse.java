package com.example.ingestion.dto.response;

/**
 * Acknowledgement returned (202) when async categorisation is triggered.
 */
public record TriggerResponse(String status, String message) {
}

