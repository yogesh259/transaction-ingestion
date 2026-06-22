package com.example.ingestion.controller;

import com.example.ingestion.dto.request.RuleUpsertRequest;
import com.example.ingestion.dto.response.RuleResponse;
import com.example.ingestion.service.RuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /**
     * Create or update a categorisation rule (admin endpoint). Upsert by keyword.
     */
    @PostMapping
    public RuleResponse upsert(@Valid @RequestBody RuleUpsertRequest request) {
        return ruleService.upsert(request);
    }
}

