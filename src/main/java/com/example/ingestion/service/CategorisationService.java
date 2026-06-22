package com.example.ingestion.service;

import com.example.ingestion.entity.CategoryRule;
import com.example.ingestion.repository.CategoryRuleRepository;
import com.example.ingestion.repository.TransactionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Categorises all uncategorised merchants asynchronously. The active rules are
 * loaded once per run and applied to each merchant in turn; the per-merchant
 * categorisation and persistence is delegated to {@link MerchantCategoriser}.
 */
@Service
public class CategorisationService {

    private static final Logger log = LoggerFactory.getLogger(CategorisationService.class);

    private final CategoryRuleRepository ruleRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantCategoriser merchantCategoriser;

    public CategorisationService(CategoryRuleRepository ruleRepository,
                                 TransactionRepository transactionRepository,
                                 MerchantCategoriser merchantCategoriser) {
        this.ruleRepository = ruleRepository;
        this.transactionRepository = transactionRepository;
        this.merchantCategoriser = merchantCategoriser;
    }

    /**
     * Asynchronously categorise every uncategorised merchant on the configurable
     * thread pool.
     */
    @Async("categorisationExecutor")
    public void triggerCategorisation() {
        List<CategoryRule> activeRules = ruleRepository.findByIsActiveTrueOrderByPriorityAsc();
        List<String> merchants = transactionRepository.findUncategorisedMerchantNames();
        log.info("Categorisation started for {} merchant(s) using {} active rule(s)",
                merchants.size(), activeRules.size());

        int categorised = 0;
        for (String merchant : merchants) {
            try {
                if (merchantCategoriser.categorise(merchant, activeRules)) {
                    categorised++;
                }
            } catch (Exception ex) {
                log.warn("Failed to categorise '{}': {}", merchant, ex.getMessage());
            }
        }

        log.info("Categorisation finished: {} categorised, {} skipped",
                categorised, merchants.size() - categorised);
    }
}

