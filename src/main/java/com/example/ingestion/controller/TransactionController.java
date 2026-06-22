package com.example.ingestion.controller;

import com.example.ingestion.dto.request.TransactionRequest;
import com.example.ingestion.dto.response.BulkIngestResponse;
import com.example.ingestion.dto.response.PagedResponse;
import com.example.ingestion.dto.response.TransactionResponse;
import com.example.ingestion.entity.Source;
import com.example.ingestion.service.TransactionService;
import java.time.LocalDate;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Bulk-ingest a JSON array of transactions. Validation is per-row: valid
     * rows are persisted, invalid rows are reported in the response.
     */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.OK)
    public BulkIngestResponse bulkIngest(@RequestBody List<TransactionRequest> requests) {
        return transactionService.ingestBulk(requests);
    }

    /**
     * Paginated, filterable list of transactions (by category, source, date range).
     */
    @GetMapping
    public PagedResponse<TransactionResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Source source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @ParameterObject Pageable pageable) {
        return PagedResponse.from(
                transactionService.query(category, source, dateFrom, dateTo, pageable));
    }
}

