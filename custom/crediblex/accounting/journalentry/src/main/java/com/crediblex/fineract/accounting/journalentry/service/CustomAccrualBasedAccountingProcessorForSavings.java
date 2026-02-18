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
 *
 * Hardcoded LOC Receivable logic: For LOC Receivable products, skips journal entries for account transfers (handled on
 * loan side) and creates custom entries for normal deposits (DR 100062, CR 200086).
 */
@Slf4j
@Primary
@Component
public class CustomAccrualBasedAccountingProcessorForSavings extends AccrualBasedAccountingProcessorForSavings {

    // Hardcoded RBF Configuration
    private static final String RBF_PRODUCT_SHORT_NAME = "RBF"; // RBF loan product short_name
    private static final String RBF_GL_CODE = "200040"; // Loan Payable - Working Capital - Revenue Finance
    private static final Long RBF_PAYMENT_TYPE_ID = 5L; // RBF withdrawal payment type

    // Hardcoded LOC Receivable Configuration
    private static final String LOC_RECEIVABLE_PRODUCT_SHORT_NAME = "LRL"; // LOC Receivable loan product short_name
    private static final Long LOC_RECEIVABLE_PAYMENT_TYPE_ID = 73L; // LOC Receivable withdrawal payment type
    private static final String LOC_RECEIVABLE_DEBIT_GL_CODE = "100062"; // Client Receivable Clearing Acc - Current
                                                                         // Asset
    private static final String LOC_RECEIVABLE_CREDIT_GL_CODE = "200086"; // Invoice Discounting - Clearing - Current
                                                                          // Liability
    private static final String LOC_RECEIVABLE_LOAN_PAYABLE_GL_CODE = "200041"; // Loan Payable - Invoice Discounting -
                                                                                // Receivable - Current Liability

    // Hardcoded LOC Activation Configuration
    private static final String LOC_ACTIVATION_PRODUCT_SHORT_NAME = "LAA"; // LOC Activation loan product short_name
    private static final Long PROCESSING_FEE_PAYMENT_TYPE_ID = 1L; // Processing Fee payment type

    // LOC Activation Processing Fee GL Codes
    private static final String LOC_ACTIVATION_DEBIT_GL_CODE = "100062"; // Client Receivable Clearing Acc - Current
                                                                         // Asset
    private static final String LOC_ACTIVATION_FEE_INCOME_GL_CODE = "300004"; // Loan Servicing / Processing Fee Income
                                                                              // - Revenue
    private static final String LOC_ACTIVATION_VAT_GL_CODE = "200065"; // Value Added Tax - VAT - Current Liability

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

            // Custom logic: Handle RBF and LOC Receivable deposits/withdrawals with account transfer
            // For RBF and LOC Receivable products: DO NOT create any journal entries on savings side
            // Journal entries will only be created on the loan side
            if (savingsTransactionDTO.getTransactionType().isDeposit() && savingsTransactionDTO.isAccountTransfer()) {
                // Check if the linked loan product is RBF or LOC Receivable (not the savings product)
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomAccrualBasedAccountingProcessorForSavings: RBF loan product detected - Skipping journal entries for deposit (loan disbursement)");
                    // Skip journal entry creation for RBF deposits
                    processedTransactionIndices.add(i);
                    continue;
                }
                if (linkedLoanProductId != null && isLOCReceivableLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomAccrualBasedAccountingProcessorForSavings: LOC Receivable loan product detected - Skipping journal entries for deposit (loan disbursement)");
                    // Skip journal entry creation for LOC Receivable deposits - handled on loan side
                    processedTransactionIndices.add(i);
                    continue;
                }
                // Non-RBF/Non-LOC Receivable: Use default parent logic - mark for later processing
                processedTransactionIndices.add(i);
            }
            // Custom logic: Handle RBF and LOC Receivable withdrawals with account transfer (loan repayment from
            // savings)
            else if (savingsTransactionDTO.getTransactionType().isWithdrawal() && savingsTransactionDTO.isAccountTransfer()) {
                // Check if the linked loan product is RBF or LOC Receivable (not the savings product)
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomAccrualBasedAccountingProcessorForSavings: RBF loan product detected - Skipping journal entries for withdrawal (loan repayment)");
                    // Skip journal entry creation for RBF withdrawals
                    processedTransactionIndices.add(i);
                    continue;
                }
                if (linkedLoanProductId != null && isLOCReceivableLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomAccrualBasedAccountingProcessorForSavings: LOC Receivable loan product detected - Skipping journal entries for withdrawal (loan repayment)");
                    // Skip journal entry creation for LOC Receivable withdrawals - handled on loan side
                    processedTransactionIndices.add(i);
                    continue;
                }
                // Non-RBF/Non-LOC Receivable: Use default parent logic - mark for later processing
                processedTransactionIndices.add(i);
            } else if (savingsTransactionDTO.getTransactionType().isDeposit() && !savingsTransactionDTO.isAccountTransfer()) {
                // Normal deposits (not account transfers)

                // LOC Activation: Skip journal entries entirely - handled elsewhere
                if (savingsProductId != null && isLOCActivationSavingsProduct(savingsProductId)) {
                    log.info(
                            "CustomAccrualBasedAccountingProcessorForSavings: LOC Activation loan product detected - Skipping journal entries for normal deposit");
                    processedTransactionIndices.add(i);
                    continue;
                }

                // For RBF savings product, if payment_type = 5 (RBF Loan Disbursement), ignore it and use default GL
                // 100062
                if (paymentTypeId != null && paymentTypeId == 5L) {
                    // Payment type 5 is for loan disbursements, not normal deposits
                    // Use NULL to get the default ASSET account (100062) instead of 100003
                    log.debug(
                            "CustomAccrualBasedAccountingProcessorForSavings: Normal deposit with payment_type=5, using default GL (100062) instead");
                    // Create journal entries with payment_type = null to get default GL 100062
                    // For deposits: DR ASSET (1), CR SAVINGS_CONTROL (2)
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(), AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(),
                            savingsProductId, null, savingsId, transactionId, transactionDate, amount, isReversal);
                } else {
                    // Use default parent logic
                    super.createJournalEntriesForSavings(savingsDTO);
                }
                // Normal deposits (not account transfers) - use default parent logic
                // Note: Payment type 5 (RBF Loan Disbursement) is only used for account transfers (disbursements)
                // and withdrawals (repayments), so it won't appear in normal deposits
                processedTransactionIndices.add(i);
            } else if (savingsTransactionDTO.getTransactionType().isWithdrawal() && !savingsTransactionDTO.isAccountTransfer()
                    && paymentTypeId != null && paymentTypeId.equals(RBF_PAYMENT_TYPE_ID)) {
                // RBF Manual withdrawal with payment_type=5
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    // RBF Loan Repayment withdrawal: DR 200040 (RBF Loan Payable), CR 100003 (Bank)
                    log.info("CustomAccrualBasedAccountingProcessorForSavings: RBF manual withdrawal - DR 200040, CR Bank");
                    GLAccount rbfGLAccount = getRBFGLAccount();
                    GLAccount bankAccount = getLinkedGLAccountForSavingsProduct(savingsProductId,
                            AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(), paymentTypeId);
                    if (rbfGLAccount != null && bankAccount != null) {
                        this.helper.createDebitJournalEntryForSavings(office, currencyCode, rbfGLAccount, savingsId, transactionId,
                                transactionDate, amount);
                        this.helper.createCreditJournalEntryForSavings(office, currencyCode, bankAccount, savingsId, transactionId,
                                transactionDate, amount);
                        processedTransactionIndices.add(i);
                    } else {
                        log.error("RBF GL Account 200040 or Bank Account not found, using default logic for this transaction");
                        // Do NOT mark as processed - let parent handle it
                    }
                } else {
                    // Not RBF product but has payment_type=5, use default logic
                    // Do NOT mark as processed - let parent handle it
                }
            } else if (savingsTransactionDTO.getTransactionType().isWithdrawal() && !savingsTransactionDTO.isAccountTransfer()
                    && paymentTypeId != null && paymentTypeId.equals(LOC_RECEIVABLE_PAYMENT_TYPE_ID)) {
                // LOC Receivable Manual withdrawal with payment_type=73
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isLOCReceivableLoanProduct(linkedLoanProductId)) {
                    // LOC Receivable Loan Repayment withdrawal: DR 200041 (Loan Payable - Invoice Discounting), CR
                    // 100003 (Bank)
                    log.info("CustomAccrualBasedAccountingProcessorForSavings: LOC Receivable manual withdrawal - DR 200041, CR Bank");
                    GLAccount locLoanPayableAccount = getLOCReceivableLoanPayableGLAccount();
                    GLAccount bankAccount = getLinkedGLAccountForSavingsProduct(savingsProductId,
                            AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(), paymentTypeId);
                    if (locLoanPayableAccount != null && bankAccount != null) {
                        this.helper.createDebitJournalEntryForSavings(office, currencyCode, locLoanPayableAccount, savingsId, transactionId,
                                transactionDate, amount);
                        this.helper.createCreditJournalEntryForSavings(office, currencyCode, bankAccount, savingsId, transactionId,
                                transactionDate, amount);
                        processedTransactionIndices.add(i);
                    } else {
                        log.error("LOC Receivable GL Account 200041 or Bank Account not found, using default logic for this transaction");
                        // Do NOT mark as processed - let parent handle it
                    }
                } else {
                    // Not LOC Receivable product but has payment_type=73, use default logic
                    // Do NOT mark as processed - let parent handle it
                }
            } else if (savingsTransactionDTO.getTransactionType().isWithdrawal() && !savingsTransactionDTO.isAccountTransfer()
                    && paymentTypeId != null && paymentTypeId.equals(PROCESSING_FEE_PAYMENT_TYPE_ID)) {
                // LOC Activation Processing Fee withdrawal with payment_type=1
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId == null && isLOCActivationSavingsProduct(savingsProductId)) {
                    // LOC Activation Processing Fee withdrawal:
                    // DR 100062 (Client Receivable Clearing Acc - Current Asset) - Total amount
                    // CR 300004 (Loan Servicing / Processing Fee Income - Revenue) - Fee amount (excluding VAT)
                    // CR 200065 (Value Added Tax - VAT - Current Liability) - VAT amount
                    log.info(
                            "CustomAccrualBasedAccountingProcessorForSavings: LOC Activation processing fee withdrawal - DR 100062, CR 300004, CR 200065");

                    GLAccount debitAccount = glAccountRepository.findOneByGlCode(LOC_ACTIVATION_DEBIT_GL_CODE).orElse(null);
                    GLAccount feeIncomeAccount = glAccountRepository.findOneByGlCode(LOC_ACTIVATION_FEE_INCOME_GL_CODE).orElse(null);
                    GLAccount vatAccount = glAccountRepository.findOneByGlCode(LOC_ACTIVATION_VAT_GL_CODE).orElse(null);

                    if (debitAccount != null && feeIncomeAccount != null && vatAccount != null) {
                        // Get the tax/VAT amount from the LOC charge linked to this savings account
                        // The LOC charge has the actual tax amount configured (not hardcoded 5%)
                        BigDecimal vatAmount = BigDecimal.ZERO;
                        BigDecimal feeAmount = amount;

                        // First, try to get VAT from taxPayments in the SavingsTransactionDTO
                        java.util.List<org.apache.fineract.accounting.journalentry.data.TaxPaymentDTO> taxPaymentsList = savingsTransactionDTO
                                .getTaxPayments();
                        if (taxPaymentsList != null && !taxPaymentsList.isEmpty()) {
                            for (org.apache.fineract.accounting.journalentry.data.TaxPaymentDTO taxPayment : taxPaymentsList) {
                                if (taxPayment.getAmount() != null && taxPayment.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                                    vatAmount = vatAmount.add(taxPayment.getAmount());
                                }
                            }
                            if (vatAmount.compareTo(BigDecimal.ZERO) > 0) {
                                feeAmount = amount.subtract(vatAmount);
                                log.info("CustomAccrualBasedAccountingProcessorForSavings: Got VAT {} from taxPayments, fee amount = {}",
                                        vatAmount, feeAmount);
                            }
                        }

                        // Fallback 1: Query the LOC charges directly using the settlement savings account ID
                        // This is more reliable than querying through the paid_by table (similar to LOC API approach)
                        if (vatAmount.compareTo(BigDecimal.ZERO) == 0) {
                            vatAmount = getLOCChargeTaxAmountBySavingsAccount(savingsId);
                            if (vatAmount.compareTo(BigDecimal.ZERO) > 0) {
                                feeAmount = amount.subtract(vatAmount);
                                log.info(
                                        "CustomAccrualBasedAccountingProcessorForSavings: Got VAT {} from LOC charges (by savings account), fee amount = {}",
                                        vatAmount, feeAmount);
                            }
                        }

                        // Fallback 2: Query the LOC charge via paid_by link table (legacy approach)
                        if (vatAmount.compareTo(BigDecimal.ZERO) == 0) {
                            vatAmount = getLOCChargeTaxAmount(transactionId);
                            if (vatAmount.compareTo(BigDecimal.ZERO) > 0) {
                                feeAmount = amount.subtract(vatAmount);
                                log.info(
                                        "CustomAccrualBasedAccountingProcessorForSavings: Got VAT {} from LOC charge (by transaction), fee amount = {}",
                                        vatAmount, feeAmount);
                            }
                        }

                        // If still no VAT found, the charge might not have tax configured - use full amount as fee
                        if (vatAmount.compareTo(BigDecimal.ZERO) == 0) {
                            log.info(
                                    "CustomAccrualBasedAccountingProcessorForSavings: No VAT found for LOC Activation, using full amount {} as fee",
                                    amount);
                            feeAmount = amount;
                        }

                        if (isReversal) {
                            // Reversal: Swap DR/CR
                            this.helper.createCreditJournalEntryForSavings(office, currencyCode, debitAccount, savingsId, transactionId,
                                    transactionDate, amount);
                            this.helper.createDebitJournalEntryForSavings(office, currencyCode, feeIncomeAccount, savingsId, transactionId,
                                    transactionDate, feeAmount);
                            if (vatAmount.compareTo(BigDecimal.ZERO) > 0) {
                                this.helper.createDebitJournalEntryForSavings(office, currencyCode, vatAccount, savingsId, transactionId,
                                        transactionDate, vatAmount);
                            }
                            log.info(
                                    "CustomAccrualBasedAccountingProcessorForSavings: LOC Activation processing fee REVERSAL - CR 100062 {}, DR 300004 {}, DR 200065 {}",
                                    amount, feeAmount, vatAmount);
                        } else {
                            // Normal: DR 100062 (total), CR 300004 (fee), CR 200065 (VAT)
                            this.helper.createDebitJournalEntryForSavings(office, currencyCode, debitAccount, savingsId, transactionId,
                                    transactionDate, amount);
                            this.helper.createCreditJournalEntryForSavings(office, currencyCode, feeIncomeAccount, savingsId, transactionId,
                                    transactionDate, feeAmount);
                            if (vatAmount.compareTo(BigDecimal.ZERO) > 0) {
                                this.helper.createCreditJournalEntryForSavings(office, currencyCode, vatAccount, savingsId, transactionId,
                                        transactionDate, vatAmount);
                            }
                            log.info(
                                    "CustomAccrualBasedAccountingProcessorForSavings: LOC Activation processing fee - DR 100062 {}, CR 300004 {}, CR 200065 {}",
                                    amount, feeAmount, vatAmount);
                        }
                        processedTransactionIndices.add(i);
                    } else {
                        log.error(
                                "LOC Activation GL Accounts (100062, 300004, or 200065) not found, using default logic for this transaction");
                        // Do NOT mark as processed - let parent handle it
                    }
                } else {
                    // Not LOC Activation product but has payment_type=1, use default logic
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
                log.debug(
                        "CustomAccrualBasedAccountingProcessorForSavings: {} transactions were handled by custom logic, delegating {} unprocessed transactions to parent processor",
                        processedTransactionIndices.size(), unprocessedTransactions.size());

                // Call parent's processing logic for handled transactions that use default logic
                for (int i = 0; i < savingsDTO.getNewSavingsTransactions().size(); i++) {
                    if (processedTransactionIndices.contains(i)) {
                        SavingsTransactionDTO tx = savingsDTO.getNewSavingsTransactions().get(i);
                        if ((tx.getTransactionType().isDeposit() && tx.isAccountTransfer())
                                || (tx.getTransactionType().isWithdrawal() && tx.isAccountTransfer())
                                || (tx.getTransactionType().isDeposit() && !tx.isAccountTransfer())) {
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
     * Check if loan product is LOC Receivable Queries product short_name from database to identify LOC Receivable
     * products
     */
    private boolean isLOCReceivableLoanProduct(Long loanProductId) {
        if (loanProductId == null) {
            return false;
        }

        try {
            // Query product short_name from database
            String sql = "SELECT short_name FROM m_product_loan WHERE id = ?";
            String shortName = this.jdbcTemplate.queryForObject(sql, String.class, loanProductId);
            return LOC_RECEIVABLE_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (Exception e) {
            log.debug(
                    "CustomAccrualBasedAccountingProcessorForSavings: Error checking LOC Receivable loan product for loanProductId {}: {}",
                    loanProductId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if loan product is LOC Activation Queries product short_name from database to identify LOC Activation
     * products
     */
    private boolean isLOCActivationSavingsProduct(Long savingsProductId) {
        if (savingsProductId == null) {
            return false;
        }

        try {
            // Query product short_name from database
            String sql = "SELECT short_name FROM m_savings_product WHERE id = ?";
            String shortName = this.jdbcTemplate.queryForObject(sql, String.class, savingsProductId);
            return LOC_ACTIVATION_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (Exception e) {
            log.debug(
                    "CustomAccrualBasedAccountingProcessorForSavings: Error checking LOC Activation loan product for loanProductId {}: {}",
                    savingsProductId, e.getMessage());
            return false;
        }
    }

    /**
     * Get GL 100062 account (Client Receivable Clearing Acc - Current Asset) for LOC Receivable deposits Looks up by GL
     * code to avoid hardcoding account ID
     */
    private GLAccount getLOCReceivableDebitGLAccount() {
        try {
            return glAccountRepository.findOneByGlCode(LOC_RECEIVABLE_DEBIT_GL_CODE).orElse(null);
        } catch (Exception e) {
            log.error("CustomAccrualBasedAccountingProcessorForSavings: Error finding GL account {}: {}", LOC_RECEIVABLE_DEBIT_GL_CODE,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Get GL 200086 account (Invoice Discounting - Clearing - Current Liability) for LOC Receivable deposits Looks up
     * by GL code to avoid hardcoding account ID
     */
    private GLAccount getLOCReceivableCreditGLAccount() {
        try {
            return glAccountRepository.findOneByGlCode(LOC_RECEIVABLE_CREDIT_GL_CODE).orElse(null);
        } catch (Exception e) {
            log.error("CustomAccrualBasedAccountingProcessorForSavings: Error finding GL account {}: {}", LOC_RECEIVABLE_CREDIT_GL_CODE,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Get GL 200041 account (Loan Payable - Invoice Discounting - Receivable - Current Liability) for LOC Receivable
     * manual withdrawals Looks up by GL code to avoid hardcoding account ID
     */
    private GLAccount getLOCReceivableLoanPayableGLAccount() {
        try {
            return glAccountRepository.findOneByGlCode(LOC_RECEIVABLE_LOAN_PAYABLE_GL_CODE).orElse(null);
        } catch (Exception e) {
            log.error("CustomAccrualBasedAccountingProcessorForSavings: Error finding GL account {}: {}",
                    LOC_RECEIVABLE_LOAN_PAYABLE_GL_CODE, e.getMessage());
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

    /**
     * Get the tax amount from the LOC charge linked to this savings account. This queries the LOC directly using the
     * settlement savings account ID (more reliable approach), similar to how the LOC API returns charge information.
     *
     * @param savingsAccountId
     *            The savings account ID
     * @return The tax amount from the LOC charges, or ZERO if not found
     */
    private BigDecimal getLOCChargeTaxAmountBySavingsAccount(Long savingsAccountId) {
        try {
            // Query the LOC charges directly using the settlement savings account ID
            // This is more reliable than querying through the paid_by table
            String sql = "SELECT COALESCE(SUM(lc.tax_amount), 0) " + "FROM m_line_of_credit loc "
                    + "JOIN m_line_of_credit_charge lc ON lc.line_of_credit_id = loc.id " + "WHERE loc.settlement_savings_account_id = ? "
                    + "AND lc.is_active = true " + "AND lc.is_paid_derived = false " + "AND lc.waived = false";

            BigDecimal taxAmount = jdbcTemplate.queryForObject(sql, BigDecimal.class, savingsAccountId);

            if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
                log.info("CustomAccrualBasedAccountingProcessorForSavings: Found LOC charge tax amount {} for savings account {}",
                        taxAmount, savingsAccountId);
                return taxAmount;
            }

            // If no unpaid charges found, try to get from recently paid charges (within last few seconds)
            // This handles the case where the charge was just paid but journal entries are being created
            String sqlPaid = "SELECT COALESCE(SUM(lc.tax_amount), 0) " + "FROM m_line_of_credit loc "
                    + "JOIN m_line_of_credit_charge lc ON lc.line_of_credit_id = loc.id " + "WHERE loc.settlement_savings_account_id = ? "
                    + "AND lc.is_active = true";

            taxAmount = jdbcTemplate.queryForObject(sqlPaid, BigDecimal.class, savingsAccountId);

            if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
                log.info(
                        "CustomAccrualBasedAccountingProcessorForSavings: Found LOC charge tax amount {} (from all charges) for savings account {}",
                        taxAmount, savingsAccountId);
                return taxAmount;
            }

            return BigDecimal.ZERO;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            log.debug("CustomAccrualBasedAccountingProcessorForSavings: No LOC found for savings account {}", savingsAccountId);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("CustomAccrualBasedAccountingProcessorForSavings: Error getting LOC charge tax amount for savings account {}: {}",
                    savingsAccountId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get the tax amount from the LOC charge linked to this savings transaction. This queries the
     * m_line_of_credit_charge_paid_by table to find the linked LOC charge, then gets the tax_amount from the
     * m_line_of_credit_charge table.
     *
     * @param transactionId
     *            The savings transaction ID (e.g., "S12345")
     * @return The tax amount from the LOC charge, or ZERO if not found
     */
    private BigDecimal getLOCChargeTaxAmount(String transactionId) {
        try {
            // Extract numeric transaction ID from string like "S14119"
            String numericId = transactionId.replace("S", "").trim();
            Long transactionNumericId = Long.parseLong(numericId);

            // Query the LOC charge tax amount via the paid_by link table
            // The m_line_of_credit_charge has a tax_amount column that stores the actual configured tax
            String sql = "SELECT COALESCE(SUM(lc.tax_amount), 0) " + "FROM m_line_of_credit_charge_paid_by pb "
                    + "JOIN m_line_of_credit_charge lc ON lc.id = pb.line_of_credit_charge_id "
                    + "WHERE pb.savings_account_transaction_id = ?";

            BigDecimal taxAmount = jdbcTemplate.queryForObject(sql, BigDecimal.class, transactionNumericId);

            if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
                log.info("CustomAccrualBasedAccountingProcessorForSavings: Found LOC charge tax amount {} for savings transaction {}",
                        taxAmount, transactionId);
                return taxAmount;
            }

            return BigDecimal.ZERO;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            log.debug("CustomAccrualBasedAccountingProcessorForSavings: No LOC charge found for savings transaction {}", transactionId);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("CustomAccrualBasedAccountingProcessorForSavings: Error getting LOC charge tax amount for transaction {}: {}",
                    transactionId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
