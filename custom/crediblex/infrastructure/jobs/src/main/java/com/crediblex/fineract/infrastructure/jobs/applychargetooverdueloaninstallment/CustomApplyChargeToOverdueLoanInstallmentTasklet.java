/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.infrastructure.jobs.applychargetooverdueloaninstallment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Custom implementation of ApplyChargeToOverdueLoanInstallmentTasklet with: - Sorted processing (TreeMap by loan ID) to
 * prevent deadlocks - Deadlock retry logic with exponential backoff - Batch processing for better performance and error
 * isolation - Enhanced logging and metrics
 */
@Slf4j
@RequiredArgsConstructor
public class CustomApplyChargeToOverdueLoanInstallmentTasklet implements Tasklet {

    private final ConfigurationDomainService configurationDomainService;
    private final LoanReadPlatformService loanReadPlatformService;
    private final LoanChargeWritePlatformService loanChargeWritePlatformService;
    private final PlatformTransactionManager transactionManager;
    private final PenaltyJobProperties penaltyJobProperties;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        final long startTime = System.currentTimeMillis();
        log.info("Starting custom penalty job execution");

        final Long penaltyWaitPeriodValue = configurationDomainService.retrievePenaltyWaitPeriod();
        final Boolean backdatePenalties = configurationDomainService.isBackdatePenaltiesEnabled();
        final Collection<OverdueLoanScheduleData> overdueLoanScheduledInstallments = loanReadPlatformService
                .retrieveAllLoansWithOverdueInstallments(penaltyWaitPeriodValue, backdatePenalties);

        if (overdueLoanScheduledInstallments.isEmpty()) {
            log.info("No overdue loan installments found to process");
            return RepeatStatus.FINISHED;
        }

        log.info("Found {} overdue loan installments to process", overdueLoanScheduledInstallments.size());

        // Use TreeMap to sort loans by ID - prevents deadlocks and improves cache locality
        final Map<Long, Collection<OverdueLoanScheduleData>> overdueScheduleData = new TreeMap<>();
        for (final OverdueLoanScheduleData overdueInstallment : overdueLoanScheduledInstallments) {
            overdueScheduleData.computeIfAbsent(overdueInstallment.getLoanId(), k -> new ArrayList<>()).add(overdueInstallment);
        }

        final int totalLoans = overdueScheduleData.size();
        log.info("Processing {} unique loans with overdue installments (sorted by loan ID)", totalLoans);

        final List<Throwable> exceptions = new ArrayList<>();
        int processedCount = 0;
        int successCount = 0;
        int failureCount = 0;

        if (penaltyJobProperties.isEnableBatchProcessing() && penaltyJobProperties.getBatchSize() > 0) {
            // Process loans in batches for better performance and error isolation
            processedCount = processLoansInBatches(overdueScheduleData, exceptions, startTime, totalLoans);
        } else {
            // Process all loans sequentially (fallback mode)
            processedCount = processLoansSequentially(overdueScheduleData, exceptions);
        }

        successCount = processedCount - exceptions.size();
        failureCount = exceptions.size();

        final long executionTime = System.currentTimeMillis() - startTime;
        log.info("Penalty job execution completed - Total: {}, Success: {}, Failed: {}, Time: {}ms ({}s)", totalLoans, successCount,
                failureCount, executionTime, executionTime / 1000.0);

        if (!exceptions.isEmpty()) {
            log.error("Penalty job completed with {} failures out of {} loans processed", failureCount, processedCount);
            throw new JobExecutionException(exceptions);
        }

        return RepeatStatus.FINISHED;
    }

    /**
     * Process loans in batches for better performance and error isolation
     */
    private int processLoansInBatches(Map<Long, Collection<OverdueLoanScheduleData>> overdueScheduleData, List<Throwable> exceptions,
            long startTime, int totalLoans) {
        int processedCount = 0;
        int batchNumber = 0;
        final int batchSize = penaltyJobProperties.getBatchSize();
        final List<Map.Entry<Long, Collection<OverdueLoanScheduleData>>> loanEntries = new ArrayList<>(overdueScheduleData.entrySet());

        for (int i = 0; i < loanEntries.size(); i += batchSize) {
            batchNumber++;
            final int endIndex = Math.min(i + batchSize, loanEntries.size());
            final List<Map.Entry<Long, Collection<OverdueLoanScheduleData>>> batch = loanEntries.subList(i, endIndex);

            final long batchStartTime = System.currentTimeMillis();
            log.info("Processing batch {}: loans {} to {} ({} loans)", batchNumber, i + 1, endIndex, batch.size());

            try {
                processBatch(batch, exceptions);
                processedCount += batch.size();

                final long batchTime = System.currentTimeMillis() - batchStartTime;
                final double avgTimePerLoan = batchTime / (double) batch.size();
                final int remainingLoans = totalLoans - processedCount;
                final double estimatedRemainingTime = remainingLoans * avgTimePerLoan;

                log.info("Batch {} completed: {} loans in {}ms (avg {}ms/loan). Progress: {}/{} ({}%). Estimated remaining time: {}s",
                        batchNumber, batch.size(), batchTime, String.format("%.1f", avgTimePerLoan), processedCount, totalLoans,
                        String.format("%.1f", (processedCount * 100.0 / totalLoans)),
                        String.format("%.1f", estimatedRemainingTime / 1000.0));

            } catch (Exception e) {
                log.error("Error processing batch {}", batchNumber, e);
                // Continue processing next batch even if current batch fails
                // Individual loan failures are already captured in exceptions list
            }
        }

        return processedCount;
    }

    /**
     * Process a batch of loans in a separate transaction
     */
    private void processBatch(List<Map.Entry<Long, Collection<OverdueLoanScheduleData>>> batch, List<Throwable> exceptions) {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        transactionTemplate.executeWithoutResult(status -> {
            for (Map.Entry<Long, Collection<OverdueLoanScheduleData>> entry : batch) {
                processLoanWithRetry(entry.getKey(), entry.getValue(), exceptions);
            }
        });
    }

    /**
     * Process loans sequentially (fallback mode when batch processing is disabled)
     */
    private int processLoansSequentially(Map<Long, Collection<OverdueLoanScheduleData>> overdueScheduleData, List<Throwable> exceptions) {
        int processedCount = 0;
        for (Map.Entry<Long, Collection<OverdueLoanScheduleData>> entry : overdueScheduleData.entrySet()) {
            processLoanWithRetry(entry.getKey(), entry.getValue(), exceptions);
            processedCount++;
        }
        return processedCount;
    }

    /**
     * Process a single loan with deadlock retry logic
     */
    private void processLoanWithRetry(Long loanId, Collection<OverdueLoanScheduleData> overdueLoanScheduleDataList,
            List<Throwable> exceptions) {
        int attempt = 0;
        Exception lastException = null;
        final int maxRetries = penaltyJobProperties.getMaxRetries();

        while (attempt <= maxRetries) {
            try {
                if (!overdueLoanScheduleDataList.isEmpty()) {
                    loanChargeWritePlatformService.applyOverdueChargesForLoan(loanId, overdueLoanScheduleDataList);
                }
                // Success - return
                if (attempt > 0) {
                    log.info("Successfully processed loan {} after {} retry attempts", loanId, attempt);
                }
                return;

            } catch (final PlatformApiDataValidationException e) {
                // Don't retry validation errors
                final List<ApiParameterError> errors = e.getErrors();
                for (final ApiParameterError error : errors) {
                    log.error("Apply Charges due for overdue loans failed for account {} with message: {}", loanId,
                            error.getDeveloperMessage(), e);
                }
                exceptions.add(e);
                return;

            } catch (final AbstractPlatformDomainRuleException e) {
                // Don't retry business rule exceptions
                log.error("Apply Charges due for overdue loans failed for account {} with message: {}", loanId, e.getDefaultUserMessage(),
                        e);
                exceptions.add(e);
                return;

            } catch (Exception e) {
                lastException = e;

                // Check if this is a deadlock exception
                if (isDeadlockException(e)) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        final long delay = calculateRetryDelay(attempt);
                        log.warn("Deadlock detected for loan {} (attempt {}/{}) - retrying after {}ms", loanId, attempt, maxRetries, delay);
                        try {
                            // Use CompletableFuture with delayed executor for more efficient resource usage
                            // This uses the ForkJoinPool instead of directly blocking the thread
                            CompletableFuture.runAsync(() -> {
                                // Empty runnable - we just need the delay
                            }, CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS)).get();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.error("Retry delay interrupted for loan {}", loanId);
                            exceptions.add(new RuntimeException("Retry interrupted", ie));
                            return;
                        } catch (java.util.concurrent.ExecutionException ee) {
                            log.error("Error during retry delay for loan {}", loanId, ee);
                            exceptions.add(e);
                            return;
                        }
                    } else {
                        log.error("Failed to process loan {} after {} retry attempts due to deadlock", loanId, maxRetries);
                        exceptions.add(e);
                        return;
                    }
                } else {
                    // Not a deadlock - don't retry
                    log.error("Apply Charges due for overdue loans failed for account {} with non-retryable error", loanId, e);
                    exceptions.add(e);
                    return;
                }
            }
        }

        // Should not reach here, but handle it just in case
        if (lastException != null) {
            log.error("Failed to process loan {} after all retry attempts", loanId, lastException);
            exceptions.add(lastException);
        }
    }

    /**
     * Check if an exception is a deadlock exception.
     *
     * Detects deadlocks by: 1. Checking for Spring's DeadlockLoserDataAccessException (most reliable) 2. Recursively
     * checking exception chain for "deadlock detected" message 3. Checking the exception message itself
     */
    private boolean isDeadlockException(Exception e) {
        // Check for Spring's deadlock wrapper (most reliable indicator)
        if (e instanceof DeadlockLoserDataAccessException) {
            return true;
        }

        // Check exception chain recursively for deadlock indicators
        Throwable cause = e.getCause();
        while (cause != null) {
            // Check for Spring's deadlock wrapper in the chain
            if (cause instanceof DeadlockLoserDataAccessException) {
                return true;
            }
            // Check message for "deadlock detected" using case-insensitive matching without creating temporary strings
            final String message = cause.getMessage();
            if (message != null && containsIgnoreCase(message, "deadlock detected")) {
                return true;
            }
            cause = cause.getCause();
        }

        // Check exception message itself
        final String exceptionMessage = e.getMessage();
        return exceptionMessage != null && containsIgnoreCase(exceptionMessage, "deadlock detected");
    }

    /**
     * Case-insensitive string contains check using regionMatches to avoid creating temporary lowercase strings. More
     * efficient than toLowerCase().contains() for potentially large strings.
     */
    private boolean containsIgnoreCase(String text, String searchString) {
        if (text == null || searchString == null) {
            return false;
        }
        final int searchLength = searchString.length();
        final int textLength = text.length();
        if (searchLength > textLength) {
            return false;
        }
        for (int i = 0; i <= textLength - searchLength; i++) {
            if (text.regionMatches(true, i, searchString, 0, searchLength)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate retry delay with exponential backoff
     */
    private long calculateRetryDelay(int attempt) {
        final long retryInitialDelayMs = penaltyJobProperties.getRetryInitialDelayMs();
        final double retryMultiplier = penaltyJobProperties.getRetryMultiplier();
        final long retryMaxDelayMs = penaltyJobProperties.getRetryMaxDelayMs();
        final long delay = (long) (retryInitialDelayMs * Math.pow(retryMultiplier, attempt - 1));
        return Math.min(delay, retryMaxDelayMs);
    }
}
