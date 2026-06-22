package com.example.ingestion.dto.response;

import java.util.List;

/**
 * Summary returned by the bulk ingest endpoint. Valid rows are accepted and
 * persisted; invalid rows are reported with their index and reason.
 */
public record BulkIngestResponse(
        int acceptedCount,
        int rejectedCount,
        List<RejectedItem> errors
) {
    public record RejectedItem(int index, String reason) {
    }
}

