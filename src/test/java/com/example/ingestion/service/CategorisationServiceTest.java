package com.example.ingestion.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ingestion.entity.CategoryRule;
import com.example.ingestion.repository.CategoryRuleRepository;
import com.example.ingestion.repository.TransactionRepository;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategorisationServiceTest {

    /** Runs the fan-out tasks inline so assertions are deterministic. */
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Mock
    private CategoryRuleRepository ruleRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MerchantCategoriser merchantCategoriser;

    private CategorisationService service;

    @BeforeEach
    void setUp() {
        service = new CategorisationService(
                ruleRepository, transactionRepository, merchantCategoriser, DIRECT_EXECUTOR);
    }

    @Test
    void trigger_loadsActiveRulesOnce_andCategorisesEachMerchantWithThem() {
        List<CategoryRule> rules = List.of(CategoryRule.builder()
                .keyword("UBER").category("Transport").priority(20).isActive(true).build());
        when(ruleRepository.findByIsActiveTrueOrderByPriorityAsc()).thenReturn(rules);
        when(transactionRepository.findUncategorisedMerchantNames())
                .thenReturn(List.of("UBER TRIP", "STARBUCKS NYC"));
        when(merchantCategoriser.categorise(anyString(), eq(rules))).thenReturn(true);

        service.triggerCategorisation();

        // rules fetched exactly once for the whole run (not per merchant)
        verify(ruleRepository, times(1)).findByIsActiveTrueOrderByPriorityAsc();
        verify(merchantCategoriser).categorise("UBER TRIP", rules);
        verify(merchantCategoriser).categorise("STARBUCKS NYC", rules);
    }

    @Test
    void trigger_continuesAfterAFailingMerchant() {
        List<CategoryRule> rules = List.of();
        when(ruleRepository.findByIsActiveTrueOrderByPriorityAsc()).thenReturn(rules);
        when(transactionRepository.findUncategorisedMerchantNames())
                .thenReturn(List.of("BOOM", "STARBUCKS NYC"));
        when(merchantCategoriser.categorise("BOOM", rules)).thenThrow(new RuntimeException("db down"));
        when(merchantCategoriser.categorise("STARBUCKS NYC", rules)).thenReturn(true);

        service.triggerCategorisation();

        // a failure on one merchant must not stop the rest of the run
        verify(merchantCategoriser).categorise("STARBUCKS NYC", rules);
    }

    @Test
    void trigger_withNoUncategorisedMerchants_doesNoPerMerchantWork() {
        when(ruleRepository.findByIsActiveTrueOrderByPriorityAsc()).thenReturn(List.of());
        when(transactionRepository.findUncategorisedMerchantNames()).thenReturn(List.of());

        service.triggerCategorisation();

        verify(merchantCategoriser, never()).categorise(anyString(), anyList());
    }
}

