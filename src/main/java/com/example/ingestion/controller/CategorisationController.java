package com.example.ingestion.controller;

import com.example.ingestion.dto.response.TriggerResponse;
import com.example.ingestion.service.CategorisationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categorise")
public class CategorisationController {

    private final CategorisationService categorisationService;

    public CategorisationController(CategorisationService categorisationService) {
        this.categorisationService = categorisationService;
    }

    /**
     * Asynchronously trigger categorisation of all UNCATEGORISED merchants.
     * Returns 202 Accepted immediately; processing continues in the background.
     */
    @PostMapping("/trigger")
    public ResponseEntity<TriggerResponse> trigger() {
        categorisationService.triggerCategorisation();
        return ResponseEntity.accepted().body(new TriggerResponse(
                "ACCEPTED",
                "Categorisation of uncategorised merchants has been triggered."));
    }
}

