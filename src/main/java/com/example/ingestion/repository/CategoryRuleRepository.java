package com.example.ingestion.repository;

import com.example.ingestion.entity.CategoryRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    /**
     * Active rules ordered by priority ascending (lower value = higher priority).
     */
    List<CategoryRule> findByIsActiveTrueOrderByPriorityAsc();

    Optional<CategoryRule> findByKeywordIgnoreCase(String keyword);
}

