package com.example.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ingestion.dto.request.RuleUpsertRequest;
import com.example.ingestion.dto.response.RuleResponse;
import com.example.ingestion.entity.CategoryRule;
import com.example.ingestion.repository.CategoryRuleRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock
    private CategoryRuleRepository ruleRepository;

    @InjectMocks
    private RuleService ruleService;

    @Test
    void createsNewRuleWhenKeywordAbsent() {
        when(ruleRepository.findByKeywordIgnoreCase("TESCO")).thenReturn(Optional.empty());
        when(ruleRepository.save(any(CategoryRule.class))).thenAnswer(invocation -> {
            CategoryRule saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        RuleResponse response = ruleService.upsert(
                new RuleUpsertRequest("TESCO", "Shopping", "Groceries", 25, true));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.keyword()).isEqualTo("TESCO");
        assertThat(response.category()).isEqualTo("Shopping");
        assertThat(response.priority()).isEqualTo(25);
    }

    @Test
    void updatesExistingRuleByCaseInsensitiveKeyword() {
        CategoryRule existing = CategoryRule.builder()
                .id(3L).keyword("TESCO").category("Old").priority(99).isActive(true).build();
        when(ruleRepository.findByKeywordIgnoreCase("tesco")).thenReturn(Optional.of(existing));
        when(ruleRepository.save(any(CategoryRule.class))).thenAnswer(i -> i.getArgument(0));

        RuleResponse response = ruleService.upsert(
                new RuleUpsertRequest("tesco", "Shopping", "Groceries", 10, false));

        assertThat(response.id()).isEqualTo(3L); // updated existing row, not a new one
        assertThat(response.category()).isEqualTo("Shopping");
        assertThat(response.isActive()).isFalse();

        ArgumentCaptor<CategoryRule> captor = ArgumentCaptor.forClass(CategoryRule.class);
        verify(ruleRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(3L);
        assertThat(captor.getValue().getPriority()).isEqualTo(10);
    }
}

