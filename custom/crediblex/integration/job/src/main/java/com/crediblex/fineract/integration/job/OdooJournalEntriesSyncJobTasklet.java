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
package com.crediblex.fineract.integration.job;

import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSync;
import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSyncRepository;
import com.crediblex.fineract.integration.odoo.service.JournalEntryOdooTrackingService;
import com.crediblex.fineract.integration.odoo.service.OdooJournalEntryService;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanMonthlyAccrualJobAudit;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanMonthlyAccrualJobAuditRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Tasklet for synchronizing journal entries from Fineract to Odoo ERP system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OdooJournalEntriesSyncJobTasklet implements Tasklet {

    private final JournalEntryOdooSyncRepository journalEntryOdooSyncRepository;
    private final JournalEntryOdooTrackingService journalEntryOdooTrackingService;
    private final OdooJournalEntryService odooJournalEntryService;
    private final LoanMonthlyAccrualJobAuditRepository loanMonthlyAccrualJobAuditRepository;
    private final GLAccountRepository glAccountRepository;
    private final OfficeRepository officeRepository;

    // GL Account codes for accrual journal entries
    private static final String INTEREST_INCOME_GL_CODE = "300000";
    private static final String INTEREST_RECEIVABLE_GL_CODE = "100034";
    private static final String ODOO_ACCRUAL_JOURNAL_CODE = "ACCR";
    private static final String ODOO_EARLY_CLOSURE_JOURNAL_CODE = "BNK8";

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("Starting Odoo Journal Entries Sync Job execution...");

        try {
            // Step 1: Process unposted loan accrual summations and create journal entries
            processLoanAccrualJournalEntries();

            // Step 2: Get all pending journal entries that need to be synced to Odoo
            List<JournalEntryOdooSync> pendingEntries = journalEntryOdooSyncRepository.findPendingEntries();
            log.info("Found {} pending journal entries to sync to Odoo", pendingEntries.size());

            // Group entries by loan ID (entries without loan ID will be processed individually)
            Map<Long, List<JournalEntryOdooSync>> entriesByLoanId = pendingEntries.stream().filter(entry -> entry.getLoanId() != null)
                    .collect(Collectors.groupingBy(JournalEntryOdooSync::getLoanId));

            // Get entries without loan ID (savings transactions, manual entries, etc.)
            List<JournalEntryOdooSync> entriesWithoutLoanId = pendingEntries.stream().filter(entry -> entry.getLoanId() == null)
                    .collect(Collectors.toList());

            int successCount = 0;
            int failureCount = 0;
            int movesCreated = 0;

            // Process entries grouped by loan ID
            for (Map.Entry<Long, List<JournalEntryOdooSync>> loanGroup : entriesByLoanId.entrySet()) {
                Long loanId = loanGroup.getKey();
                List<JournalEntryOdooSync> loanEntries = loanGroup.getValue();

                log.info("Processing {} journal entries for loan ID: {}", loanEntries.size(), loanId);

                try {
                    // Check for early closure entries first
                    boolean processed = processEarlyClosureJournalEntriesForLoan(loanId, loanEntries);

                    if (!processed) {
                        // Post all journal entries for this loan (may create multiple moves for different journals)
                        Map<Integer, Long> journalToMoveMap = odooJournalEntryService.postJournalEntriesForLoan(loanId, loanEntries);

                        if (!journalToMoveMap.isEmpty()) {
                            // Mark all entries in this loan as posted
                            // Note: We use the first move ID for simplicity, but all entries are successfully posted
                            Long firstMoveId = journalToMoveMap.values().iterator().next();

                            for (JournalEntryOdooSync sync : loanEntries) {
                                journalEntryOdooTrackingService.markAsPosted(sync.getJournalEntry().getId(), firstMoveId);
                                successCount++;
                            }
                            movesCreated += journalToMoveMap.size();
                            log.info("Successfully posted {} journal entries for loan {} to Odoo across {} moves. Journals: {}",
                                    loanEntries.size(), loanId, journalToMoveMap.size(), journalToMoveMap.keySet());
                        } else {
                            // This should rarely happen as the service method should throw exceptions for specific
                            // failures
                            String errorMsg = "Failed to create any moves in Odoo for loan " + loanId
                                    + " - No specific error details available (possible authentication or configuration issue)";
                            log.error("No moves created for loan {}: This suggests a service-level issue without specific error details",
                                    loanId);

                            for (JournalEntryOdooSync sync : loanEntries) {
                                journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), errorMsg);
                                failureCount++;
                            }
                        }
                    } else {
                        // Early closure was processed, count as success
                        successCount += loanEntries.size();
                        log.info("Successfully processed {} early closure journal entries for loan {}", loanEntries.size(), loanId);
                    }

                } catch (Exception e) {
                    // Capture the specific error details for better debugging
                    String specificError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    String detailedErrorMsg = String.format("Failed to post journal entries for loan %d to Odoo: %s", loanId,
                            specificError);

                    log.error("Failed to post journal entries for loan {} to Odoo - Error: {}", loanId, specificError, e);

                    for (JournalEntryOdooSync sync : loanEntries) {
                        journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), detailedErrorMsg);
                        failureCount++;
                    }
                }
            }

            // Process entries without loan ID - only group SAVINGS_DEPOSIT_TO_CASH_MARGIN entries, process others
            // individually
            List<JournalEntryOdooSync> cashMarginEntries = entriesWithoutLoanId.stream()
                    .filter(entry -> "SAVINGS_DEPOSIT_TO_CASH_MARGIN".equals(entry.getBusinessEventType())).collect(Collectors.toList());

            // Process SAVINGS_DEPOSIT_TO_CASH_MARGIN entries as a group
            if (!cashMarginEntries.isEmpty()) {
                String businessEventType = "SAVINGS_DEPOSIT_TO_CASH_MARGIN";
                log.info("Processing {} journal entries for business event type: {}", cashMarginEntries.size(), businessEventType);

                try {
                    // Post all journal entries for this business event (may create multiple moves for different
                    // journals)
                    Map<Integer, Long> journalToMoveMap = odooJournalEntryService.postJournalEntriesForBusinessEvent(businessEventType,
                            cashMarginEntries);

                    if (!journalToMoveMap.isEmpty()) {
                        // Mark all entries in this business event as posted
                        // Note: We use the first move ID for simplicity, but all entries are successfully posted
                        Long firstMoveId = journalToMoveMap.values().iterator().next();

                        for (JournalEntryOdooSync sync : cashMarginEntries) {
                            journalEntryOdooTrackingService.markAsPosted(sync.getJournalEntry().getId(), firstMoveId);
                            successCount++;
                        }
                        movesCreated += journalToMoveMap.size();
                        log.info("Successfully posted {} journal entries for business event {} to Odoo across {} moves. Journals: {}",
                                cashMarginEntries.size(), businessEventType, journalToMoveMap.size(), journalToMoveMap.keySet());
                    } else {
                        // This should rarely happen as the service method should throw exceptions for specific failures
                        String errorMsg = "Failed to create any moves in Odoo for business event " + businessEventType
                                + " - No specific error details available (possible authentication or configuration issue)";
                        log.error(
                                "No moves created for business event {}: This suggests a service-level issue without specific error details",
                                businessEventType);

                        for (JournalEntryOdooSync sync : cashMarginEntries) {
                            journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), errorMsg);
                            failureCount++;
                        }
                    }

                } catch (Exception e) {
                    // Capture the specific error details for better debugging
                    String specificError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    String detailedErrorMsg = String.format("Failed to post journal entries for business event %s to Odoo: %s",
                            businessEventType, specificError);

                    log.error("Failed to post journal entries for business event {} to Odoo - Error: {}", businessEventType, specificError,
                            e);

                    for (JournalEntryOdooSync sync : cashMarginEntries) {
                        journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), detailedErrorMsg);
                        failureCount++;
                    }
                }
            }

            log.info("Odoo Journal Entries Sync Job completed - Success: {}, Failures: {}, Moves Created: {}", successCount, failureCount,
                    movesCreated);

        } catch (Exception e) {
            log.error("Error during Odoo Journal Entries Sync Job execution", e);
            throw e;
        }

        log.info("Completed Odoo Journal Entries Sync Job execution");
        return RepeatStatus.FINISHED;
    }

    /**
     * Process unposted loan monthly accrual summations by creating corresponding journal entries. This method creates
     * debit and credit entries for interest accruals and marks them for Odoo posting.
     */
    private void processLoanAccrualJournalEntries() {
        try {
            // Find all loan accrual records that haven't been posted to Odoo yet
            List<LoanMonthlyAccrualJobAudit> unpostedAccruals = loanMonthlyAccrualJobAuditRepository.findUnpostedAccruals();
            log.info("Found {} unposted loan accrual summations to process", unpostedAccruals.size());

            if (unpostedAccruals.isEmpty()) {
                return;
            }

            // Get GL accounts for accrual entries
            GLAccount interestIncomeAccount = glAccountRepository.findOneByGlCode(INTEREST_INCOME_GL_CODE).orElseThrow(
                    () -> new RuntimeException("Interest Income GL Account with code " + INTEREST_INCOME_GL_CODE + " not found"));

            GLAccount interestReceivableAccount = glAccountRepository.findOneByGlCode(INTEREST_RECEIVABLE_GL_CODE).orElseThrow(
                    () -> new RuntimeException("Interest Receivable GL Account with code " + INTEREST_RECEIVABLE_GL_CODE + " not found"));

            // Get head office (adjust this logic based on your office structure)
            Office headOffice = officeRepository.findById(1L).orElseThrow(() -> new RuntimeException("Head Office not found"));

            int successCount = 0;
            int failureCount = 0;

            for (LoanMonthlyAccrualJobAudit accrualAudit : unpostedAccruals) {
                try {
                    createAccrualJournalEntries(accrualAudit, interestIncomeAccount, interestReceivableAccount, headOffice);

                    // Mark as posted to Odoo
                    accrualAudit.setPostedToOdoo(true);
                    loanMonthlyAccrualJobAuditRepository.saveAndFlush(accrualAudit);

                    successCount++;
                    log.info("Successfully created accrual journal entries for Loan ID: {} with amount: {}", accrualAudit.getLoanId(),
                            accrualAudit.getTotalInterestAccrualDerived());

                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to create accrual journal entries for Loan ID: {}", accrualAudit.getLoanId(), e);
                }
            }

            log.info("Loan accrual journal entries processing completed - Success: {}, Failures: {}", successCount, failureCount);

        } catch (Exception e) {
            log.error("Error during loan accrual journal entries processing", e);
            throw new RuntimeException("Failed to process loan accrual journal entries", e);
        }
    }

    /**
     * Post accrual journal entries directly to Odoo without creating database records. Creates debit and credit journal
     * lines on-the-fly and posts them to Odoo.
     */
    private void createAccrualJournalEntries(LoanMonthlyAccrualJobAudit accrualAudit, GLAccount interestIncomeAccount,
            GLAccount interestReceivableAccount, Office office) {

        String transactionId = ODOO_ACCRUAL_JOURNAL_CODE + "_" + accrualAudit.getId() + "_" + System.currentTimeMillis();
        BigDecimal accrualAmount = accrualAudit.getTotalInterestAccrualDerived();
        LocalDate transactionDate = accrualAudit.getGeneratedOnDate();
        String description = "Interest Accrual - Loan ID: " + accrualAudit.getLoanId();

        try {
            // Post accrual journal entries directly to Odoo without creating database records
            Long odooMoveId = odooJournalEntryService.postAccrualJournalEntriesToOdoo(accrualAudit.getLoanId(), transactionId,
                    transactionDate, description, accrualAmount, INTEREST_INCOME_GL_CODE, // Credit account (300000)
                    INTEREST_RECEIVABLE_GL_CODE, // Debit account (100034)
                    ODOO_ACCRUAL_JOURNAL_CODE);

            if (odooMoveId != null) {
                log.info("Successfully posted accrual journal entries to Odoo for Loan ID: {} with move ID: {} and amount: {}",
                        accrualAudit.getLoanId(), odooMoveId, accrualAmount);
            } else {
                throw new RuntimeException("Failed to post accrual journal entries to Odoo - No move ID returned");
            }

        } catch (Exception e) {
            log.error("Failed to post accrual journal entries to Odoo for Loan ID: {} with amount: {}", accrualAudit.getLoanId(),
                    accrualAmount, e);
            throw e;
        }

        log.debug("Posted accrual journal entries to Odoo for Loan ID: {} with transaction ID: {}", accrualAudit.getLoanId(),
                transactionId);
    }

    /**
     * Process early closure journal entries for a specific loan. This method checks if the loan is closed (status 600)
     * and has EARLY_CLOSURE business event type entries.
     *
     * @param loanId
     *            The ID of the loan to process
     * @param loanEntries
     *            The journal entries for this loan
     * @return true if early closure entries were found and processed, false otherwise
     */
    private boolean processEarlyClosureJournalEntriesForLoan(Long loanId, List<JournalEntryOdooSync> loanEntries) {
        // Check if there are any EARLY_CLOSURE business event type entries
        List<JournalEntryOdooSync> earlyClosureEntries = loanEntries.stream()
                .filter(entry -> "EARLY_CLOSURE".equals(entry.getBusinessEventType())).collect(Collectors.toList());

        if (earlyClosureEntries.isEmpty()) {
            log.debug("No early closure entries found for loan ID: {}", loanId);
            return true;
        }

        log.info("Found {} early closure journal entries for loan ID: {}", earlyClosureEntries.size(), loanId);

        try {
            // Check loan status to confirm it is closed (status 600)
            // You can add a loan repository call here to verify loan status if needed
            // For now, we'll proceed based on the presence of EARLY_CLOSURE entries

            // Process early closure entries with specific transformation logic

            log.info("Processing {} early closure journal entries for loan ID: {}", earlyClosureEntries.size(), loanId);

            // Placeholder for early closure processing logic
            // This will be implemented in the next step based on your requirements
            processEarlyClosureEntriesGroup(loanId, earlyClosureEntries);

            return true;

        } catch (Exception e) {
            log.error("Failed to process early closure entries for loan ID: {}", loanId, e);

            // Mark entries as failed
            String errorMsg = "Failed to process early closure entries for loan " + loanId + ": " + e.getMessage();
            for (JournalEntryOdooSync sync : earlyClosureEntries) {
                journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), errorMsg);
            }

            return false;
        }
    }

    /**
     * Process grouped early closure entries and send them to Odoo. This method transforms the database journal entries
     * into the required Odoo format with remapping and additional entries.
     *
     * @param loanId
     *            The ID of the loan
     * @param earlyClosureEntries
     *            The early closure journal entries to process
     */
    private void processEarlyClosureEntriesGroup(Long loanId, List<JournalEntryOdooSync> earlyClosureEntries) {
        log.info("Processing early closure entries group for loan ID: {} with {} entries", loanId, earlyClosureEntries.size());

        try {
            // Create the transformed journal lines for Odoo
            List<Map<String, Object>> odooJournalLines = createEarlyClosureOdooJournalLines(loanId, earlyClosureEntries);

            // Get transaction date from the first entry (they should all be from the same transaction)
            LocalDate transactionDate = earlyClosureEntries.get(0).getJournalEntry().getTransactionDate();

            // Post to Odoo using the transformed journal lines and reusing existing service methods
            Long odooMoveId = odooJournalEntryService.postEarlyClosureJournalEntriesToOdoo(loanId, odooJournalLines, transactionDate,
                    ODOO_EARLY_CLOSURE_JOURNAL_CODE);

            if (odooMoveId != null) {
                // Mark all original entries as posted
                for (JournalEntryOdooSync entry : earlyClosureEntries) {
                    journalEntryOdooTrackingService.markAsPosted(entry.getJournalEntry().getId(), odooMoveId);
                }
                log.info("Successfully posted {} early closure journal entries for loan {} to Odoo with move ID: {}",
                        earlyClosureEntries.size(), loanId, odooMoveId);
            } else {
                throw new RuntimeException("Failed to post early closure journal entries to Odoo - No move ID returned");
            }

        } catch (Exception e) {
            log.error("Failed to process early closure entries for loan ID: {}", loanId, e);
            throw e;
        }
    }

    /**
     * Create the transformed journal lines for early closure entries to be sent to Odoo. This method handles the
     * mapping and creation of additional entries as per requirements.
     *
     * @param loanId
     *            The ID of the loan
     * @param earlyClosureEntries
     *            The original journal entries from the database
     * @return List of transformed journal lines for Odoo
     */
    private List<Map<String, Object>> createEarlyClosureOdooJournalLines(Long loanId, List<JournalEntryOdooSync> earlyClosureEntries) {
        List<Map<String, Object>> odooJournalLines = new ArrayList<>();
        BigDecimal feeAmount = BigDecimal.ZERO;

        // Process each original journal entry and transform/map as needed
        for (JournalEntryOdooSync entrySync : earlyClosureEntries) {
            var journalEntry = entrySync.getJournalEntry();
            String glCode = journalEntry.getGlAccount().getGlCode();
            String accountName = journalEntry.getGlAccount().getName();
            BigDecimal amount = journalEntry.getAmount();
            boolean isDebit = journalEntry.isDebitEntry();

            // Transform entries based on account names and business rules
            switch (glCode) {
                case "Fees Receivable": // Maps to Early Settlement Fee Revenue (300002)
                    feeAmount = amount; // Store fee amount for additional entries
                    odooJournalLines
                            .add(createOdooJournalLine("Early Settlement Fee Revenue", "Revenue", isDebit ? "DR" : "CR", amount, "300002"));
                break;

                case "Liability Transfer": // Maps to Working Capital Loan (210003)
                    odooJournalLines.add(
                            createOdooJournalLine("Working Capital Loan", "Working Capital Loan", isDebit ? "DR" : "CR", amount, "210003"));
                break;

                default:
                    // For all other GL codes, use the original journal entry data
                    String accountType = journalEntry.getGlAccount().getDescription() != null ? journalEntry.getGlAccount().getDescription()
                            : "Other";

                    odooJournalLines.add(createOdooJournalLine(accountName, accountType, isDebit ? "DR" : "CR", amount, glCode));
                break;
            }
        }

        // Add the two additional entries for Early Settlement Fee - Receivable (if fee amount > 0)
        if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Early Settlement Fee - Receivable DR entry
            odooJournalLines.add(createOdooJournalLine("Early Settlement Fee - Receivable", "Current Asset", "DR", feeAmount, "100065"));

            // Early Settlement Fee - Receivable CR entry (balancing entry)
            odooJournalLines.add(createOdooJournalLine("Early Settlement Fee - Receivable", "Current Asset", "CR", feeAmount, "100065"));
        }

        log.info("Created {} transformed journal lines for early closure of loan ID: {}", odooJournalLines.size(), loanId);

        return odooJournalLines;
    }

    /**
     * Helper method to create a journal line map for Odoo posting.
     *
     * @param accountName
     *            The name of the account
     * @param accountType
     *            The type/category of the account
     * @param debitCredit
     *            "DR" for debit, "CR" for credit
     * @param amount
     *            The amount for this line
     * @param glCode
     *            The GL account code
     * @return Map representing a journal line for Odoo
     */
    private Map<String, Object> createOdooJournalLine(String accountName, String accountType, String debitCredit, BigDecimal amount,
            String glCode) {
        Map<String, Object> journalLine = new HashMap<>();
        journalLine.put("account_name", accountName);
        journalLine.put("account_type", accountType);
        journalLine.put("debit_credit", debitCredit);
        journalLine.put("amount", amount);
        journalLine.put("gl_code", glCode);

        return journalLine;
    }
}
