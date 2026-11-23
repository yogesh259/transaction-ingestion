package com.example.ingestion.service;

import com.example.ingestion.entity.CategoryRule;
import com.example.ingestion.repository.CategoryRuleRepository;
import com.example.ingestion.repository.TransactionRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Categorises all uncategorised merchants asynchronously. Active rules are loaded
 * once per run, then each merchant is categorised in parallel on the configurable
 * thread pool; the per-merchant categorisation and persistence is delegated to
 * {@link MerchantCategoriser}.
 */
@Service
public class CategorisationService {

    private static final Logger log = LoggerFactory.getLogger(CategorisationService.class);

    private final CategoryRuleRepository ruleRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantCategoriser merchantCategoriser;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CategorisationService(CategoryRuleRepository ruleRepository,
                                 TransactionRepository transactionRepository,
                                 MerchantCategoriser merchantCategoriser,
                                 @Qualifier("categorisationExecutor") Executor executor) {
        this.ruleRepository = ruleRepository;
        this.transactionRepository = transactionRepository;
        this.merchantCategoriser = merchantCategoriser;
        this.executor = executor;
    }

    /**
     * Asynchronously categorise every uncategorised merchant, fanning the
     * per-merchant work out across the configurable thread pool.
     */
    @Async("categorisationExecutor")
    public void triggerCategorisation() {
        run(false);
    }

    /**
     * Asynchronously categorise merchants. With {@code recategorise=true} the
     * active rules are re-applied to every merchant (so a newly added rule can
     * override an earlier result); the LLM is still only called for merchants
     * that have no category yet.
     */
    @Async("categorisationExecutor")
    public void triggerCategorisation(boolean recategorise) {
        run(recategorise);
    }

    private void run(boolean recategorise) {
        if (!running.compareAndSet(false, true)) {
            log.info("Categorisation already running; trigger ignored");
            return;
        }
        try {
            if (recategorise) {
                runRecategorisation();
            } else {
                runCategorisation();
            }
        } finally {
            running.set(false);
        }
    }

    /** Whether a categorisation run is in progress, and how many merchants still lack a category. */
    public CategorisationStatus status() {
        return new CategorisationStatus(
                running.get(),
                transactionRepository.countDistinctMerchants(),
                transactionRepository.countUncategorisedMerchants());
    }

    public record CategorisationStatus(boolean running, long totalMerchants, long uncategorisedMerchants) {
    }

    private void runCategorisation() {
        List<CategoryRule> activeRules = ruleRepository.findByIsActiveTrueOrderByPriorityAsc();
        List<String> merchants = transactionRepository.findUncategorisedMerchantNames();
        log.info("Categorisation started for {} merchant(s) using {} active rule(s)",
                merchants.size(), activeRules.size());

        List<CompletableFuture<Boolean>> futures = merchants.stream()
                .map(merchant -> CompletableFuture.supplyAsync(
                        () -> safelyCategorise(merchant, activeRules), executor))
                .toList();

        long categorised = futures.stream()
                .map(CompletableFuture::join)
                .filter(Boolean::booleanValue)
                .count();

        log.info("Categorisation finished: {} categorised, {} skipped",
                categorised, merchants.size() - categorised);
    }

    private void runRecategorisation() {
        List<CategoryRule> activeRules = ruleRepository.findByIsActiveTrueOrderByPriorityAsc();
        Set<String> uncategorised = new HashSet<>(transactionRepository.findUncategorisedMerchantNames());
        List<String> merchants = transactionRepository.findDistinctMerchantNames();
        log.info("Re-categorisation started for {} merchant(s) ({} uncategorised) using {} active rule(s)",
                merchants.size(), uncategorised.size(), activeRules.size());

        long updated = merchants.stream()
                .map(merchant -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return merchantCategoriser.categorise(
                                merchant, activeRules, uncategorised.contains(merchant));
                    } catch (Exception ex) {
                        log.warn("Failed to re-categorise '{}': {}", merchant, ex.getMessage());
                        return false;
                    }
                }, executor))
                .toList().stream()
                .map(CompletableFuture::join)
                .filter(Boolean::booleanValue)
                .count();
        log.info("Re-categorisation finished: {} merchant(s) updated", updated);
    }

    private boolean safelyCategorise(String merchant, List<CategoryRule> activeRules) {
        try {
            return merchantCategoriser.categorise(merchant, activeRules);
        } catch (Exception ex) {
            log.warn("Failed to categorise '{}': {}", merchant, ex.getMessage());
            return false;
        }
    }
}

