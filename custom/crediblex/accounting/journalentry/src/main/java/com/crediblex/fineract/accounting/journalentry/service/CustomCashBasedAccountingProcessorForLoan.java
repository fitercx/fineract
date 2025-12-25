package com.crediblex.fineract.accounting.journalentry.service;

import com.crediblex.fineract.accounting.journalentry.CustomAccountingProcessorHelper;
import com.crediblex.fineract.accounting.journalentry.journalentry.CustomLoanDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.common.AccountingConstants;
import org.apache.fineract.accounting.journalentry.data.LoanDTO;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.journalentry.service.CashBasedAccountingProcessorForLoan;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CustomCashBasedAccountingProcessorForLoan extends CashBasedAccountingProcessorForLoan {

    // RBF Product Short Name - used for product-specific GL account mapping
    // Only RBF products should use LIABILITY_TRANSFER (GL 200040) for account transfers
    // Using short_name instead of ID to be environment-agnostic (works across UAT/PROD)
    private static final String RBF_PRODUCT_SHORT_NAME = "RBF";

    @Autowired
    private CustomAccountingProcessorHelper customAccountingProcessorHelper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public CustomCashBasedAccountingProcessorForLoan(AccountingProcessorHelper helper,
            JournalEntryWritePlatformService journalEntryWritePlatformService) {
        super(helper, journalEntryWritePlatformService);
    }

    /**
     * Check if the loan product is RBF (Revenue Based Financing) product RBF products should use LIABILITY_TRANSFER
     * financial activity (mapped to GL 200040) Other products should use their product-specific FUND_SOURCE This method
     * queries the database to get product short_name, making it environment-agnostic (works across UAT/PROD with
     * different product IDs)
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
            // Query product short_name from database
            String sql = "SELECT short_name FROM m_product_loan WHERE id = ?";
            String shortName = this.jdbcTemplate.queryForObject(sql, String.class, loanProductId);

            return RBF_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (Exception e) {
            // If query fails, log and return false (use product FUND_SOURCE as fallback)
            // This ensures the system continues to work even if there's a DB issue
            return false;
        }
    }

    @Override
    public void createJournalEntriesForLoan(final LoanDTO loanDTO) {
        final Long officeId = loanDTO.getOfficeId();
        final GLClosure latestGLClosure = this.helper.getLatestClosureByBranch(officeId);
        final Long loanProductId = loanDTO.getLoanProductId();
        final String currencyCode = loanDTO.getCurrencyCode();
        final Office office = this.helper.getOfficeById(officeId);
        for (final LoanTransactionDTO loanTransactionDTO : loanDTO.getNewLoanTransactions()) {
            final LocalDate transactionDate = loanTransactionDTO.getTransactionDate();
            final String transactionId = loanTransactionDTO.getTransactionId();
            final Long paymentTypeId = loanTransactionDTO.getPaymentTypeId();
            final Long loanId = loanDTO.getLoanId();
            final LoanTransactionEnumData transactionType = loanTransactionDTO.getTransactionType();

            this.helper.checkForBranchClosures(latestGLClosure, transactionDate);

            if (loanTransactionDTO.isReversed()) {
                journalEntryWritePlatformService.createJournalEntryForReversedLoanTransaction(transactionDate, transactionId, officeId);
                continue;
            }

            /** Handle Disbursements **/
            if (transactionType.isDisbursement()) {
                createJournalEntriesForDisbursements(loanDTO, loanTransactionDTO, office);
            }
            /***
             * Logic for repayments, repayments at disbursement (except charge adjustment)
             ***/
            else if ((transactionType.isRepaymentType() && !transactionType.isChargeAdjustment())
                    || transactionType.isRepaymentAtDisbursement() || transactionType.isChargePayment()
                    || transactionType.isVatDeductionAtDisbursement()) {
                createJournalEntriesForRepayments(loanDTO, loanTransactionDTO, office);
            }

            /** Logic for handling recovery payments **/
            else if (transactionType.isRecoveryRepayment()) {
                createJournalEntriesForRecoveryRepayments(loanDTO, loanTransactionDTO, office);
            }

            /** Logic for Refunds of Overpayments **/
            else if (transactionType.isRefund()) {
                createJournalEntriesForRefund(loanDTO, loanTransactionDTO, office);
            }

            /** Logic for Credit Balance Refunds **/
            else if (transactionType.isCreditBalanceRefund()) {
                createJournalEntriesForCreditBalanceRefund(loanDTO, loanTransactionDTO, office);
            }

            /***
             * Only principal write off affects cash based accounting (interest and fee write off need not be
             * considered). Debit losses written off and credit Loan Portfolio
             **/
            else if (transactionType.isWriteOff()) {
                final BigDecimal principalAmount = loanTransactionDTO.getPrincipal();
                if (principalAmount != null && principalAmount.compareTo(BigDecimal.ZERO) > 0) {
                    this.helper.createJournalEntriesForLoan(office, currencyCode,
                            AccountingConstants.CashAccountsForLoan.LOSSES_WRITTEN_OFF.getValue(),
                            AccountingConstants.CashAccountsForLoan.LOAN_PORTFOLIO.getValue(), loanProductId, paymentTypeId, loanId,
                            transactionId, transactionDate, principalAmount);

                }
            } else if (transactionType.isInitiateTransfer() || transactionType.isApproveTransfer()
                    || transactionType.isWithdrawTransfer()) {
                createJournalEntriesForTransfers(loanDTO, loanTransactionDTO, office);
            }
            /** Logic for Refunds of Active Loans **/
            else if (transactionType.isRefundForActiveLoans()) {
                createJournalEntriesForRefundForActiveLoan(loanDTO, loanTransactionDTO, office);
            }
            // Logic for Chargebacks
            else if (transactionType.isChargeback()) {
                createJournalEntriesForChargeback(loanDTO, loanTransactionDTO, office);
            }
            // Logic for Charge Adjustment
            else if (transactionType.isChargeAdjustment()) {
                createJournalEntriesForChargeAdjustment(loanDTO, loanTransactionDTO, office);
            }
            // Logic for Charge-Off
            else if (transactionType.isChargeoff()) {
                createJournalEntriesForChargeOff(loanDTO, loanTransactionDTO, office);
            }
        }
    }

    /**
     * Debit loan Portfolio and credit Fund source for a Disbursement <br/>
     *
     * @param loanDTOSuper
     * @param loanTransactionDTO
     * @param office
     */
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

        if (MathUtil.isGreaterThanZero(principalPortion)) {
            this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.CashAccountsForLoan.LOAN_PORTFOLIO.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                    transactionDate, principalPortion);
        }

        if (MathUtil.isGreaterThanZero(overpaymentPortion)) {
            this.helper.createDebitJournalEntryForLoan(office, currencyCode, AccountingConstants.CashAccountsForLoan.OVERPAYMENT.getValue(),
                    loanProductId, paymentTypeId, loanId, transactionId, transactionDate, overpaymentPortion);
        }
        if (loanTransactionDTO.isLoanToLoanTransfer()) {
            this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.FinancialActivity.ASSET_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                    transactionDate, loanTransactionDTO.getAmount());
        } else if (loanTransactionDTO.isAccountTransfer()) {
            // For account transfers (disburse to savings), use product-specific logic
            // Only RBF products should use LIABILITY_TRANSFER (mapped to GL 200040)
            // Other products should use their product-specific FUND_SOURCE
            if (isRBFProduct(loanProductId)) {
                // RBF product: Use LIABILITY_TRANSFER (maps to GL 200040)
                this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                        AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId,
                        transactionId, transactionDate, loanDTO.getNetDisbursalAmount());
            } else {
                // Non-RBF products: Use product-specific FUND_SOURCE
                this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                        AccountingConstants.CashAccountsForLoan.FUND_SOURCE.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                        transactionDate, loanDTO.getNetDisbursalAmount());
            }
        } else {
            this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.CashAccountsForLoan.FUND_SOURCE.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                    transactionDate, loanTransactionDTO.getAmount());
        }
    }

    @Override
    protected void createJournalEntriesForLoanRepayments(LoanDTO loanDTO, LoanTransactionDTO loanTransactionDTO, Office office) {
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
        final BigDecimal penaltiesAmount = loanTransactionDTO.getPenalties();
        final BigDecimal overPaymentAmount = loanTransactionDTO.getOverPayment();
        final Long paymentTypeId = loanTransactionDTO.getPaymentTypeId();

        BigDecimal totalDebitAmount = new BigDecimal(0);
        Map<Integer, BigDecimal> debitAccountMapForGoodwillCredit = new LinkedHashMap<>();

        if (principalAmount != null && principalAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(principalAmount);
            this.helper.createCreditJournalEntryForLoan(office, currencyCode, AccountingConstants.CashAccountsForLoan.LOAN_PORTFOLIO,
                    loanProductId, paymentTypeId, loanId, transactionId, transactionDate, principalAmount);
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, principalAmount,
                        AccountingConstants.CashAccountsForLoan.GOODWILL_CREDIT.getValue(), debitAccountMapForGoodwillCredit,
                        paymentTypeId);
            }
        }

        if (interestAmount != null && interestAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(interestAmount);
            this.helper.createCreditJournalEntryForLoan(office, currencyCode, AccountingConstants.CashAccountsForLoan.INTEREST_ON_LOANS,
                    loanProductId, paymentTypeId, loanId, transactionId, transactionDate, interestAmount);
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, interestAmount,
                        AccountingConstants.CashAccountsForLoan.INCOME_FROM_GOODWILL_CREDIT_INTEREST.getValue(),
                        debitAccountMapForGoodwillCredit, paymentTypeId);
            }
        }

        if (feesAmount != null && feesAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(feesAmount);

            if (loanTransactionDTO.getTransactionType().isVatDeductionAtDisbursement()) {
                this.customAccountingProcessorHelper.createCreditJournalEntryForLoanCharges(office, currencyCode, loanId, transactionId,
                        transactionDate, feesAmount, loanTransactionDTO.getFeePayments());
            } else {
                this.helper.createCreditJournalEntryForLoanCharges(office, currencyCode,
                        AccountingConstants.CashAccountsForLoan.INCOME_FROM_FEES.getValue(), loanProductId, loanId, transactionId,
                        transactionDate, feesAmount, loanTransactionDTO.getFeePayments());
            }
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, feesAmount,
                        AccountingConstants.CashAccountsForLoan.INCOME_FROM_GOODWILL_CREDIT_FEES.getValue(),
                        debitAccountMapForGoodwillCredit, paymentTypeId);
            }
        }

        if (penaltiesAmount != null && penaltiesAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(penaltiesAmount);
            this.helper.createCreditJournalEntryForLoanCharges(office, currencyCode,
                    AccountingConstants.CashAccountsForLoan.INCOME_FROM_PENALTIES.getValue(), loanProductId, loanId, transactionId,
                    transactionDate, penaltiesAmount, loanTransactionDTO.getPenaltyPayments());
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, penaltiesAmount,
                        AccountingConstants.CashAccountsForLoan.INCOME_FROM_GOODWILL_CREDIT_PENALTY.getValue(),
                        debitAccountMapForGoodwillCredit, paymentTypeId);
            }
        }

        if (overPaymentAmount != null && overPaymentAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(overPaymentAmount);
            this.helper.createCreditJournalEntryForLoan(office, currencyCode, AccountingConstants.CashAccountsForLoan.OVERPAYMENT,
                    loanProductId, paymentTypeId, loanId, transactionId, transactionDate, overPaymentAmount);
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, overPaymentAmount,
                        AccountingConstants.CashAccountsForLoan.GOODWILL_CREDIT.getValue(), debitAccountMapForGoodwillCredit,
                        paymentTypeId);
            }
        }

        /*** create a single debit entry for the entire amount **/
        if (loanTransactionDTO.isLoanToLoanTransfer()) {
            this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.FinancialActivity.ASSET_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                    transactionDate, totalDebitAmount);
        } else if (loanTransactionDTO.isAccountTransfer()) {
            this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId,
                    transactionId, transactionDate, totalDebitAmount);
        } else {
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {

                // create debit entries
                for (Map.Entry<Integer, BigDecimal> debitEntry : debitAccountMapForGoodwillCredit.entrySet()) {
                    this.helper.createDebitJournalEntryForLoan(office, currencyCode, debitEntry.getKey(), loanProductId, paymentTypeId,
                            loanId, transactionId, transactionDate, debitEntry.getValue());
                }

            } else {

                if (!loanTransactionDTO.getTransactionType().isRepaymentAtDisbursement()
                        && !loanTransactionDTO.getTransactionType().isVatDeductionAtDisbursement()) {
                    this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                            AccountingConstants.CashAccountsForLoan.FUND_SOURCE.getValue(), loanProductId, paymentTypeId, loanId,
                            transactionId, transactionDate, totalDebitAmount);
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
                    AccountingConstants.CashAccountsForLoan.FUND_SOURCE.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                    transactionDate, totalDebitAmount);
        }
    }
}
