package com.example.ingestion.service;

import com.example.ingestion.dto.request.RuleUpsertRequest;
import com.example.ingestion.dto.response.RuleResponse;
import com.example.ingestion.entity.CategoryRule;
import com.example.ingestion.repository.CategoryRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin operations for categorisation rules. Upserts by keyword
 * (case-insensitive): an existing rule with the same keyword is updated,
 * otherwise a new rule is created.
 */
@Service
public class RuleService {

    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    private final CategoryRuleRepository ruleRepository;

    public RuleService(CategoryRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Transactional
    public RuleResponse upsert(RuleUpsertRequest request) {
        CategoryRule rule = ruleRepository.findByKeywordIgnoreCase(request.keyword())
                .orElseGet(CategoryRule::new);
        boolean isNew = rule.getId() == null;

        rule.setKeyword(request.keyword());
        rule.setCategory(request.category());
        rule.setSubCategory(request.subCategory());
        rule.setPriority(request.priority());
        rule.setIsActive(request.isActive());

        CategoryRule saved = ruleRepository.save(rule);
        log.info("{} rule id={} keyword='{}'", isNew ? "Created" : "Updated", saved.getId(), saved.getKeyword());

        return new RuleResponse(
                saved.getId(),
                saved.getKeyword(),
                saved.getCategory(),
                saved.getSubCategory(),
                saved.getPriority(),
                saved.getIsActive());
    }
}

