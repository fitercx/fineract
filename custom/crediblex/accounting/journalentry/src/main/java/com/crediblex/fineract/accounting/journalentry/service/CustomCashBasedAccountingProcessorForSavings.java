package com.crediblex.fineract.accounting.journalentry.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.common.AccountingConstants.CashAccountsForSavings;
import org.apache.fineract.accounting.common.AccountingConstants.FinancialActivity;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.data.ChargePaymentDTO;
import org.apache.fineract.accounting.journalentry.data.SavingsDTO;
import org.apache.fineract.accounting.journalentry.data.SavingsTransactionDTO;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.journalentry.service.CashBasedAccountingProcessorForSavings;
import org.apache.fineract.organisation.office.domain.Office;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Custom Cash-Based Accounting Processor for Savings
 *
 * Hardcoded RBF logic: For RBF savings products, uses GL 200040 instead of LIABILITY_TRANSFER when loan is disbursed to
 * savings.
 */
@Slf4j
@Primary
@Component
public class CustomCashBasedAccountingProcessorForSavings extends CashBasedAccountingProcessorForSavings {

    // Hardcoded RBF Configuration
    private static final String RBF_PRODUCT_SHORT_NAME = "RBF"; // RBF loan product short_name
    private static final String RBF_GL_CODE = "200040"; // Loan Payable - Working Capital - Revenue Finance

    private final AccountingProcessorHelper helper;
    private final GLAccountRepository glAccountRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CustomCashBasedAccountingProcessorForSavings(AccountingProcessorHelper accountingProcessorHelper,
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
        for (final SavingsTransactionDTO savingsTransactionDTO : savingsDTO.getNewSavingsTransactions()) {
            final LocalDate transactionDate = savingsTransactionDTO.getTransactionDate();
            final String transactionId = savingsTransactionDTO.getTransactionId();
            final Office office = this.helper.getOfficeById(savingsTransactionDTO.getOfficeId());
            final Long paymentTypeId = savingsTransactionDTO.getPaymentTypeId();
            final boolean isReversal = savingsTransactionDTO.isReversed();
            final BigDecimal amount = savingsTransactionDTO.getAmount();
            final BigDecimal overdraftAmount = savingsTransactionDTO.getOverdraftAmount();
            final List<ChargePaymentDTO> feePayments = savingsTransactionDTO.getFeePayments();
            final List<ChargePaymentDTO> penaltyPayments = savingsTransactionDTO.getPenaltyPayments();

            this.helper.checkForBranchClosures(latestGLClosure, transactionDate);

            // Custom logic: Handle RBF deposits/withdrawals with account transfer
            // For RBF products: DO NOT create any journal entries on savings side
            // Journal entries will only be created on the loan side
            if (savingsTransactionDTO.getTransactionType().isDeposit() && savingsTransactionDTO.isAccountTransfer()) {
                // Check if the linked loan product is RBF (not the savings product)
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: RBF loan product detected - Skipping journal entries for deposit (loan disbursement)");
                    // Skip journal entry creation for RBF deposits
                    continue;
                }
                // Non-RBF: Use default parent logic
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        FinancialActivity.LIABILITY_TRANSFER.getValue(), CashAccountsForSavings.SAVINGS_CONTROL.getValue(),
                        savingsProductId, paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
            }
            // Custom logic: Handle RBF withdrawals with account transfer (loan repayment from savings)
            else if (savingsTransactionDTO.getTransactionType().isWithdrawal() && savingsTransactionDTO.isAccountTransfer()) {
                // Check if the linked loan product is RBF (not the savings product)
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: RBF loan product detected - Skipping journal entries for withdrawal (loan repayment)");
                    // Skip journal entry creation for RBF withdrawals
                    continue;
                }
                // Non-RBF: Use default parent logic
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CashAccountsForSavings.SAVINGS_CONTROL.getValue(), FinancialActivity.LIABILITY_TRANSFER.getValue(),
                        savingsProductId, paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
            } else if (savingsTransactionDTO.getTransactionType().isDeposit() && !savingsTransactionDTO.isAccountTransfer()) {
                // Normal deposits (not account transfers) - ensure they use GL 100062, not payment_type-specific GL
                // For RBF savings product, if payment_type = 5 (RBF Loan Disbursement), ignore it and use default GL
                // 100062
                Long effectivePaymentTypeId = paymentTypeId;
                if (paymentTypeId != null && paymentTypeId == 5L) {
                    // Payment type 5 is for loan disbursements, not normal deposits
                    // Use NULL to get the default ASSET account (100062)
                    effectivePaymentTypeId = null;
                    log.debug(
                            "CustomCashBasedAccountingProcessorForSavings: Normal deposit with payment_type=5, using default GL (100062) instead");
                }
                // Use default parent logic with corrected payment type
                // For deposits: DR ASSET (1), CR SAVINGS_CONTROL (2)
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CashAccountsForSavings.SAVINGS_REFERENCE.getValue(), CashAccountsForSavings.SAVINGS_CONTROL.getValue(),
                        savingsProductId, effectivePaymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
            } else {
                // For all other transaction types, delegate to parent
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
            log.info("CustomCashBasedAccountingProcessorForSavings: Found linked loan product {} for savingsId {}", loanProductId,
                    savingsId);
            return loanProductId;
        } catch (Exception e) {
            log.warn("CustomCashBasedAccountingProcessorForSavings: Error finding linked loan product for savingsId {}: {}", savingsId,
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
            log.debug("CustomCashBasedAccountingProcessorForSavings: Error checking RBF loan product for loanProductId {}: {}",
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
            log.error("CustomCashBasedAccountingProcessorForSavings: Error finding GL account {}: {}", RBF_GL_CODE, e.getMessage());
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
                            CashAccountsForSavings.SAVINGS_CONTROL.getValue(), paymentTypeId);
                } catch (Exception e) {
                    // No mapping with payment type, will try without
                }
            }

            // If not found with payment type, try without payment type (payment_type IS NULL)
            if (glAccountId == null) {
                String sql = "SELECT gl_account_id FROM acc_product_mapping WHERE product_id = ? AND product_type = ? AND financial_account_type = ? AND payment_type IS NULL LIMIT 1";
                glAccountId = jdbcTemplate.queryForObject(sql, Long.class, savingsProductId, 2,
                        CashAccountsForSavings.SAVINGS_CONTROL.getValue());
            }

            if (glAccountId != null) {
                return glAccountRepository.findById(glAccountId).orElse(null);
            }
            return null;
        } catch (Exception e) {
            log.warn("CustomCashBasedAccountingProcessorForSavings: Error finding SAVINGS_CONTROL account: {}", e.getMessage());
            return null;
        }
    }
}
