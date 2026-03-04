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
package com.crediblex.fineract.integration.odoo.service;
import com.crediblex.fineract.accounting.journalentry.service.LOCAccountingHelper;

import com.crediblex.fineract.accounting.journalentry.SavingsJournalEntryCreatedBusinessEvent;
import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSync;
import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSyncRepository;
import com.crediblex.fineract.portfolio.accountdetails.service.CredXAccountDetailsReadPlatformServiceJpaRepositoryImpl;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.infrastructure.event.business.domain.journalentry.LoanJournalEntryCreatedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "odoo.enabled", havingValue = "true")
public class JournalEntryOdooTrackingService {

    private final BusinessEventNotifierService businessEventNotifierService;
    private final JournalEntryOdooSyncRepository journalEntryOdooSyncRepository;
    private final LoanTransactionRepository loanTransactionRepository;
    private final SavingsAccountTransactionRepository savingsAccountTransactionRepository;
    private final CredXAccountDetailsReadPlatformServiceJpaRepositoryImpl accountDetailsService;
    private final LOCAccountingHelper locAccountingHelper;

    @PostConstruct
    public void addListeners() {
        log.info("Initializing Journal Entry Odoo tracking listeners");

        businessEventNotifierService.addPostBusinessEventListener(LoanJournalEntryCreatedBusinessEvent.class, event -> {
            try {
                JournalEntry journalEntry = event.get();
                createTrackingRecord(journalEntry);
            } catch (Exception e) {
                log.error("Error creating journal entry tracking record", e);
            }
        });

        businessEventNotifierService.addPostBusinessEventListener(SavingsJournalEntryCreatedBusinessEvent.class, event -> {
            try {
                JournalEntry journalEntry = event.get();
                createTrackingRecord(journalEntry);
            } catch (Exception e) {
                log.error("Error creating savings journal entry tracking record", e);
            }
        });
    }

    @Transactional
    public void createTrackingRecord(JournalEntry journalEntry) {
        log.debug("Creating tracking record for journal entry ID: {}", journalEntry.getId());

        // Check if tracking record already exists
        if (journalEntryOdooSyncRepository.findByJournalEntryId(journalEntry.getId()).isEmpty()) {
            // Check if the transaction is reversed - only create tracking records for non-reversed transactions
            boolean isTransactionReversed = false;

            if (journalEntry.getLoanTransactionId() != null) {
                isTransactionReversed = isLoanTransactionReversed(journalEntry.getLoanTransactionId());
            } else if (journalEntry.getSavingsTransactionId() != null) {
                isTransactionReversed = isSavingsTransactionReversed(journalEntry.getSavingsTransactionId());
            }

            if (isTransactionReversed) {
                log.debug("Skipping tracking record creation for journal entry ID: {} - transaction is reversed", journalEntry.getId());
                return;
            }

            // Get loan ID from loan transaction if available
            Long loanId = null;
            String businessEventType = null;

            if (journalEntry.getLoanTransactionId() != null) {
                loanId = getLoanIdFromTransactionId(journalEntry.getLoanTransactionId());
                businessEventType = getBusinessEventTypeFromLoanTransaction(journalEntry.getLoanTransactionId());
            } else if (journalEntry.getSavingsTransactionId() != null) {
                // For savings transactions, try to get linked loan ID from savings account
                loanId = getLoanIdFromSavingsTransactionId(journalEntry.getSavingsTransactionId());
                String glCode = journalEntry.getGlAccount() != null ? journalEntry.getGlAccount().getGlCode() : null;
                boolean isDebit = journalEntry.isDebitEntry();
                businessEventType = getBusinessEventTypeFromSavingsTransaction(journalEntry.getSavingsTransactionId(), glCode, isDebit);
            }

            // Skip creating tracking records for ACCRUAL business events
            // Reason: Accrual journal entry posting depends on "Generate Loan Monthly Accrual Summations" job.
            // When this job runs, it creates monthly accrual entries and we use those records for creating
            // journal lines manually by using the accrued values from that job and post to Odoo.
            // Therefore, there's no reason to create tracking records here for individual accrual entries.
            if ("ACCRUAL".equals(businessEventType)) {
                log.debug("Skipping tracking record creation for journal entry ID: {} - ACCRUAL business event type not tracked",
                        journalEntry.getId());
                return;
            }

            JournalEntryOdooSync trackingRecord = new JournalEntryOdooSync(journalEntry, loanId, businessEventType);
            journalEntryOdooSyncRepository.save(trackingRecord);

            String transactionType = journalEntry.getLoanTransactionId() != null ? "loan" : "savings";
            log.info("Created Odoo sync tracking record for {} journal entry ID: {} with loan ID: {} and business event type: {}",
                    transactionType, journalEntry.getId(), loanId, businessEventType);
        } else {
            log.debug("Tracking record already exists for journal entry ID: {}", journalEntry.getId());
        }
    }

    /**
     * Get loan ID from loan transaction ID
     */
    private Long getLoanIdFromTransactionId(Long loanTransactionId) {
        if (loanTransactionId == null) {
            return null;
        }

        try {
            LoanTransaction loanTransaction = loanTransactionRepository.findById(loanTransactionId).orElse(null);
            if (loanTransaction != null && loanTransaction.getLoan() != null) {
                return loanTransaction.getLoan().getId();
            }
        } catch (Exception e) {
            log.warn("Failed to get loan ID for transaction ID: {}", loanTransactionId, e);
        }

        return null;
    }

    /**
     * Check if a loan transaction is reversed
     */
    private boolean isLoanTransactionReversed(Long loanTransactionId) {
        if (loanTransactionId == null) {
            return false;
        }

        try {
            String sql = "SELECT is_reversed FROM m_loan_transaction WHERE id = ?";
            List<Boolean> results = accountDetailsService.getJdbcTemplate().queryForList(sql, Boolean.class, loanTransactionId);

            if (!results.isEmpty()) {
                Boolean isReversed = results.get(0);
                boolean reversed = Boolean.TRUE.equals(isReversed);
                log.debug("Loan transaction {} is reversed: {}", loanTransactionId, reversed);
                return reversed;
            } else {
                log.warn("No loan transaction found with ID: {}", loanTransactionId);
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to check if loan transaction {} is reversed", loanTransactionId, e);
            return false;
        }
    }

    /**
     * Check if a savings transaction is reversed
     */
    private boolean isSavingsTransactionReversed(Long savingsTransactionId) {
        if (savingsTransactionId == null) {
            return false;
        }

        try {
            String sql = "SELECT is_reversed FROM m_savings_account_transaction WHERE id = ?";
            List<Boolean> results = accountDetailsService.getJdbcTemplate().queryForList(sql, Boolean.class, savingsTransactionId);

            if (!results.isEmpty()) {
                Boolean isReversed = results.get(0);
                boolean reversed = Boolean.TRUE.equals(isReversed);
                log.debug("Savings transaction {} is reversed: {}", savingsTransactionId, reversed);
                return reversed;
            } else {
                log.warn("No savings transaction found with ID: {}", savingsTransactionId);
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to check if savings transaction {} is reversed", savingsTransactionId, e);
            return false;
        }
    }

    /**
     * Get business event type based on loan transaction type Maps transaction_type_enum values to business event types
     */
    private String getBusinessEventTypeFromLoanTransaction(Long loanTransactionId) {
        if (loanTransactionId == null) {
            return null;
        }

        try {
            // Query the loan transaction to get the transaction type
            String sql = "SELECT transaction_type_enum FROM m_loan_transaction WHERE id = ?";
            List<Integer> transactionTypes = accountDetailsService.getJdbcTemplate().queryForList(sql, Integer.class, loanTransactionId);

            if (!transactionTypes.isEmpty()) {
                Integer transactionTypeEnum = transactionTypes.get(0);

                // Check for early closure/foreclosure only
                String earlyClosureCheck = checkForEarlyClosureTransaction(loanTransactionId);
                if (earlyClosureCheck != null) {
                    return earlyClosureCheck;
                }

                // Static mapping based on transaction_type_enum values
                // transaction_type_enum IN (1, 5, 35) maps to "DISBURSEMENT"
                if (transactionTypeEnum != null && (transactionTypeEnum == 1 || transactionTypeEnum == 5 || transactionTypeEnum == 35)) {
                    log.debug("Loan transaction {} with type {} mapped to DISBURSEMENT business event", loanTransactionId,
                            transactionTypeEnum);
                    return "DISBURSEMENT";
                }

                // Add more mappings as needed in the future
                // For example:
                if (transactionTypeEnum == 2) {
                    return "REPAYMENT";
                }

                if (transactionTypeEnum == 10) {
                    return "ACCRUAL";
                }

                log.debug("Loan transaction {} with type {} has no specific business event mapping", loanTransactionId,
                        transactionTypeEnum);
                return null;
            } else {
                log.warn("No transaction type found for loan transaction ID: {}", loanTransactionId);
                return null;
            }

        } catch (Exception e) {
            log.warn("Failed to get business event type for loan transaction ID: {}", loanTransactionId, e);
            return null;
        }
    }

    /**
     * Separate method to check if a loan transaction is an early closure/foreclosure This method is only called when
     * the transaction doesn't match standard business event types
     */
    private String checkForEarlyClosureTransaction(Long loanTransactionId) {
        try {
            List<Map<String, Object>> results = getForeclosureTransactionDetails(loanTransactionId);

            if (!results.isEmpty()) {
                Map<String, Object> result = results.get(0);
                Integer isForeclosure = ((Number) result.get("is_foreclosure")).intValue();
                Integer transactionTypeEnum = (Integer) result.get("transaction_type_enum");

                if (isForeclosure == 1) {
                    log.debug("Loan transaction {} with type {} identified as EARLY_CLOSURE business event", loanTransactionId,
                            transactionTypeEnum);
                    return "EARLY_CLOSURE";
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check for early closure for loan transaction ID: {}", loanTransactionId, e);
        }

        return null;
    }

    /**
     * Query to get foreclosure/early closure transaction details Separated into its own method for better
     * maintainability
     */
    private List<Map<String, Object>> getForeclosureTransactionDetails(Long loanTransactionId) {
        String sql = "SELECT mlt.transaction_type_enum, " + "CASE WHEN matd.transfer_type = 6 THEN 1 "
                + "     WHEN ml.closedon_date IS NOT NULL AND mlt.transaction_date = ml.closedon_date "
                + "          AND ml.loan_status_id = 600 AND mlt.transaction_type_enum = 2 THEN 1 " + "     ELSE 0 END as is_foreclosure "
                + "FROM m_loan_transaction mlt " + "LEFT JOIN m_account_transfer_transaction matt ON matt.to_loan_transaction_id = mlt.id "
                + "LEFT JOIN m_account_transfer_details matd ON matd.id = matt.account_transfer_details_id "
                + "LEFT JOIN m_loan ml ON ml.id = mlt.loan_id " + "WHERE mlt.id = ?";

        return accountDetailsService.getJdbcTemplate().queryForList(sql, loanTransactionId);
    }

    /**
     * Get business event type based on savings transaction type Maps transaction_type_enum values to business event
     * types
     */
    private String getBusinessEventTypeFromSavingsTransaction(Long savingsTransactionId, String glCode, boolean isDebit) {
        if (savingsTransactionId == null) {
            return null;
        }

        try {
            // Query the savings transaction to get the transaction type
            String sql = "SELECT transaction_type_enum FROM m_savings_account_transaction WHERE id = ?";
            List<Integer> transactionTypes = accountDetailsService.getJdbcTemplate().queryForList(sql, Integer.class, savingsTransactionId);

            if (!transactionTypes.isEmpty()) {
                Integer transactionTypeEnum = transactionTypes.get(0);

                // Static mapping based on transaction_type_enum values
                if (transactionTypeEnum != null) {
                    switch (transactionTypeEnum) {
                        case 1:
                            // Special case: GL code 200040 with credit entry should return DISBURSEMENT
                            if ("200040".equals(glCode) && !isDebit) {
                                log.debug(
                                        "Savings transaction {} with type {} and GL code {} (credit) mapped to DISBURSEMENT business event",
                                        savingsTransactionId, transactionTypeEnum, glCode);
                                return "DISBURSEMENT";
                            }

                            // Check for cash margin GL codes to determine if it's SAVINGS_DEPOSIT_TO_CASH_MARGIN
                            if (hasCashMarginGLCodes(savingsTransactionId, glCode)) {
                                log.debug(
                                        "Savings transaction {} with type {} and GL code {} mapped to SAVINGS_DEPOSIT_TO_CASH_MARGIN business event",
                                        savingsTransactionId, transactionTypeEnum, glCode);
                                return "SAVINGS_DEPOSIT_TO_CASH_MARGIN";
                            }
                            log.debug("Savings transaction {} with type {} mapped to SAVINGS_DEPOSIT business event", savingsTransactionId,
                                    transactionTypeEnum);
                            return "SAVINGS_DEPOSIT";
                        case 2:
                                // Nested check: If savings product is LOC Activation, return LOC_ACTIVATION_FEE as business event
                                try {
                                    SavingsAccountTransaction savingsTransaction = savingsAccountTransactionRepository.findById(savingsTransactionId).orElse(null);
                                    if (savingsTransaction != null && savingsTransaction.getSavingsAccount() != null && savingsTransaction.getSavingsAccount().getProduct() != null) {
                                        Long savingsProductId = savingsTransaction.getSavingsAccount().getProduct().getId();
                                        if (locAccountingHelper.isLOCActivationSavingsProduct(savingsProductId)) {
                                            log.debug("Savings withdrawal from LOC Activation product, returning LOC_ACTIVATION_FEE business event");
                                            return "LOC_ACTIVATION_FEE";
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("Error checking LOC Activation savings product for savings transaction {}", savingsTransactionId, e);
                                }
                            log.debug("Savings transaction {} with type {} mapped to SAVINGS_WITHDRAWAL business event",
                                    savingsTransactionId, transactionTypeEnum);
                            return "SAVINGS_WITHDRAWAL";
                        default:
                            log.debug("Savings transaction {} with type {} has no specific business event mapping", savingsTransactionId,
                                    transactionTypeEnum);
                            return null;
                    }
                }

                log.debug("Savings transaction {} has null transaction type", savingsTransactionId);
                return null;
            } else {
                log.warn("No transaction type found for savings transaction ID: {}", savingsTransactionId);
                return null;
            }

        } catch (Exception e) {
            log.warn("Failed to get business event type for savings transaction ID: {}", savingsTransactionId, e);
            return null;
        }
    }

    /**
     * Get linked loan ID from savings transaction ID This method uses the existing
     * CredXAccountDetailsReadPlatformServiceJpaRepositoryImpl which already has the complex logic to find linked loan
     * accounts
     */
    private Long getLoanIdFromSavingsTransactionId(Long savingsTransactionId) {
        if (savingsTransactionId == null) {
            return null;
        }

        try {
            // First get the savings account ID from the transaction
            SavingsAccountTransaction savingsTransaction = savingsAccountTransactionRepository.findById(savingsTransactionId).orElse(null);
            if (savingsTransaction != null && savingsTransaction.getSavingsAccount() != null) {
                Long savingsAccountId = savingsTransaction.getSavingsAccount().getId();

                return getLoanIdFromSavingsAccountId(savingsAccountId);
            }
        } catch (Exception e) {
            log.warn("Failed to get linked loan ID for savings transaction ID: {}", savingsTransactionId, e);
        }

        return null;
    }

    /**
     * Get linked loan ID from savings account ID using portfolio account associations
     */
    private Long getLoanIdFromSavingsAccountId(Long savingsAccountId) {
        if (savingsAccountId == null) {
            return null;
        }

        try {
            // Query m_portfolio_account_associations to find linked loan account
            String sql = "SELECT loan_account_id FROM m_portfolio_account_associations WHERE linked_savings_account_id = ?";
            List<Long> loanIds = accountDetailsService.getJdbcTemplate().queryForList(sql, Long.class, savingsAccountId);

            if (loanIds.size() == 1) {
                Long linkedLoanId = loanIds.get(0);
                log.debug("Found linked loan ID {} for savings account ID {}", linkedLoanId, savingsAccountId);
                return linkedLoanId;
            } else if (loanIds.isEmpty()) {
                log.debug("No linked loan found for savings account ID {}", savingsAccountId);
                return null;
            } else {
                log.error("Query returned {} loan associations for savings account ID {}, expected exactly 1. Loan IDs: {}", loanIds.size(),
                        savingsAccountId, loanIds);
                return null;
            }
        } catch (Exception e) {
            log.warn("Failed to get linked loan ID for savings account ID: {}", savingsAccountId, e);
            return null;
        }
    }

    @Transactional
    public void markAsPosted(Long journalEntryId, Long odooMoveId) {
        journalEntryOdooSyncRepository.findByJournalEntryId(journalEntryId).ifPresent(sync -> {
            sync.markAsPosted(odooMoveId);
            journalEntryOdooSyncRepository.save(sync);
            log.info("Marked journal entry {} as posted to Odoo with move ID: {}", journalEntryId, odooMoveId);
        });
    }

    @Transactional
    public void markAsFailed(Long journalEntryId, String errorMessage) {
        journalEntryOdooSyncRepository.findByJournalEntryId(journalEntryId).ifPresent(sync -> {
            sync.markAsFailed(errorMessage);
            journalEntryOdooSyncRepository.save(sync);
            log.warn("Marked journal entry {} as failed to post to Odoo: {}", journalEntryId, errorMessage);
        });
    }

    /**
     * Check if the current journal entry has specific GL codes (100006 or 210002) This is used to determine if a
     * SAVINGS_DEPOSIT should be categorized as SAVINGS_DEPOSIT_TO_CASH_MARGIN
     */
    private boolean hasCashMarginGLCodes(Long savingsTransactionId, String glCode) {
        if (savingsTransactionId == null || glCode == null) {
            return false;
        }

        // Check if the current journal entry has one of the specific GL codes
        boolean hasSpecificCode = "100006".equals(glCode) || "210002".equals(glCode);

        log.debug("Savings transaction {} with GL code {} has specific code (100006 or 210002): {}", savingsTransactionId, glCode,
                hasSpecificCode);

        return hasSpecificCode;
    }
}
