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
import com.crediblex.fineract.integration.odoo.service.EntryProcessingResult;
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
                    // Validate GL accounts exist in Odoo before posting
                    Map<String, Boolean> validationResults = validateGLAccountsExistInOdooWithDetails(loanEntries);
                    List<String> invalidAccounts = validationResults.entrySet().stream().filter(entry -> !entry.getValue())
                            .map(Map.Entry::getKey).collect(Collectors.toList());

                    if (!invalidAccounts.isEmpty()) {
                        String errorMsg = String.format("GL accounts do not exist in Odoo for loan %d: %s", loanId,
                                String.join(", ", invalidAccounts));
                        log.error("GL account validation failed for loan {} - Missing accounts: {}", loanId, invalidAccounts);
                        for (JournalEntryOdooSync sync : loanEntries) {
                            journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), errorMsg);
                            failureCount++;
                        }
                        continue;
                    }

                    // Post all journal entries for this loan (may create multiple moves for different journals)
                    EntryProcessingResult result = processJournalEntriesForLoan(loanId, loanEntries);

                    successCount += result.getSuccessCount();
                    failureCount += result.getFailureCount();
                    movesCreated += result.getMovesCreated();

                    if (result.getSuccessCount() > 0) {
                        log.info("Successfully posted {} out of {} journal entries for loan {} to Odoo across {} moves",
                                result.getSuccessCount(), loanEntries.size(), loanId, result.getMovesCreated());
                    }
                    if (result.getFailureCount() > 0) {
                        log.warn("Failed to post {} out of {} journal entries for loan {}", result.getFailureCount(), loanEntries.size(),
                                loanId);
                    }

                } catch (Exception e) {
                    // If enhanced processing fails completely, fall back to original behavior
                    // Mark all entries as failed with the same error message (original behavior)
                    String specificError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    String detailedErrorMsg;

                    // Handle specific constraint violations (keeping original logic)
                    if (specificError.contains("account_move_line_account_id_fkey")) {
                        String accountDetails = loanEntries.stream()
                                .map(sync -> String.format("JE %d: GL Code '%s' (Account: %s)", sync.getJournalEntry().getId(),
                                        sync.getJournalEntry().getGlAccount().getGlCode(), sync.getJournalEntry().getGlAccount().getName()))
                                .collect(Collectors.joining("; "));

                        detailedErrorMsg = String.format(
                                "GL Account foreign key constraint violation for loan %d. Accounts in entries: %s. One or more mapped account IDs do not exist in Odoo.",
                                loanId, accountDetails);
                        log.error("GL Account constraint violation for loan {} - Account details: {}", loanId, accountDetails);
                        log.error("This suggests account mapping returned invalid account IDs or accounts were deleted/archived in Odoo");
                    } else if (specificError.contains("constraint") || specificError.contains("fkey")) {
                        detailedErrorMsg = String.format("Database constraint violation for loan %d: %s. Check data integrity.", loanId,
                                specificError);
                        log.error("Database constraint violation for loan {}: {}", loanId, specificError);
                    } else {
                        detailedErrorMsg = String.format("Failed to post journal entries for loan %d to Odoo: %s", loanId, specificError);
                        log.error("Failed to post journal entries for loan {} to Odoo - Error: {}", loanId, specificError, e);
                    }

                    // Mark all entries as failed (original behavior)
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
                    EntryProcessingResult result = processJournalEntriesForBusinessEvent(businessEventType, cashMarginEntries);

                    successCount += result.getSuccessCount();
                    failureCount += result.getFailureCount();
                    movesCreated += result.getMovesCreated();

                    if (result.getSuccessCount() > 0) {
                        log.info("Successfully posted {} out of {} journal entries for business event {} to Odoo across {} moves",
                                result.getSuccessCount(), cashMarginEntries.size(), businessEventType, result.getMovesCreated());
                    }
                    if (result.getFailureCount() > 0) {
                        log.warn("Failed to post {} out of {} journal entries for business event {}", result.getFailureCount(),
                                cashMarginEntries.size(), businessEventType);
                    }

                } catch (Exception e) {
                    // If enhanced processing fails completely, fall back to original behavior
                    // Mark all entries as failed with the same error message (original behavior)
                    String specificError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    String detailedErrorMsg = String.format("Failed to post journal entries for business event %s to Odoo: %s",
                            businessEventType, specificError);

                    log.error("Failed to post journal entries for business event {} to Odoo - Error: {}", businessEventType, specificError,
                            e);

                    // Mark all entries as failed (original behavior)
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
    private void processEarlyClosureJournalEntriesForLoan(Long loanId, List<JournalEntryOdooSync> loanEntries) {
        // Check if there are any EARLY_CLOSURE business event type entries
        List<JournalEntryOdooSync> earlyClosureEntries = loanEntries.stream()
                .filter(entry -> "EARLY_CLOSURE".equals(entry.getBusinessEventType())).collect(Collectors.toList());

        if (earlyClosureEntries.isEmpty()) {
            log.debug("No early closure entries found for loan ID: {}", loanId);
            return; // Exit early if no entries to process
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

        } catch (Exception e) {
            log.error("Failed to process early closure entries for loan ID: {}", loanId, e);

            // Mark entries as failed
            String errorMsg = "Failed to process early closure entries for loan " + loanId + ": " + e.getMessage();
            for (JournalEntryOdooSync sync : earlyClosureEntries) {
                journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), errorMsg);
            }
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

        // Validate that we have entries to process
        if (earlyClosureEntries == null || earlyClosureEntries.isEmpty()) {
            log.warn("No early closure entries to process for loan ID: {}", loanId);
            return;
        }

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
            if (journalEntry.getGlAccount() == null) {
                log.warn("Skipping journal entry with null GL account for loan ID: {}", loanId);
                continue;
            }

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
     * Validate that all GL accounts referenced in the journal entries exist in Odoo with detailed results. This
     * prevents foreign key constraint violations when creating journal entries.
     *
     * @param journalEntries
     *            List of journal entries to validate
     * @return Map of GL code to validation result (true if exists, false if not)
     */
    private Map<String, Boolean> validateGLAccountsExistInOdooWithDetails(List<JournalEntryOdooSync> journalEntries) {
        Map<String, Boolean> validationResults = new HashMap<>();

        try {
            log.debug("Validating {} journal entries for GL account existence in Odoo with detailed results", journalEntries.size());

            // Array of GL codes that should be skipped during validation
            String[] skipValidationGlCodes = { "Liability Transfer" };

            for (JournalEntryOdooSync entrySync : journalEntries) {
                if (entrySync == null || entrySync.getJournalEntry() == null || entrySync.getJournalEntry().getGlAccount() == null) {
                    log.warn("Skipping validation for null journal entry or GL account");
                    continue;
                }

                String glCode = entrySync.getJournalEntry().getGlAccount().getGlCode();
                String accountName = entrySync.getJournalEntry().getGlAccount().getName();

                if (glCode == null || glCode.trim().isEmpty()) {
                    log.error("Journal entry {} has null or empty GL code", entrySync.getJournalEntry().getId());
                    validationResults.put("NULL_GL_CODE", false);
                    continue;
                }

                // Skip if already validated
                if (validationResults.containsKey(glCode)) {
                    continue;
                }

                // Check if this GL code should be skipped during validation
                boolean skipValidation = false;
                for (String skipCode : skipValidationGlCodes) {
                    if (skipCode.equals(glCode) || skipCode.equals(accountName)) {
                        log.debug("Skipping validation for GL code '{}' as it's in the skip list", glCode);
                        validationResults.put(glCode, true); // Mark as valid since it's skipped
                        skipValidation = true;
                        break;
                    }
                }

                if (skipValidation) {
                    continue;
                }

                // Check if account exists in Odoo
                boolean accountExists = odooJournalEntryService.doesAccountExistInOdoo(glCode);
                validationResults.put(glCode, accountExists);

                if (!accountExists) {
                    log.error("GL Account with code '{}' and name '{}' does not exist in Odoo", glCode, accountName);
                } else {
                    log.debug("GL Account with code '{}' validated successfully in Odoo", glCode);
                }
            }

            long validCount = validationResults.values().stream().mapToLong(valid -> valid ? 1 : 0).sum();
            long totalCount = validationResults.size();
            log.debug("GL Account validation completed: {}/{} accounts valid", validCount, totalCount);

            return validationResults;

        } catch (Exception e) {
            log.error("Error validating GL accounts in Odoo", e);
            validationResults.put("VALIDATION_ERROR", false);
            return validationResults;
        }
    }

    /**
     * Validate that all GL accounts referenced in the journal entries exist in Odoo. This prevents foreign key
     * constraint violations when creating journal entries.
     *
     * @param journalEntries
     *            List of journal entries to validate
     * @return true if all GL accounts exist in Odoo, false otherwise
     */
    private boolean validateGLAccountsExistInOdoo(List<JournalEntryOdooSync> journalEntries) {
        Map<String, Boolean> validationResults = validateGLAccountsExistInOdooWithDetails(journalEntries);
        return validationResults.values().stream().allMatch(Boolean::booleanValue);
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

    /**
     * Process journal entries for a loan with individual entry tracking
     */
    private EntryProcessingResult processJournalEntriesForLoan(Long loanId, List<JournalEntryOdooSync> loanEntries) {
        try {
            // Try the enhanced service method that provides individual entry tracking
            EntryProcessingResult result = odooJournalEntryService.postJournalEntriesForLoanWithTracking(loanId, loanEntries);

            // Mark individual entries as posted or failed based on the detailed result
            for (Long successfulEntryId : result.getSuccessfulEntryIds()) {
                Long moveId = result.getJournalToMoveMap().isEmpty() ? 0L : result.getJournalToMoveMap().values().iterator().next();
                journalEntryOdooTrackingService.markAsPosted(successfulEntryId, moveId);
            }

            for (Map.Entry<Long, String> failedEntry : result.getFailedEntryIds().entrySet()) {
                journalEntryOdooTrackingService.markAsFailed(failedEntry.getKey(), failedEntry.getValue());
            }

            return result;
        } catch (Exception e) {
            // If enhanced method fails, fall back to original method for consistency
            log.warn("Enhanced processing failed for loan {}, falling back to original method: {}", loanId, e.getMessage());

            try {
                // Use original method as fallback
                Map<Integer, Long> journalToMoveMap = odooJournalEntryService.postJournalEntriesForLoan(loanId, loanEntries);

                if (!journalToMoveMap.isEmpty()) {
                    // Mark all entries as posted (original behavior)
                    Long firstMoveId = journalToMoveMap.values().iterator().next();
                    for (JournalEntryOdooSync sync : loanEntries) {
                        journalEntryOdooTrackingService.markAsPosted(sync.getJournalEntry().getId(), firstMoveId);
                    }
                    return new EntryProcessingResult(
                            loanEntries.stream().map(sync -> sync.getJournalEntry().getId()).collect(Collectors.toList()), Map.of(),
                            journalToMoveMap.size(), journalToMoveMap);
                } else {
                    // No moves created - this will be handled by the catch block above
                    throw new RuntimeException("Failed to create any moves in Odoo for loan " + loanId);
                }
            } catch (Exception fallbackException) {
                // Both methods failed - let the exception bubble up
                throw fallbackException;
            }
        }
    }

    /**
     * Process journal entries for a business event with individual entry tracking
     */
    private EntryProcessingResult processJournalEntriesForBusinessEvent(String businessEventType, List<JournalEntryOdooSync> entries) {
        try {
            // Try the enhanced service method that provides individual entry tracking
            EntryProcessingResult result = odooJournalEntryService.postJournalEntriesForBusinessEventWithTracking(businessEventType,
                    entries);

            // Mark individual entries as posted or failed based on the detailed result
            for (Long successfulEntryId : result.getSuccessfulEntryIds()) {
                Long moveId = result.getJournalToMoveMap().isEmpty() ? 0L : result.getJournalToMoveMap().values().iterator().next();
                journalEntryOdooTrackingService.markAsPosted(successfulEntryId, moveId);
            }

            for (Map.Entry<Long, String> failedEntry : result.getFailedEntryIds().entrySet()) {
                journalEntryOdooTrackingService.markAsFailed(failedEntry.getKey(), failedEntry.getValue());
            }

            return result;
        } catch (Exception e) {
            // If enhanced method fails, fall back to original method for consistency
            log.warn("Enhanced processing failed for business event {}, falling back to original method: {}", businessEventType,
                    e.getMessage());

            try {
                // Use original method as fallback
                Map<Integer, Long> journalToMoveMap = odooJournalEntryService.postJournalEntriesForBusinessEvent(businessEventType,
                        entries);

                if (!journalToMoveMap.isEmpty()) {
                    // Mark all entries as posted (original behavior)
                    Long firstMoveId = journalToMoveMap.values().iterator().next();
                    for (JournalEntryOdooSync sync : entries) {
                        journalEntryOdooTrackingService.markAsPosted(sync.getJournalEntry().getId(), firstMoveId);
                    }
                    return new EntryProcessingResult(
                            entries.stream().map(sync -> sync.getJournalEntry().getId()).collect(Collectors.toList()), Map.of(),
                            journalToMoveMap.size(), journalToMoveMap);
                } else {
                    // No moves created - this will be handled by the catch block above
                    throw new RuntimeException("Failed to create any moves in Odoo for business event " + businessEventType);
                }
            } catch (Exception fallbackException) {
                // Both methods failed - let the exception bubble up
                throw fallbackException;
            }
        }
    }
}
