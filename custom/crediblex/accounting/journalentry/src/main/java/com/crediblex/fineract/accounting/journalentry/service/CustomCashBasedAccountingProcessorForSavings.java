package com.crediblex.fineract.accounting.journalentry.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Custom Cash-Based Accounting Processor for Savings
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
public class CustomCashBasedAccountingProcessorForSavings extends CashBasedAccountingProcessorForSavings {

    // Hardcoded RBF Configuration
    private static final String RBF_PRODUCT_SHORT_NAME = "RBF"; // RBF loan product short_name
    private static final String RBF_GL_CODE = "200040"; // Loan Payable - Working Capital - Revenue Finance

    // Hardcoded LOC Receivable Configuration
    private static final String LOC_RECEIVABLE_PRODUCT_SHORT_NAME = "LRL"; // LOC Receivable loan product short_name
    private static final String LOC_RECEIVABLE_DEBIT_GL_CODE = "100062"; // Client Receivable Clearing Acc - Current
                                                                         // Asset
    private static final String LOC_RECEIVABLE_CREDIT_GL_CODE = "200086"; // Invoice Discounting - Clearing - Current
                                                                          // Liability
    private static final String LOC_RECEIVABLE_LOAN_PAYABLE_GL_CODE = "200041"; // Loan Payable - Invoice Discounting -
                                                                                // Receivable - Current Liability

    // Payment Type IDs
    private static final Long RBF_PAYMENT_TYPE_ID = 5L; // RBF payment type
    private static final Long LOC_RECEIVABLE_PAYMENT_TYPE_ID = 73L; // LOC Receivable payment type

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

        // Track transactions that were processed by custom logic to avoid duplicate processing
        List<Integer> processedTransactionIndices = new ArrayList<>();

        for (int i = 0; i < savingsDTO.getNewSavingsTransactions().size(); i++) {
            final SavingsTransactionDTO savingsTransactionDTO = savingsDTO.getNewSavingsTransactions().get(i);
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

            // Custom logic: Handle RBF and LOC Receivable deposits/withdrawals with account transfer
            // For RBF and LOC Receivable products: DO NOT create any journal entries on savings side
            // Journal entries will only be created on the loan side
            if (savingsTransactionDTO.getTransactionType().isDeposit() && savingsTransactionDTO.isAccountTransfer()) {
                // Check if the linked loan product is RBF or LOC Receivable (not the savings product)
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: RBF loan product detected - Skipping journal entries for deposit (loan disbursement)");
                    // Skip journal entry creation for RBF deposits
                    processedTransactionIndices.add(i);
                    continue;
                }
                if (linkedLoanProductId != null && isLOCReceivableLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: LOC Receivable loan product detected - Skipping journal entries for deposit (loan disbursement)");
                    // Skip journal entry creation for LOC Receivable deposits - handled on loan side
                    continue;
                }
                if (linkedLoanProductId != null && isLOCReceivableLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: LOC Receivable loan product detected - Skipping journal entries for deposit (loan disbursement)");
                    // Skip journal entry creation for LOC Receivable deposits - handled on loan side
                    continue;
                }
                // Non-RBF/Non-LOC Receivable: Use default parent logic
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        FinancialActivity.LIABILITY_TRANSFER.getValue(), CashAccountsForSavings.SAVINGS_CONTROL.getValue(),
                        savingsProductId, paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
                processedTransactionIndices.add(i);
            }
            // Custom logic: Handle RBF and LOC Receivable withdrawals with account transfer (loan repayment from
            // savings)
            else if (savingsTransactionDTO.getTransactionType().isWithdrawal() && savingsTransactionDTO.isAccountTransfer()) {
                // Check if the linked loan product is RBF or LOC Receivable (not the savings product)
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: RBF loan product detected - Skipping journal entries for withdrawal (loan repayment)");
                    // Skip journal entry creation for RBF withdrawals
                    processedTransactionIndices.add(i);
                    continue;
                }
                if (linkedLoanProductId != null && isLOCReceivableLoanProduct(linkedLoanProductId)) {
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: LOC Receivable loan product detected - Skipping journal entries for withdrawal (loan repayment)");
                    // Skip journal entry creation for LOC Receivable withdrawals - handled on loan side
                    continue;
                }
                // Non-RBF/Non-LOC Receivable: Use default parent logic
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CashAccountsForSavings.SAVINGS_CONTROL.getValue(), FinancialActivity.LIABILITY_TRANSFER.getValue(),
                        savingsProductId, paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
                processedTransactionIndices.add(i);
            } else if (savingsTransactionDTO.getTransactionType().isDeposit() && !savingsTransactionDTO.isAccountTransfer()) {
                // Normal deposits (not account transfers)
                // Check if this is a LOC Receivable linked savings account
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isLOCReceivableLoanProduct(linkedLoanProductId)) {
                    // LOC Receivable Normal Deposit (Repayment): DR 100062 (Client Receivable Clearing), CR 200086
                    // (Invoice Discounting Clearing)
                    log.info("CustomCashBasedAccountingProcessorForSavings: LOC Receivable normal deposit - DR 100062, CR 200086");
                    GLAccount debitAccount = getLOCReceivableDebitGLAccount();
                    GLAccount creditAccount = getLOCReceivableCreditGLAccount();
                    if (debitAccount != null && creditAccount != null) {
                        this.helper.createDebitJournalEntryForSavings(office, currencyCode, debitAccount, savingsId, transactionId,
                                transactionDate, amount);
                        this.helper.createCreditJournalEntryForSavings(office, currencyCode, creditAccount, savingsId, transactionId,
                                transactionDate, amount);
                    } else {
                        log.error("LOC Receivable GL Accounts (100062 or 200086) not found, using default logic");
                        super.createJournalEntriesForSavings(savingsDTO);
                    }
                    continue;
                }
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
                // Normal deposits (not account transfers)
                // Check if this is a LOC Receivable linked savings account
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);
                if (linkedLoanProductId != null && isLOCReceivableLoanProduct(linkedLoanProductId)) {
                    // LOC Receivable Normal Deposit (Repayment): DR 100062 (Client Receivable Clearing), CR 200086
                    // (Invoice Discounting Clearing)
                    log.info("CustomCashBasedAccountingProcessorForSavings: LOC Receivable normal deposit - DR 100062, CR 200086");
                    GLAccount debitAccount = getLOCReceivableDebitGLAccount();
                    GLAccount creditAccount = getLOCReceivableCreditGLAccount();
                    if (debitAccount != null && creditAccount != null) {
                        this.helper.createDebitJournalEntryForSavings(office, currencyCode, debitAccount, savingsId, transactionId,
                                transactionDate, amount);
                        this.helper.createCreditJournalEntryForSavings(office, currencyCode, creditAccount, savingsId, transactionId,
                                transactionDate, amount);
                    } else {
                        log.error("LOC Receivable GL Accounts (100062 or 200086) not found, using default logic");
                        super.createJournalEntriesForSavings(savingsDTO);
                    }
                    continue;
                }
                // Check if this is a charge reversal deposit FIRST (before creating any GL entries)
                // Charge reversals use transaction type CHARGE_REVERSAL
                // For charge reversals: DR 100062, CR 210003
                boolean isChargeReversalDeposit = isChargeReversalTransaction(savingsTransactionDTO);

                if (isChargeReversalDeposit) {
                    // Charge reversal deposit: Use GL 100062 and 210003
                    // DR: 100062 (Client Receivable Clearing Acc)
                    // CR: 210003 (Working Capital Loan / SAVINGS_CONTROL)
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: Charge reversal deposit detected for transaction {}, using GL 100062 and 210003",
                            transactionId);

                    GLAccount debitAccount = glAccountRepository.findOneByGlCode("100062")
                            .orElseThrow(() -> new RuntimeException("GL Account 100062 (Client Receivable Clearing Acc) not found"));

                    // Get SAVINGS_CONTROL account (should be 210003 for RBF)
                    GLAccount creditAccount = getSavingsControlAccount(savingsProductId, paymentTypeId);
                    if (creditAccount == null) {
                        // Fallback to default SAVINGS_CONTROL lookup
                        creditAccount = glAccountRepository.findOneByGlCode("210003")
                                .orElseThrow(() -> new RuntimeException("GL Account 210003 (Working Capital Loan) not found"));
                    }

                    // Create manual journal entries: DR 100062, CR 210003 (only once, no duplicates)
                    this.helper.createDebitJournalEntryForSavings(office, currencyCode, debitAccount, savingsId, transactionId,
                            transactionDate, amount);
                    this.helper.createCreditJournalEntryForSavings(office, currencyCode, creditAccount, savingsId, transactionId,
                            transactionDate, amount);
                    // IMPORTANT: Mark as processed to prevent calling parent/default logic which would create
                    // duplicates
                    processedTransactionIndices.add(i);
                } else {
                    // Normal deposits (not account transfers) - use default GL 100062
                    // For RBF savings product, if payment_type = 5 (RBF Loan Disbursement), ignore it and use default
                    // GL 100062
                    Long effectivePaymentTypeId = paymentTypeId;
                    if (paymentTypeId != null && paymentTypeId == 5L) {
                        // Payment type 5 is for loan disbursements, not normal deposits
                        // Use NULL to get the default ASSET account (100062)
                        effectivePaymentTypeId = null;
                        log.debug(
                                "CustomCashBasedAccountingProcessorForSavings: Normal deposit with payment_type=5, using default GL (100062) instead");
                    }
                    // For deposits: DR ASSET (1), CR SAVINGS_CONTROL (2)
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            CashAccountsForSavings.SAVINGS_REFERENCE.getValue(), CashAccountsForSavings.SAVINGS_CONTROL.getValue(),
                            savingsProductId, effectivePaymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
                    processedTransactionIndices.add(i);
                }
            } else if (savingsTransactionDTO.getTransactionType().isWithdrawal() && !savingsTransactionDTO.isAccountTransfer()
                    && paymentTypeId != null
                    && (paymentTypeId.equals(RBF_PAYMENT_TYPE_ID) || paymentTypeId.equals(LOC_RECEIVABLE_PAYMENT_TYPE_ID))) {
                // Manual withdrawal with payment_type=5 (RBF) or payment_type=73 (LOC Receivable)
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);

                if (paymentTypeId.equals(RBF_PAYMENT_TYPE_ID) && linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    // RBF Loan Repayment withdrawal: DR 200040 (RBF Loan Payable), CR 100003 (Bank)
                    GLAccount rbfGLAccount = getRBFGLAccount();
                    GLAccount bankAccount = getLinkedGLAccountForSavingsProduct(savingsProductId,
                            CashAccountsForSavings.SAVINGS_REFERENCE.getValue(), paymentTypeId);
                    if (rbfGLAccount != null && bankAccount != null) {
                        this.helper.createDebitJournalEntryForSavings(office, currencyCode, rbfGLAccount, savingsId, transactionId,
                                transactionDate, amount);
                        this.helper.createCreditJournalEntryForSavings(office, currencyCode, bankAccount, savingsId, transactionId,
                                transactionDate, amount);
                    } else {
                        log.error("RBF GL Account 200040 or Bank Account not found, using default logic");
                        super.createJournalEntriesForSavings(savingsDTO);
                    }
                } else if (paymentTypeId.equals(LOC_RECEIVABLE_PAYMENT_TYPE_ID) && linkedLoanProductId != null
                        && isLOCReceivableLoanProduct(linkedLoanProductId)) {
                    // LOC Receivable Loan Repayment withdrawal: DR 200041 (Loan Payable - Invoice Discounting), CR
                    // 100003 (Bank)
                    log.info("CustomCashBasedAccountingProcessorForSavings: LOC Receivable manual withdrawal - DR 200041, CR Bank");
                    GLAccount locLoanPayableAccount = getLOCReceivableLoanPayableGLAccount();
                    GLAccount bankAccount = getLinkedGLAccountForSavingsProduct(savingsProductId,
                            CashAccountsForSavings.SAVINGS_REFERENCE.getValue(), paymentTypeId);
                    if (locLoanPayableAccount != null && bankAccount != null) {
                        this.helper.createDebitJournalEntryForSavings(office, currencyCode, locLoanPayableAccount, savingsId, transactionId,
                                transactionDate, amount);
                        this.helper.createCreditJournalEntryForSavings(office, currencyCode, bankAccount, savingsId, transactionId,
                                transactionDate, amount);
                    } else {
                        log.error("LOC Receivable GL Account 200041 or Bank Account not found, using default logic");
                        super.createJournalEntriesForSavings(savingsDTO);
                    }
                } else {
                    // Payment type matched but product type didn't, use default logic
                    super.createJournalEntriesForSavings(savingsDTO);
                }
            } else {
                // For all other transaction types, delegate to parent
                super.createJournalEntriesForSavings(savingsDTO);
                    && paymentTypeId != null
                    && (paymentTypeId.equals(RBF_PAYMENT_TYPE_ID) || paymentTypeId.equals(LOC_RECEIVABLE_PAYMENT_TYPE_ID))) {
                // Manual withdrawal with payment_type=5 (RBF) or payment_type=73 (LOC Receivable)
                Long linkedLoanProductId = getLinkedLoanProductId(savingsId);

                if (paymentTypeId.equals(RBF_PAYMENT_TYPE_ID) && linkedLoanProductId != null && isRBFLoanProduct(linkedLoanProductId)) {
                    // RBF Loan Repayment withdrawal: DR 200040 (RBF Loan Payable), CR 100003 (Bank)
                    // For reversals: CR 200040 (RBF Loan Payable), DR 100003 (Bank) - swap the entries
                    GLAccount rbfGLAccount = getRBFGLAccount();
                    GLAccount bankAccount = getLinkedGLAccountForSavingsProduct(savingsProductId,
                            CashAccountsForSavings.SAVINGS_REFERENCE.getValue(), paymentTypeId);
                    if (rbfGLAccount != null && bankAccount != null) {
                        if (isReversal) {
                            // Reversal: Swap DR/CR - CR 200040, DR 100003
                            this.helper.createCreditJournalEntryForSavings(office, currencyCode, rbfGLAccount, savingsId, transactionId,
                                    transactionDate, amount);
                            this.helper.createDebitJournalEntryForSavings(office, currencyCode, bankAccount, savingsId, transactionId,
                                    transactionDate, amount);
                            log.info("CustomCashBasedAccountingProcessorForSavings: RBF withdrawal REVERSAL - CR 200040, DR 100003");
                        } else {
                            // Normal: DR 200040, CR 100003
                            this.helper.createDebitJournalEntryForSavings(office, currencyCode, rbfGLAccount, savingsId, transactionId,
                                    transactionDate, amount);
                            this.helper.createCreditJournalEntryForSavings(office, currencyCode, bankAccount, savingsId, transactionId,
                                    transactionDate, amount);
                            log.info("CustomCashBasedAccountingProcessorForSavings: RBF withdrawal - DR 200040, CR 100003");
                        }
                        processedTransactionIndices.add(i);
                    } else {
                        log.error("RBF GL Account 200040 or Bank Account not found, using default logic for this transaction");
                        // Do NOT mark as processed - let parent handle it
                    }
                } else if (paymentTypeId.equals(LOC_RECEIVABLE_PAYMENT_TYPE_ID) && linkedLoanProductId != null
                        && isLOCReceivableLoanProduct(linkedLoanProductId)) {
                    // LOC Receivable Loan Repayment withdrawal: DR 200041 (Loan Payable - Invoice Discounting), CR
                    // 100003 (Bank)
                    log.info("CustomCashBasedAccountingProcessorForSavings: LOC Receivable manual withdrawal - DR 200041, CR Bank");
                    GLAccount locLoanPayableAccount = getLOCReceivableLoanPayableGLAccount();
                    GLAccount bankAccount = getLinkedGLAccountForSavingsProduct(savingsProductId,
                            CashAccountsForSavings.SAVINGS_REFERENCE.getValue(), paymentTypeId);
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
                    // Payment type matched but product type didn't, use default logic
                    // Do NOT mark as processed - let parent handle it
                }
            }
            // For all other transaction types that were not explicitly handled, they will be processed by parent below
        }

        // IMPORTANT: Only delegate to parent for transactions that were NOT processed by custom logic
        // This prevents duplicate GL entries for handled transaction types
        if (processedTransactionIndices.size() < savingsDTO.getNewSavingsTransactions().size()) {
            // Create a filtered list with only unprocessed transactions
            List<SavingsTransactionDTO> unprocessedTransactions = new ArrayList<>();
            for (int i = 0; i < savingsDTO.getNewSavingsTransactions().size(); i++) {
                if (!processedTransactionIndices.contains(i)) {
                    unprocessedTransactions.add(savingsDTO.getNewSavingsTransactions().get(i));
                }
            }

            if (!unprocessedTransactions.isEmpty()) {
                log.debug(
                        "CustomCashBasedAccountingProcessorForSavings: {} transactions were handled by custom logic, delegating {} unprocessed transactions to parent processor",
                        processedTransactionIndices.size(), unprocessedTransactions.size());

                // Create a temporary wrapper to pass only unprocessed transactions to parent
                // We'll call parent's helper methods directly for each unprocessed transaction to avoid passing entire
                // DTO
                for (SavingsTransactionDTO unprocessedTx : unprocessedTransactions) {
                    final LocalDate transactionDate = unprocessedTx.getTransactionDate();
                    final String transactionId = unprocessedTx.getTransactionId();
                    final Office office = this.helper.getOfficeById(unprocessedTx.getOfficeId());
                    final Long paymentTypeId = unprocessedTx.getPaymentTypeId();
                    final boolean isReversal = unprocessedTx.isReversed();
                    final BigDecimal amount = unprocessedTx.getAmount();
                    final BigDecimal overdraftAmount = unprocessedTx.getOverdraftAmount();
                    final List<ChargePaymentDTO> feePayments = unprocessedTx.getFeePayments();
                    final List<ChargePaymentDTO> penaltyPayments = unprocessedTx.getPenaltyPayments();

                    // Call parent's processing logic for this single transaction
                    if (unprocessedTx.getTransactionType().isDeposit()) {
                        this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                                CashAccountsForSavings.SAVINGS_REFERENCE.getValue(), CashAccountsForSavings.SAVINGS_CONTROL.getValue(),
                                savingsProductId, paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
                        if (unprocessedTx.getTransactionType().isChargeTransaction() && penaltyPayments != null
                                && !penaltyPayments.isEmpty()) {
                            this.helper.createCashBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                                    CashAccountsForSavings.SAVINGS_CONTROL, CashAccountsForSavings.INCOME_FROM_PENALTIES, savingsProductId,
                                    paymentTypeId, savingsId, transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal,
                                    penaltyPayments);
                        }
                    } else if (unprocessedTx.getTransactionType().isWithdrawal()) {
                        this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                                CashAccountsForSavings.SAVINGS_CONTROL.getValue(), CashAccountsForSavings.SAVINGS_REFERENCE.getValue(),
                                savingsProductId, paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
                    }
                }
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
            log.debug("CustomCashBasedAccountingProcessorForSavings: Error checking LOC Receivable loan product for loanProductId {}: {}",
                    loanProductId, e.getMessage());
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
            log.error("CustomCashBasedAccountingProcessorForSavings: Error finding GL account {}: {}", LOC_RECEIVABLE_DEBIT_GL_CODE,
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
            log.error("CustomCashBasedAccountingProcessorForSavings: Error finding GL account {}: {}", LOC_RECEIVABLE_CREDIT_GL_CODE,
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
            log.error("CustomCashBasedAccountingProcessorForSavings: Error finding GL account {}: {}", LOC_RECEIVABLE_LOAN_PAYABLE_GL_CODE,
                    e.getMessage());
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
            log.debug("CustomCashBasedAccountingProcessorForSavings: Error checking LOC Receivable loan product for loanProductId {}: {}",
                    loanProductId, e.getMessage());
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
            log.error("CustomCashBasedAccountingProcessorForSavings: Error finding GL account {}: {}", LOC_RECEIVABLE_DEBIT_GL_CODE,
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
            log.error("CustomCashBasedAccountingProcessorForSavings: Error finding GL account {}: {}", LOC_RECEIVABLE_CREDIT_GL_CODE,
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
            log.error("CustomCashBasedAccountingProcessorForSavings: Error finding GL account {}: {}", LOC_RECEIVABLE_LOAN_PAYABLE_GL_CODE,
                    e.getMessage());
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
                    log.debug("No mapping found with payment type {}, will try without: {}", paymentTypeId, e.getMessage());
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
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("CustomCashBasedAccountingProcessorForSavings: Error finding SAVINGS_CONTROL account: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if a savings transaction is a charge reversal by transaction type or note. Charge reversals use transaction
     * type CHARGE_REVERSAL to distinguish them from regular deposits. For charge reversals, we use GL 100062 and 210003
     * (same as normal deposits, but we handle them explicitly to prevent duplicates).
     *
     * Note: We check both transaction type and notes because the transaction type might be updated AFTER GL entries are
     * created.
     */
    private boolean isChargeReversalTransaction(SavingsTransactionDTO savingsTransactionDTO) {
        try {
            // Primary check: Check if the transaction type is CHARGE_REVERSAL (using enum constant instead of hardcoded
            // ID)
            if (savingsTransactionDTO.getTransactionType() != null) {
                Integer transactionTypeId = savingsTransactionDTO.getTransactionType().getId().intValue();
                SavingsAccountTransactionType transactionType = SavingsAccountTransactionType.fromInt(transactionTypeId);

                if (transactionType == SavingsAccountTransactionType.CHARGE_REVERSAL) {
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: Detected charge reversal transaction (CHARGE_REVERSAL) - will use GL 100062 and 210003");
                    return true;
                }
            }

            // Fallback: Check notes if transaction type is not CHARGE_REVERSAL yet (transaction type might be updated
            // after GL creation)
            // This handles the case where transaction type is updated AFTER the deposit is created
            String transactionId = savingsTransactionDTO.getTransactionId();
            if (transactionId != null) {
                boolean isChargeReversalByNote = isChargeReversalDeposit(transactionId, null);
                if (isChargeReversalByNote) {
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: Detected charge reversal transaction via note - will use GL 100062 and 210003");
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log.warn("CustomCashBasedAccountingProcessorForSavings: Error checking if transaction is charge reversal: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a savings deposit transaction is for a charge reversal by querying the transaction note. This is a
     * fallback method for backwards compatibility if deposits were created without the CHARGE_REVERSAL transaction
     * type. Charge reversal deposits have a note containing "Refund for reversed charge".
     *
     * Notes are stored in the m_note table, linked by savings_account_transaction_id.
     */
    private boolean isChargeReversalDeposit(String transactionId, Long savingsId) {
        try {
            // Extract numeric transaction ID from string like "S14119"
            String numericId = transactionId.replace("S", "").trim();
            Long transactionNumericId = Long.parseLong(numericId);

            // Query the m_note table for notes related to this savings transaction
            String sql = "SELECT note FROM m_note WHERE savings_account_transaction_id = ? LIMIT 1";
            try {
                String note = jdbcTemplate.queryForObject(sql, String.class, transactionNumericId);
                boolean isChargeReversal = note != null && note.contains("Refund for reversed charge");
                if (isChargeReversal) {
                    log.info(
                            "CustomCashBasedAccountingProcessorForSavings: Transaction {} is a charge reversal deposit (note: {}) - will use GL 300015 instead of 100062",
                            transactionId, note);
                }
                return isChargeReversal;
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                log.debug("CustomCashBasedAccountingProcessorForSavings: No note found for savings transaction ID {}",
                        transactionNumericId);
                return false;
            }
        } catch (Exception e) {
            log.warn("CustomCashBasedAccountingProcessorForSavings: Error checking if transaction {} is charge reversal: {}", transactionId,
                    e.getMessage());
            return false;
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
