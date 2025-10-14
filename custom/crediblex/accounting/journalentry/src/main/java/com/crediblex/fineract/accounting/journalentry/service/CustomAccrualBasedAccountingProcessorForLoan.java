package com.crediblex.fineract.accounting.journalentry.service;

import com.crediblex.fineract.accounting.journalentry.CustomAccountingProcessorHelper;
import com.crediblex.fineract.accounting.journalentry.journalentry.CustomLoanDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fineract.accounting.common.AccountingConstants;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.data.LoanDTO;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.journalentry.service.AccrualBasedAccountingProcessorForLoan;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.organisation.office.domain.Office;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CustomAccrualBasedAccountingProcessorForLoan extends AccrualBasedAccountingProcessorForLoan {

    @Autowired
    protected CustomAccountingProcessorHelper customAccountingProcessorHelper;

    public CustomAccrualBasedAccountingProcessorForLoan(AccountingProcessorHelper helper,
            JournalEntryWritePlatformService journalEntryWritePlatformService) {
        super(helper, journalEntryWritePlatformService);
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
        if (loanTransactionDTO.isLoanToLoanTransfer()) {
            this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.FinancialActivity.ASSET_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId, transactionId,
                    transactionDate, loanTransactionDTO.getAmount());
        } else if (loanTransactionDTO.isAccountTransfer()) {
            // May not play so well with multi disbursal transactions
            this.helper.createCreditJournalEntryForLoan(office, currencyCode,
                    AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId,
                    transactionId, transactionDate, loanDTO.getNetDisbursalAmount());
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
                this.customAccountingProcessorHelper.createCreditJournalEntryForLoanCharges(office, currencyCode, loanId, transactionId,
                        transactionDate, feesAmount, loanTransactionDTO.getFeePayments(), true);
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
            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, feesAmount,
                        AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_GOODWILL_CREDIT_FEES.getValue(),
                        debitAccountMapForGoodwillCredit, paymentTypeId);
            }
        }

        // handle penalties payment of writeOff
        if (penaltiesAmount != null && penaltiesAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDebitAmount = totalDebitAmount.add(penaltiesAmount);
            if (isIncomeFromFee) {
                GLAccount account = this.helper.getLinkedGLAccountForLoanProduct(loanProductId,
                        AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_PENALTIES.getValue(), paymentTypeId);
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

            if (loanTransactionDTO.getTransactionType().isGoodwillCredit()) {
                populateDebitAccountEntry(loanProductId, penaltiesAmount,
                        AccountingConstants.AccrualAccountsForLoan.INCOME_FROM_GOODWILL_CREDIT_PENALTY.getValue(),
                        debitAccountMapForGoodwillCredit, paymentTypeId);
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

        /**
         * Single DEBIT transaction for write-offs or Repayments
         ***/
        if (totalDebitAmount.compareTo(BigDecimal.ZERO) > 0) {
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
                    this.helper.createDebitJournalEntryForLoan(office, currencyCode,
                            AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), loanProductId, paymentTypeId, loanId,
                            transactionId, transactionDate, totalDebitAmount);
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
}
