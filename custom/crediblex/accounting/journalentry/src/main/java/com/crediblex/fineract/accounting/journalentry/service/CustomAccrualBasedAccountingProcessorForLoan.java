package com.crediblex.fineract.accounting.journalentry.service;

import com.crediblex.fineract.accounting.journalentry.CustomAccountingProcessorHelper;
import com.crediblex.fineract.accounting.journalentry.data.CustomChargePaymentDTO;
import com.crediblex.fineract.accounting.journalentry.journalentry.CustomLoanDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.common.AccountingConstants;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.data.LoanDTO;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.journalentry.service.AccrualBasedAccountingProcessorForLoan;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMapping;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.PortfolioProductType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Primary
@Component
@Slf4j
public class CustomAccrualBasedAccountingProcessorForLoan extends AccrualBasedAccountingProcessorForLoan {

    // Hardcoded RBF Configuration
    private static final String RBF_PRODUCT_SHORT_NAME = "RBF";
    private static final String RBF_GL_CODE = "200040"; // Loan Payable - Working Capital - Revenue Finance
    private static final String RECEIVABLE_LOC_PRODUCT_SHORT_NAME = "LRL";
    private static final String RECEIVABLE_LOC_GL_CODE = "200041"; // Loan Payable - Invoice Discounting

    @Autowired
    protected CustomAccountingProcessorHelper customAccountingProcessorHelper;

    @Autowired
    private ProductToGLAccountMappingRepository accountMappingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.apache.fineract.accounting.glaccount.domain.GLAccountRepository glAccountRepository;

    public CustomAccrualBasedAccountingProcessorForLoan(AccountingProcessorHelper helper,
            JournalEntryWritePlatformService journalEntryWritePlatformService) {
        super(helper, journalEntryWritePlatformService);
    }

    /**
     * Check if the loan product is RBF (Revenue Based Financing) product
     *
     * @param loanProductId
     *            The loan product ID
     * @return true if RBF product, false otherwise
     */
    private boolean isRBFProduct(Long loanProductId) {
        if (loanProductId == null) {
            return false;
        }

        try {
            String sql = "SELECT short_name FROM m_product_loan WHERE id = ?";
            String shortName = this.jdbcTemplate.queryForObject(sql, String.class, loanProductId);
            return RBF_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (Exception e) {
            // If query fails, default to non-RBF (use old Liability Transfer)
            return false;
        }
    }

    /**
     * Check if the loan product is Receivable LOC product
     *
     * @param loanProductId
     *            The loan product ID
     * @return true if Receivable LOC product, false otherwise
     */
    private boolean isReceivableLOCProduct(Long loanProductId) {
        if (loanProductId == null) {
            return false;
        }

        try {
            String sql = "SELECT short_name FROM m_product_loan WHERE id = ?";
            String shortName = this.jdbcTemplate.queryForObject(sql, String.class, loanProductId);
            return RECEIVABLE_LOC_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (Exception e) {
            // If query fails, default to non-Receivable LOC (use old Liability Transfer)
            return false;
        }
    }

    @Override
    protected void createJournalEntriesForDisbursements(final LoanDTO loanDTOSuper, final LoanTransactionDTO loanTransactionDTO,
            final Office office) {

        // loan properties
        final CustomLoanDTO loanDTO = (CustomLoanDTO) loanDTOSuper;
        final Long loanProductId = loanDTO.getLoanProductId();
        final Long loanId = loanDTO.getLoanId();
        final String currencyCode = loanDTO.getCurrencyCode();

        // transaction properties
        final String transactionId = loanTransactionDTO.getTransactionId();
        final LocalDate transactionDate = loanTransactionDTO.getTransactionDate();
        final BigDecimal overpaymentPortion = loanTransactionDTO.getOverPayment() != null ? loanTransactionDTO.getOverPayment()
                : BigDecimal.ZERO;
        final BigDecimal principalPortion = loanTransactionDTO.getAmount().subtract(overpaymentPortion);
        final Long paymentTypeId = loanTransactionDTO.getPaymentTypeId();

        // create journal entries for the disbursement
        if (MathUtil.isGreaterThanZero(principalPortion)) {
            this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.AccrualAccountsForLoan.LOAN_PORTFOLIO.getValue(), loanProductId, paymentTypeId, loanId,
                    transactionId, transactionDate, principalPortion);

        }
        if (MathUtil.isGreaterThanZero(overpaymentPortion)) {
            this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.AccrualAccountsForLoan.OVERPAYMENT.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                    transactionDate, overpaymentPortion);
        }

        // LOC Receivable: Post upfront interest and fee receivables with deferred income
        if (loanDTO.isLocReceivable() && loanDTO.isPeriodicAccrualBasedAccountingEnabled()) {
            // 1. Upfront Interest Receivable -> Deferred Income
            BigDecimal totalInterest = loanDTO.getTotalContractualInterest();
            if (MathUtil.isGreaterThanZero(totalInterest)) {
                this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                        AccountingConstants.AccrualAccountsForLoan.INTEREST_RECEIVABLE.getValue(), loanProductId, paymentTypeId, loanId,
                        transactionId, transactionDate, totalInterest);
                this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                        AccountingConstants.AccrualAccountsForLoan.DEFERRED_INCOME.getValue(), loanProductId, paymentTypeId, loanId,
                        transactionId, transactionDate, totalInterest);
            }

            // 2. Upfront Fee Receivable (gross) -> Fee Income (net) + Tax Liability (tax)
            // Fees are recognized as income immediately at disbursement (NOT deferred)
            BigDecimal totalFees = loanDTO.getTotalDisbursementFees();
            BigDecimal totalFeesTax = loanDTO.getTotalDisbursementFeesTax();
            BigDecimal netFees = totalFees.subtract(totalFeesTax);

            if (MathUtil.isGreaterThanZero(totalFees)) {
                // DR Fees Receivable (gross amount including tax)
                this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                        AccountingConstants.AccrualAccountsForLoan.FEES_RECEIVABLE.getValue(), loanProductId, paymentTypeId, loanId,
                        transactionId, transactionDate, totalFees);

                // CR Income From Fees (net amount - recognized immediately)
                if (MathUtil.isGreaterThanZero(netFees)) {
                    this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                            AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_FEES.getValue(), loanProductId, paymentTypeId, loanId,
                            transactionId, transactionDate, netFees);
                }

                // CR Tax Liability (tax portion)
                // First try product-level mapping, then fall back to charge's tax group GL account
                if (MathUtil.isGreaterThanZero(totalFeesTax)) {
                    ProductToGLAccountMapping taxLiabilityMapping = accountMappingRepository.findCoreProductToFinAccountMapping(
                            loanProductId, PortfolioProductType.LOAN.getValue(),
                            AccountingConstants.AccrualAccountsForLoan.LIABILITY_FROM_TAXES.getValue());

                    if (taxLiabilityMapping != null) {
                        // Use product-level mapping if configured
                        this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                                AccountingConstants.AccrualAccountsForLoan.LIABILITY_FROM_TAXES.getValue(), loanProductId, paymentTypeId,
                                loanId, transactionId, transactionDate, totalFeesTax);
                    } else if (loanDTO.getTaxLiabilityGLAccountId() != null) {
                        // Fall back to tax liability GL account from charge's tax group
                        GLAccount taxLiabilityAccount = glAccountRepository.findById(loanDTO.getTaxLiabilityGLAccountId())
                                .orElseThrow(() -> new IllegalStateException(
                                        "Tax liability GL account not found for ID: " + loanDTO.getTaxLiabilityGLAccountId()));
                        this.helper.createCreditJournalEntryForLoan(office, currencyCode, loanId, transactionId, transactionDate,
                                totalFeesTax, taxLiabilityAccount);
                        log.info(
                                "Using tax liability GL account {} from charge's tax group for loan {} (ID: {}) since product-level mapping is not configured.",
                                loanDTO.getTaxLiabilityGLAccountId(), loanId, loanId);
                    } else {
                        log.warn(
                                "Tax liability mapping not found for loan product {} (ID: {}) and no tax GL account from charge's tax group. "
                                        + "Skipping tax liability journal entry for loan {} (ID: {}). "
                                        + "Please configure the 'LIABILITY FROM TAXES' chart of accounts mapping for this loan product or ensure charges have tax groups with GL accounts configured.",
                                loanProductId, loanProductId, loanId, loanId);
                        // Note: If tax liability mapping is missing, the tax portion will be included in the net fees
                        // income
                        // This may not be the desired accounting behavior, but prevents the disbursement from failing
                    }
                }
            }
        }

        if (loanTransactionDTO.isLoanToLoanTransfer()) {
            this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.FinancialActivity.ASSET_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                    transactionDate, loanTransactionDTO.getAmount());
        } else if (loanTransactionDTO.isAccountTransfer()) {
            // For account transfers (disburse to savings):
            // - RBF products: Use GL 200040 directly
            // - Receivable LOC products: Use GL 200041 directly
            // - Other products: Use LIABILITY_TRANSFER financial activity
            if (isRBFProduct(loanProductId)) {
                log.info("CustomAccrualBasedAccountingProcessorForLoan: RBF product detected - Using GL 200040 for loan product {}",
                        loanProductId);

                // Get GL 200040 account
                GLAccount rbfGLAccount = getRBFGLAccount();
                if (rbfGLAccount == null) {
                    log.warn("CustomAccrualBasedAccountingProcessorForLoan: GL 200040 not found, falling back to LIABILITY_TRANSFER");
                    this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                            AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId,
                            transactionId, transactionDate, loanDTO.getNetDisbursalAmount());
                } else {
                    // RBF: Credit GL 200040 directly
                    this.helper.createCreditJournalEntryForLoan(office, currencyCode, loanId, transactionId, transactionDate,
                            loanDTO.getNetDisbursalAmount(), rbfGLAccount);
                    log.info("CustomAccrualBasedAccountingProcessorForLoan: Journal entry created with GL 200040 for RBF disbursement");
                }
            } else if (isReceivableLOCProduct(loanProductId)) {
                log.info(
                        "CustomAccrualBasedAccountingProcessorForLoan: Receivable LOC product detected - Using GL 200041 for loan product {}",
                        loanProductId);

                // Get GL 200041 account
                GLAccount receivableLOCGLAccount = getReceivableLOCGLAccount();
                if (receivableLOCGLAccount == null) {
                    log.warn("CustomAccrualBasedAccountingProcessorForLoan: GL 200041 not found, falling back to LIABILITY_TRANSFER");
                    this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                            AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId,
                            transactionId, transactionDate, loanDTO.getNetDisbursalAmount());
                } else {
                    // Receivable LOC: Credit GL 200041 directly
                    this.helper.createCreditJournalEntryForLoan(office, currencyCode, loanId, transactionId, transactionDate,
                            loanDTO.getNetDisbursalAmount(), receivableLOCGLAccount);
                    log.info(
                            "CustomAccrualBasedAccountingProcessorForLoan: Journal entry created with GL 200041 for Receivable LOC disbursement");
                }
            } else {
                log.debug("CustomAccrualBasedAccountingProcessorForLoan: Standard product, using default LIABILITY_TRANSFER");
                this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                        AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId,
                        transactionId, transactionDate, loanDTO.getNetDisbursalAmount());
            }
        } else {
            this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.AccrualAccountsForLoan.FUND_SOURCE.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                    transactionDate, loanTransactionDTO.getAmount());
        }
    }

    @Override
    public void createJournalEntriesForLoansRepaymentAndWriteOffs(final LoanDTO loanDTO, final LoanTransactionDTO loanTransactionDTO,
            final Office office, final boolean writeOff, final boolean isIncomeFromFee) {
        // loan properties
        final Long loanProductId = loanDTO.getLoanProductId();
        final Long loanId = loanDTO.getLoanId();
        final String currencyCode = loanDTO.getCurrencyCode();

        // transaction properties
        final String transactionId = loanTransactionDTO.getTransactionId();
        final LocalDate transactionDate = loanTransactionDTO.getTransactionDate();
        final BigDecimal principalAmount = loanTransactionDTO.getPrincipal();
        final BigDecimal interestAmount = loanTransactionDTO.getInterest();
        final BigDecimal feesAmount = loanTransactionDTO.getFees();
        final BigDecimal taxesAmount = loanTransactionDTO.getTaxes();
        final BigDecimal penaltiesAmount = loanTransactionDTO.getPenalties();
        final BigDecimal overPaymentAmount = loanTransactionDTO.getOverPayment();
        final Long paymentTypeId = loanTransactionDTO.getPaymentTypeId();

        BigDecimal totalDebitAmount = new BigDecimal(0);

        Map<GLAccount, BigDecimal> accountMap = new LinkedHashMap<>();
        Map<Integer, BigDecimal> debitAccountMapForGoodwillCredit = new LinkedHashMap<>();

        // handle principal payment or writeOff
        if (principalAmount != null && principalAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(principalAmount);
            GLAccount account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                    AccountingConstants.AccrualAccountsForLoan.LOAN_PORTFOLIO.getValue(), paymentTypeId);
            accountMap.put(account, principalAmount);
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, principalAmount,
                        AccountingConstants.AccrualAccountsForLoan.GOODWILL_CREDIT.getValue(), debitAccountMapForGoodwillCredit,
                        paymentTypeId);
            }
        }

        // handle interest payment of writeOff
        if (interestAmount != null && interestAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(interestAmount);
            GLAccount account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                    AccountingConstants.AccrualAccountsForLoan.INTEREST_RECEIVABLE.getValue(), paymentTypeId);
            if (accountMap.containsKey(account)) {
                BigDecimal amount = accountMap.get(account).add(interestAmount);
                accountMap.put(account, amount);
            } else {
                accountMap.put(account, interestAmount);
            }
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, interestAmount,
                        AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_GOODWILL_CREDIT_INTEREST.getValue(),
                        debitAccountMapForGoodwillCredit, paymentTypeId);
            }
        }

        // handle fees payment of writeOff
        if (feesAmount != null && feesAmount.compareTo(BigDecimal.ZERO) > 0) {

            totalDebitAmount = totalDebitAmount.add(feesAmount);

            if (isIncomeFromFee) {
                this.helper.createCreditJournalEntryForLoanCharges(office, currencyCode,
                        AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_FEES.getValue(), loanProductId, loanId, transactionId,
                        transactionDate, feesAmount, loanTransactionDTO.getFeePayments());
            } else if (loanTransactionDTO.getTransactionType().isVatDeductionAtDisbursement()) {
                // For VAT deduction at disbursement, use the sum of tax amounts from the
                // fee payments as the total amount for the advanced accounting helper.
                // This aligns the helper's integrity check with the actual VAT being
                // posted in this transaction, even when VAT is collected across multiple
                // tranches.
                BigDecimal totalVatForTransaction = BigDecimal.ZERO;
                if (loanTransactionDTO.getFeePayments() != null) {
                    totalVatForTransaction = loanTransactionDTO.getFeePayments().stream().filter(Objects::nonNull)

                            .filter(CustomChargePaymentDTO.class::isInstance).map(CustomChargePaymentDTO.class::cast)
                            .map(CustomChargePaymentDTO::getTaxAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                }
                this.customAccountingProcessorHelper.createCreditJournalEntryForLoanCharges(office, currencyCode, loanId, transactionId,
                        transactionDate, totalVatForTransaction, loanTransactionDTO.getFeePayments());
            } else {
                // For RBF loans with foreclosure charges, use Early Settlement Fee Revenue (GL 300002)
                if (isRBFProduct(loanProductId) && isForeclosureCharge(loanTransactionDTO)) {
                    GLAccount earlySettlementFeeRevenue = glAccountRepository.findOneByGlCode("300002").orElse(null);
                    if (earlySettlementFeeRevenue != null) {
                        this.helper.createCreditJournalEntryForLoan(office, currencyCode, loanId, transactionId, transactionDate,
                                feesAmount, earlySettlementFeeRevenue);
                        log.info(
                                "CustomAccrualBasedAccountingProcessorForLoan: Using GL 300002 (Early Settlement Fee Revenue) for RBF foreclosure fees");
                    } else {
                        log.warn("CustomAccrualBasedAccountingProcessorForLoan: GL 300002 not found, falling back to FEES_RECEIVABLE");
                        GLAccount account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                                AccountingConstants.AccrualAccountsForLoan.FEES_RECEIVABLE.getValue(), paymentTypeId);
                        if (accountMap.containsKey(account)) {
                            BigDecimal amount = accountMap.get(account).add(feesAmount);
                            accountMap.put(account, amount);
                        } else {
                            accountMap.put(account, feesAmount);
                        }
                    }
                } else {
                    GLAccount account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                            AccountingConstants.AccrualAccountsForLoan.FEES_RECEIVABLE.getValue(), paymentTypeId);
                    if (accountMap.containsKey(account)) {
                        BigDecimal amount = accountMap.get(account).add(feesAmount);
                        accountMap.put(account, amount);
                    } else {
                        accountMap.put(account, feesAmount);
                    }
                }
            }
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, feesAmount,
                        AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_GOODWILL_CREDIT_FEES.getValue(),
                        debitAccountMapForGoodwillCredit, paymentTypeId);
            }
        }

        // handle taxes payment
        if (taxesAmount != null && taxesAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(taxesAmount);
            if (loanTransactionDTO.getTransactionType().isRepayment()) {
                this.customAccountingProcessorHelper.createJournalEntriesForInstallmentChargeTaxes(office, currencyCode, loanId,
                        transactionId, transactionDate, taxesAmount, loanTransactionDTO.getTaxPayments());
            }
        }

        // handle penalties payment of writeOff
        if (penaltiesAmount != null && penaltiesAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(penaltiesAmount);

            // Handle different transaction types
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, penaltiesAmount,
                        AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_GOODWILL_CREDIT_PENALTY.getValue(),
                        debitAccountMapForGoodwillCredit, paymentTypeId);
                GLAccount account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                        AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_RECOVERY.getValue(), paymentTypeId);
                if (accountMap.containsKey(account)) {
                    BigDecimal amount = accountMap.get(account).add(penaltiesAmount);
                    accountMap.put(account, amount);
                } else {
                    accountMap.put(account, penaltiesAmount);
                }
            } else if (loanTransactionDTO.getTransactionType().isRepayment()) {
                // For repayments: Use INCOME_FROM_RECOVERY, but for RBF products use GL 300015 instead
                GLAccount account;
                if (isRBFProduct(loanProductId)) {
                    // Use hardcoded GL 300015 for RBF overdue interest penalty income on repayments
                    account = glAccountRepository.findOneByGlCode("300015").orElse(null);
                    if (account == null) {
                        log.warn(
                                "CustomAccrualBasedAccountingProcessorForLoan: GL 300015 (Over Due Interest - LPI - RBF) not found for RBF product {}. Falling back to default INCOME_FROM_RECOVERY.",
                                loanProductId);
                        account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                                AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_RECOVERY.getValue(), paymentTypeId);
                    } else {
                        log.info(
                                "CustomAccrualBasedAccountingProcessorForLoan: Using GL 300015 (Over Due Interest - LPI - RBF) for RBF penalty income on repayment, amount: {}",
                                penaltiesAmount);
                    }
                } else {
                    // Non-RBF: Use default INCOME_FROM_RECOVERY
                    account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                            AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_RECOVERY.getValue(), paymentTypeId);
                }
                if (accountMap.containsKey(account)) {
                    BigDecimal amount = accountMap.get(account).add(penaltiesAmount);
                    accountMap.put(account, amount);
                } else {
                    accountMap.put(account, penaltiesAmount);
                }
            } else if (isIncomeFromFee) {
                // For RBF products, use GL 300015 (Over Due Interest - LPI - RBF) for penalty income
                // instead of the default INCOME_FROM_PENALTIES account
                GLAccount account;
                if (isRBFProduct(loanProductId)) {
                    // Use hardcoded GL 300015 for RBF overdue interest penalty income
                    account = glAccountRepository.findOneByGlCode("300015").orElse(null);
                    if (account == null) {
                        log.warn(
                                "CustomAccrualBasedAccountingProcessorForLoan: GL 300015 (Over Due Interest - LPI - RBF) not found for RBF product {}. Falling back to default INCOME_FROM_PENALTIES.",
                                loanProductId);
                        account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                                AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_PENALTIES.getValue(), paymentTypeId);
                    } else {
                        log.info(
                                "CustomAccrualBasedAccountingProcessorForLoan: Using GL 300015 (Over Due Interest - LPI - RBF) for RBF penalty income, amount: {}",
                                penaltiesAmount);
                    }
                } else {
                    account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                            AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_PENALTIES.getValue(), paymentTypeId);
                }
                if (accountMap.containsKey(account)) {
                    BigDecimal amount = accountMap.get(account).add(penaltiesAmount);
                    accountMap.put(account, amount);
                } else {
                    accountMap.put(account, penaltiesAmount);
                }
            } else {
                GLAccount account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                        AccountingConstants.AccrualAccountsForLoan.PENALTIES_RECEIVABLE.getValue(), paymentTypeId);
                if (accountMap.containsKey(account)) {
                    BigDecimal amount = accountMap.get(account).add(penaltiesAmount);
                    accountMap.put(account, amount);
                } else {
                    accountMap.put(account, penaltiesAmount);
                }
            }
        }

        if (overPaymentAmount != null && overPaymentAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(overPaymentAmount);
            GLAccount account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                    AccountingConstants.AccrualAccountsForLoan.OVERPAYMENT.getValue(), paymentTypeId);
            if (accountMap.containsKey(account)) {
                BigDecimal amount = accountMap.get(account).add(overPaymentAmount);
                accountMap.put(account, amount);
            } else {
                accountMap.put(account, overPaymentAmount);
            }
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, overPaymentAmount,
                        AccountingConstants.AccrualAccountsForLoan.GOODWILL_CREDIT.getValue(), debitAccountMapForGoodwillCredit,
                        paymentTypeId);
            }
        }

        for (Map.Entry<GLAccount, BigDecimal> entry : accountMap.entrySet()) {
            this.helper.createCreditJournalEntryForLoan(office, currencyCode, loanId, transactionId, transactionDate, entry.getValue(),
                    entry.getKey());
        }

        // LOC Receivable: Recognize deferred income when interest/fees are actually repaid
        // Combine interest and fees to avoid multiple entries to the same Deferred Income account
        if (loanDTO instanceof CustomLoanDTO) {
            CustomLoanDTO customLoanDTO = (CustomLoanDTO) loanDTO;
            if (customLoanDTO.isLocReceivable() && customLoanDTO.isPeriodicAccrualBasedAccountingEnabled() && !writeOff) {

                // For LOC receivable: Only unwind deferred income for unaccrued interest (early payment scenario)
                // Fees are NOT included in deferred income - they are installment fees handled through standard accrual
                if (interestAmount != null && interestAmount.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal totalInterestCharged = customLoanDTO.getTotalInterestCharged() != null
                            ? customLoanDTO.getTotalInterestCharged()
                            : BigDecimal.ZERO;
                    BigDecimal totalAccruedInterest = customLoanDTO.getTotalAccruedInterest() != null
                            ? customLoanDTO.getTotalAccruedInterest()
                            : BigDecimal.ZERO;
                    BigDecimal unaccruedInterest = totalInterestCharged.subtract(totalAccruedInterest);

                    // Only unwind if there's unaccrued interest (early payment scenario)
                    if (unaccruedInterest.compareTo(BigDecimal.ZERO) > 0) {
                        // DR Deferred Income
                        this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                                AccountingConstants.AccrualAccountsForLoan.DEFERRED_INCOME.getValue(), loanProductId, paymentTypeId, loanId,
                                transactionId, transactionDate, unaccruedInterest);

                        // CR Interest On Loans for unaccrued interest
                        this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                                AccountingConstants.AccrualAccountsForLoan.INTEREST_ON_LOANS.getValue(), loanProductId, paymentTypeId,
                                loanId, transactionId, transactionDate, unaccruedInterest);
                    }
                }
            }
        }

        /**
         * Exclude DEBIT entry for Repayments of Fees at Disbursement and VAT Deduction at Disbursement
         **/
        if (totalDebitAmount.compareTo(BigDecimal.ZERO) > 0 && isDebitAccountEntryPermitted(loanTransactionDTO)) {
            if (writeOff) {
                this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                        AccountingConstants.AccrualAccountsForLoan.LOSSES_WRITTEN_OFF.getValue(), loanProductId, paymentTypeId, loanId,
                        transactionId, transactionDate, totalDebitAmount);
            } else {
                if (loanTransactionDTO.isLoanToLoanTransfer()) {
                    this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                            AccountingConstants.FinancialActivity.ASSET_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId,
                            transactionId, transactionDate, totalDebitAmount);
                } else if (loanTransactionDTO.isAccountTransfer()) {
                    // For account transfers (repayment from savings to loan):
                    // - RBF products: Use GL 210003 (Working Capital Loan)
                    // - Receivable LOC products: Use GL 210003 (Working Capital Loan)
                    // - Other products: Use LIABILITY_TRANSFER financial activity
                    if (isRBFProduct(loanProductId)) {
                        log.info("CustomAccrualBasedAccountingProcessorForLoan: RBF product detected - Using GL 210003 for repayment");
                        // Get GL 210003 account (Working Capital Loan)
                        GLAccount rbfRepaymentGLAccount = glAccountRepository.findOneByGlCode("210003").orElse(null);
                        if (rbfRepaymentGLAccount != null) {
                            this.helper.createDebitJournalEntryForLoan(office, currencyCode, loanId, transactionId, transactionDate,
                                    totalDebitAmount, rbfRepaymentGLAccount);
                            log.info(
                                    "CustomAccrualBasedAccountingProcessorForLoan: Journal entry created with GL 210003 for RBF repayment");
                        } else {
                            log.warn(
                                    "CustomAccrualBasedAccountingProcessorForLoan: GL 210003 not found, falling back to LIABILITY_TRANSFER");
                            this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                                    AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), loanProductId, paymentTypeId,
                                    loanId, transactionId, transactionDate, totalDebitAmount);
                        }
                    } else if (isReceivableLOCProduct(loanProductId)) {
                        log.info(
                                "CustomAccrualBasedAccountingProcessorForLoan: Receivable LOC product detected - Using GL 210003 for repayment");
                        // Get GL 210003 account (Working Capital Loan)
                        GLAccount receivableLOCRepaymentGLAccount = glAccountRepository.findOneByGlCode("210003").orElse(null);
                        if (receivableLOCRepaymentGLAccount != null) {
                            this.helper.createDebitJournalEntryForLoan(office, currencyCode, loanId, transactionId, transactionDate,
                                    totalDebitAmount, receivableLOCRepaymentGLAccount);
                            log.info(
                                    "CustomAccrualBasedAccountingProcessorForLoan: Journal entry created with GL 210003 for Receivable LOC repayment");
                        } else {
                            log.warn(
                                    "CustomAccrualBasedAccountingProcessorForLoan: GL 210003 not found, falling back to LIABILITY_TRANSFER");
                            this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                                    AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), loanProductId, paymentTypeId,
                                    loanId, transactionId, transactionDate, totalDebitAmount);
                        }
                    } else {
                        log.debug("CustomAccrualBasedAccountingProcessorForLoan: Standard product, using default LIABILITY_TRANSFER");
                        this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                                AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId,
                                transactionId, transactionDate, totalDebitAmount);
                    }
                } else {
                    if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                        // create debit entries
                        for (Map.Entry<Integer, BigDecimal> debitEntry : debitAccountMapForGoodwillCredit.entrySet()) {
                            this.helper.createDebitJournalEntryForLoan(office, currencyCode, debitEntry.getKey().intValue(), loanProductId,
                                    paymentTypeId, loanId, transactionId, transactionDate, debitEntry.getValue());
                        }

                    } else {
                        this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                                AccountingConstants.AccrualAccountsForLoan.FUND_SOURCE.getValue(), loanProductId, paymentTypeId, loanId,
                                transactionId, transactionDate, totalDebitAmount);
                    }
                }
            }
        }

        /**
         * Charge Refunds have an extra refund related pair of journal entries in addition to those related to the
         * repayment above
         ***/
        if (totalDebitAmount.compareTo(BigDecimal.ZERO) > 0 && loanTransactionDTO.getTransactionType().isChargeRefund()) {
            Integer incomeAccount = this.helper.getValueForFeeOrPenaltyIncomeAccount(loanTransactionDTO.getChargeRefundChargeType());
            this.helper.createJournalEntriesForLoan(office, currencyCode, incomeAccount,
                    AccountingConstants.AccrualAccountsForLoan.FUND_SOURCE.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                    transactionDate, totalDebitAmount);
        }
    }

    private boolean isDebitAccountEntryPermitted(final LoanTransactionDTO loanTransactionDTO) {
        return !loanTransactionDTO.getTransactionType().isVatDeductionAtDisbursement()
                && !loanTransactionDTO.getTransactionType().isRepaymentAtDisbursement();
    }

    /**
     * Override parent's protected method to handle journal entries for accruals. For LOC receivable with periodic
     * accrual, this unwinds deferred income instead of creating new receivables. For non-LOC loans, this delegates to
     * the parent class logic.
     */
    @Override
    protected void createJournalEntriesForAccruals(final LoanDTO loanDTOSuper, final LoanTransactionDTO loanTransactionDTO,
            final Office office) {
        final CustomLoanDTO loanDTO = (loanDTOSuper instanceof CustomLoanDTO) ? (CustomLoanDTO) loanDTOSuper : null;

        // For LOC receivable periodic loans, unwind deferred income instead of creating new receivables
        if (loanDTO != null && loanDTO.isLocReceivable() && loanDTO.isPeriodicAccrualBasedAccountingEnabled()) {
            unwindDeferredIncomeForAccrual(loanDTO, loanTransactionDTO, office);
        } else {
            // For non-LOC loans, delegate to parent's standard accrual logic
            super.createJournalEntriesForAccruals(loanDTOSuper, loanTransactionDTO, office);
        }
    }

    /**
     * Unwinds deferred income for periodic accruals on LOC receivable loans. DR Deferred Income -> CR Interest On Loans
     * (no new receivable created)
     */
    private void unwindDeferredIncomeForAccrual(final CustomLoanDTO loanDTO, final LoanTransactionDTO loanTransactionDTO,
            final Office office) {
        final Long loanProductId = loanDTO.getLoanProductId();
        final Long loanId = loanDTO.getLoanId();
        final String currencyCode = loanDTO.getCurrencyCode();
        final String transactionId = loanTransactionDTO.getTransactionId();
        final LocalDate transactionDate = loanTransactionDTO.getTransactionDate();
        final Long paymentTypeId = loanTransactionDTO.getPaymentTypeId();

        // Get period interest amount
        BigDecimal periodInterest = loanTransactionDTO.getInterest();

        if (MathUtil.isGreaterThanZero(periodInterest)) {
            // DR Deferred Income (reduce liability)
            this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.AccrualAccountsForLoan.DEFERRED_INCOME.getValue(), loanProductId, paymentTypeId, loanId,
                    transactionId, transactionDate, periodInterest);

            // CR Interest On Loans (recognize income)
            this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.AccrualAccountsForLoan.INTEREST_ON_LOANS.getValue(), loanProductId, paymentTypeId, loanId,
                    transactionId, transactionDate, periodInterest);
        }
    }

    /**
     * Get GL 200040 account (RBF Loan Payable) Looks up by GL code to avoid hardcoding account ID
     */
    private GLAccount getRBFGLAccount() {
        try {
            return glAccountRepository.findOneByGlCode(RBF_GL_CODE).orElse(null);
        } catch (Exception e) {
            log.error("CustomAccrualBasedAccountingProcessorForLoan: Error finding GL account {}: {}", RBF_GL_CODE, e.getMessage());
            return null;
        }
    }

    /**
     * Get GL 200041 account (Receivable LOC Loan Payable - Invoice Discounting) Looks up by GL code to avoid hardcoding
     * account ID
     */
    private GLAccount getReceivableLOCGLAccount() {
        try {
            return glAccountRepository.findOneByGlCode(RECEIVABLE_LOC_GL_CODE).orElse(null);
        } catch (Exception e) {
            log.error("CustomAccrualBasedAccountingProcessorForLoan: Error finding GL account {}: {}", RECEIVABLE_LOC_GL_CODE,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Check if the transaction is a foreclosure charge
     */
    private boolean isForeclosureCharge(LoanTransactionDTO loanTransactionDTO) {
        try {
            if (loanTransactionDTO.getFeePayments() != null && !loanTransactionDTO.getFeePayments().isEmpty()) {
                for (var feePayment : loanTransactionDTO.getFeePayments()) {
                    Long chargeId = feePayment.getChargeId();
                    if (chargeId != null) {
                        String sql = "SELECT name FROM m_charge WHERE id = ?";
                        String chargeName = this.jdbcTemplate.queryForObject(sql, String.class, chargeId);
                        if (chargeName != null) {
                            String lowerName = chargeName.toLowerCase();
                            if (lowerName.contains("foreclosure") || lowerName.contains("early settlement")
                                    || lowerName.contains("prepayment") || lowerName.contains("early closure")) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("CustomAccrualBasedAccountingProcessorForLoan: Error checking foreclosure charge: {}", e.getMessage());
        }
        return false;
    }
}
