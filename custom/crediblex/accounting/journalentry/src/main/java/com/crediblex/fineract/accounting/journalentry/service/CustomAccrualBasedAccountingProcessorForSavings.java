package com.crediblex.fineract.accounting.journalentry.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.common.AccountingConstants.AccrualAccountsForSavings;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.data.SavingsDTO;
import org.apache.fineract.accounting.journalentry.data.SavingsTransactionDTO;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.journalentry.service.AccrualBasedAccountingProcessorForSavings;
import org.apache.fineract.organisation.office.domain.Office;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Custom Accrual-Based Accounting Processor for Savings
 *
 * Hardcoded RBF logic: For RBF savings products, uses GL 200040 instead of LIABILITY_TRANSFER when loan is disbursed to
 * savings.
 */
@Slf4j
@Primary
@Component
public class CustomAccrualBasedAccountingProcessorForSavings extends AccrualBasedAccountingProcessorForSavings {

    // Hardcoded RBF Configuration
    private static final String RBF_PRODUCT_SHORT_NAME = "RBF"; // RBF loan product short_name
    private static final String RBF_GL_CODE = "200040"; // Loan Payable - Working Capital - Revenue Finance

    private final AccountingProcessorHelper helper;
    private final GLAccountRepository glAccountRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CustomAccrualBasedAccountingProcessorForSavings(AccountingProcessorHelper accountingProcessorHelper,
            GLAccountRepository glAccountRepository, JdbcTemplate jdbcTemplate) {
        super(accountingProcessorHelper);
        this.helper = accountingProcessorHelper;
        this.glAccountRepository = glAccountRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createJournalEntriesForSavings(final SavingsDTO savingsDTO) {
        final GLClosure latestGLClosure = this.helper.getLatestClosureByBranch(savingsDTO.getOfficeId());
        final Long savingsProductId = savingsDTO.getSavingsProductId();
        final Long savingsId = savingsDTO.getSavingsId();
        final String currencyCode = savingsDTO.getCurrencyCode();

        // Track transactions that were processed by custom logic to avoid duplicate processing
        java.util.List<Integer> processedTransactionIndices = new java.util.ArrayList<>();

        for (int i = 0; i < savingsDTO.getNewSavingsTransactions().size(); i++) {
            final SavingsTransactionDTO savingsTransactionDTO = savingsDTO.getNewSavingsTransactions().get(i);
            final LocalDate transactionDate = savingsTransactionDTO.getTransactionDate();
            final String transactionId = savingsTransactionDTO.getTransactionId();
            final Office office = this.helper.getOfficeById(savingsTransactionDTO.getOfficeId());
            final Long paymentTypeId = savingsTransactionDTO.getPaymentTypeId();
            final boolean isReversal = savingsTransactionDTO.isReversed();
            final BigDecimal amount = savingsTransactionDTO.getAmount();

            this.helper.checkForBranchClosures(latestGLClosure, transactionDate);

            // Custom logic: Handle RBF deposits/withdrawals with account transfer
            // For RBF products: DO NOT create any journal entries on savings side
            // Journal entries will only be created on the loan side
            if (savingsTransactionDTO.getTransactionType().isDeposit() && savingsTransactionDTO.isAccountTransfer()) {
                // Check if the linked loan product is RBF (not the savings product)
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomAccrualBasedAccountingProcessorForSavings: RBF loan product detected - Skipping journal entries for deposit (loan disbursement)");
                    // Skip journal entry creation for RBF deposits
                    processedTransactionIndices.add(i);
                    continue;
                }
                // Non-RBF: Use default parent logic - mark for later processing
                processedTransactionIndices.add(i);
            }
            // Custom logic: Handle RBF withdrawals with account transfer (loan repayment from savings)
            else if (savingsTransactionDTO.getTransactionType().isWithdrawal() && savingsTransactionDTO.isAccountTransfer()) {
                // Check if the linked loan product is RBF (not the savings product)
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomAccrualBasedAccountingProcessorForSavings: RBF loan product detected - Skipping journal entries for withdrawal (loan repayment)");
                    // Skip journal entry creation for RBF withdrawals
                    processedTransactionIndices.add(i);
                    continue;
                }
                // Non-RBF: Use default parent logic - mark for later processing
                processedTransactionIndices.add(i);
            } else if (savingsTransactionDTO.getTransactionType().isDeposit() && !savingsTransactionDTO.isAccountTransfer()) {
                // Normal deposits (not account transfers) - use default parent logic
                // Note: Payment type 5 (RBF Loan Disbursement) is only used for account transfers (disbursements)
                // and withdrawals (repayments), so it won't appear in normal deposits
                processedTransactionIndices.add(i);
            } else if (savingsTransactionDTO.getTransactionType().isWithdrawal() && !savingsTransactionDTO.isAccountTransfer()
                    && paymentTypeId != null && paymentTypeId == 5L) {
                // RBF Loan Repayment withdrawal: DR 200040 (RBF Loan Payable), CR 100003 (Bank)
                // For reversals: CR 200040 (RBF Loan Payable), DR 100003 (Bank) - swap the entries
                GLAccount rbfGLAccount = getRBFGLAccount();
                GLAccount bankAccount = getLinkedGLAccountForSavingsProduct(savingsProductId,
                        AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(), paymentTypeId);
                if (rbfGLAccount != null && bankAccount != null) {
                    if (isReversal) {
                        // Reversal: Swap DR/CR - CR 200040, DR 100003
                        this.helper.createCreditJournalEntryForSavings(office, currencyCode, rbfGLAccount, savingsId, transactionId,
                                transactionDate, amount);
                        this.helper.createDebitJournalEntryForSavings(office, currencyCode, bankAccount, savingsId, transactionId,
                                transactionDate, amount);
                        log.info("CustomAccrualBasedAccountingProcessorForSavings: RBF withdrawal REVERSAL - CR 200040, DR 100003");
                    } else {
                        // Normal: DR 200040, CR 100003
                        this.helper.createDebitJournalEntryForSavings(office, currencyCode, rbfGLAccount, savingsId, transactionId,
                                transactionDate, amount);
                        this.helper.createCreditJournalEntryForSavings(office, currencyCode, bankAccount, savingsId, transactionId,
                                transactionDate, amount);
                        log.info("CustomAccrualBasedAccountingProcessorForSavings: RBF withdrawal - DR 200040, CR 100003");
                    }
                    processedTransactionIndices.add(i);
                } else {
                    log.error("RBF GL Account 200040 or Bank Account not found, using default logic for this transaction");
                    // Do NOT mark as processed - let parent handle it
                }
            }
            // For all other transaction types that were not explicitly handled, they will be processed by parent below
        }

        // IMPORTANT: Only delegate to parent for transactions that were NOT processed by custom logic
        // This prevents duplicate GL entries for handled transaction types
        if (processedTransactionIndices.size() < savingsDTO.getNewSavingsTransactions().size()) {
            // Create a filtered list with only unprocessed transactions
            java.util.List<SavingsTransactionDTO> unprocessedTransactions = new java.util.ArrayList<>();
            for (int i = 0; i < savingsDTO.getNewSavingsTransactions().size(); i++) {
                if (!processedTransactionIndices.contains(i)) {
                    unprocessedTransactions.add(savingsDTO.getNewSavingsTransactions().get(i));
                }
            }

            if (!unprocessedTransactions.isEmpty()) {
                log.debug("CustomAccrualBasedAccountingProcessorForSavings: {} transactions were handled by custom logic, delegating {} unprocessed transactions to parent processor",
                        processedTransactionIndices.size(), unprocessedTransactions.size());

                // Call parent's processing logic for handled transactions that use default logic
                for (int i = 0; i < savingsDTO.getNewSavingsTransactions().size(); i++) {
                    if (processedTransactionIndices.contains(i)) {
                        SavingsTransactionDTO tx = savingsDTO.getNewSavingsTransactions().get(i);
                        if ((tx.getTransactionType().isDeposit() && tx.isAccountTransfer()) ||
                            (tx.getTransactionType().isWithdrawal() && tx.isAccountTransfer()) ||
                            (tx.getTransactionType().isDeposit() && !tx.isAccountTransfer())) {
                            // These need parent logic
                            unprocessedTransactions.add(tx);
                        }
                    }
                }
            }

            if (!unprocessedTransactions.isEmpty()) {
                super.createJournalEntriesForSavings(savingsDTO);
            }
        }
    }

    /**
     * Get the linked loan product ID from the savings account When a loan is disbursed to savings, the savings account
     * is linked to a loan account
     */
    private Long getLinkedLoanProductId(Long savingsId) {
        if (savingsId == null) {
            return null;
        }

        try {
            // Query the linked loan product from m_portfolio_account_associations
            // When loan is linked to savings: loan_account_id = loan ID, linked_savings_account_id = savings ID
            String sql = "SELECT la.product_id FROM m_portfolio_account_associations paa "
                    + "JOIN m_loan la ON la.id = paa.loan_account_id "
                    + "WHERE paa.linked_savings_account_id = ? AND paa.association_type_enum = 1 AND paa.is_active = true LIMIT 1";
            Long loanProductId = jdbcTemplate.queryForObject(sql, Long.class, savingsId);
            log.info("CustomAccrualBasedAccountingProcessorForSavings: Found linked loan product {} for savingsId {}", loanProductId,
                    savingsId);
            return loanProductId;
        } catch (Exception e) {
            log.warn("CustomAccrualBasedAccountingProcessorForSavings: Error finding linked loan product for savingsId {}: {}", savingsId,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Check if loan product is RBF Queries product short_name from database to identify RBF products
     */
    private boolean isRBFLoanProduct(Long loanProductId) {
        if (loanProductId == null) {
            return false;
        }

        try {
            // Query product short_name from database
            String sql = "SELECT short_name FROM m_product_loan WHERE id = ?";
            String shortName = this.jdbcTemplate.queryForObject(sql, String.class, loanProductId);
            return RBF_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (Exception e) {
            log.debug("CustomAccrualBasedAccountingProcessorForSavings: Error checking RBF loan product for loanProductId {}: {}",
                    loanProductId, e.getMessage());
            return false;
        }
    }

    /**
     * Get GL 200040 account (RBF Loan Payable) Looks up by GL code to avoid hardcoding account ID
     */
    private GLAccount getRBFGLAccount() {
        try {
            return glAccountRepository.findOneByGlCode(RBF_GL_CODE).orElse(null);
        } catch (Exception e) {
            log.error("CustomAccrualBasedAccountingProcessorForSavings: Error finding GL account {}: {}", RBF_GL_CODE, e.getMessage());
            return null;
        }
    }

    /**
     * Get SAVINGS_CONTROL account for the savings product
     */
    private GLAccount getSavingsControlAccount(Long savingsProductId, Long paymentTypeId) {
        try {
            // Query the ProductToGLAccountMapping directly
            // Note: Column name is 'payment_type', not 'payment_type_id'
            Long glAccountId = null;

            // Try with payment type first (if provided)
            if (paymentTypeId != null) {
                String sql = "SELECT gl_account_id FROM acc_product_mapping WHERE product_id = ? AND product_type = ? AND financial_account_type = ? AND payment_type = ? LIMIT 1";
                try {
                    glAccountId = jdbcTemplate.queryForObject(sql, Long.class, savingsProductId, 2, // SAVING product
                                                                                                    // type
                            AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(), paymentTypeId);
                } catch (Exception e) {
                    log.debug("No mapping found with payment type {}, will try without: {}", paymentTypeId, e.getMessage());
                }
            }

            // If not found with payment type, try without payment type (payment_type IS NULL)
            if (glAccountId == null) {
                String sql = "SELECT gl_account_id FROM acc_product_mapping WHERE product_id = ? AND product_type = ? AND financial_account_type = ? AND payment_type IS NULL LIMIT 1";
                glAccountId = jdbcTemplate.queryForObject(sql, Long.class, savingsProductId, 2,
                        AccrualAccountsForSavings.SAVINGS_CONTROL.getValue());
            }

            if (glAccountId != null) {
                return glAccountRepository.findById(glAccountId).orElse(null);
            }
            return null;
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("CustomAccrualBasedAccountingProcessorForSavings: Error finding SAVINGS_CONTROL account: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get linked GL account for savings product
     */
    private GLAccount getLinkedGLAccountForSavingsProduct(Long savingsProductId, int accountMappingTypeId, Long paymentTypeId) {
        try {
            Long glAccountId = null;
            if (paymentTypeId != null) {
                String sql = "SELECT gl_account_id FROM acc_product_mapping WHERE product_id = ? AND product_type = ? AND financial_account_type = ? AND payment_type = ? LIMIT 1";
                try {
                    glAccountId = jdbcTemplate.queryForObject(sql, Long.class, savingsProductId, 2, accountMappingTypeId, paymentTypeId);
                } catch (Exception e) {
                    log.debug("No mapping found with payment type, will try without");
                }
            }
            if (glAccountId == null) {
                String sql = "SELECT gl_account_id FROM acc_product_mapping WHERE product_id = ? AND product_type = ? AND financial_account_type = ? AND payment_type IS NULL LIMIT 1";
                glAccountId = jdbcTemplate.queryForObject(sql, Long.class, savingsProductId, 2, accountMappingTypeId);
            }
            if (glAccountId != null) {
                return glAccountRepository.findById(glAccountId).orElse(null);
            }
            return null;
        } catch (Exception e) {
            log.error("Error finding GL account for savings product: {}", e.getMessage());
            return null;
        }
    }
}
