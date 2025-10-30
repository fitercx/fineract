package com.crediblex.fineract.portfolio.loanaccount.service;

import java.time.LocalDate;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDisbursementValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;

public class CustomLoanDisbursementService extends LoanDisbursementService {

    public CustomLoanDisbursementService(LoanChargeValidator loanChargeValidator, LoanDisbursementValidator loanDisbursementValidator,
            ReprocessLoanTransactionsService reprocessLoanTransactionsService) {
        super(loanChargeValidator, loanDisbursementValidator, reprocessLoanTransactionsService);
    }

    public void handleDisbursementTransaction(final Loan loan, final LocalDate disbursedOn, final PaymentDetail paymentDetail) {
        // add repayment transaction to track incoming money from client to mfi for (charges due at time of
        // disbursement)

        final Money totalFeeChargesDueAtDisbursement = loan.getSummary().getTotalFeeChargesDueAtDisbursement(loan.getCurrency());

        Money feeAndPenaltyPortion = Money.zero(loan.getCurrency());
        Money taxesPaid = Money.zero(loan.getCurrency());

        final LoanTransaction chargesPayment = LoanTransaction.repaymentAtDisbursement(loan.getOffice(), feeAndPenaltyPortion,
                paymentDetail, disbursedOn, null);
        final LoanTransaction taxPaymentTransaction = LoanTransaction.vatDeductionAtDisbursement(loan.getOffice(), taxesPaid, paymentDetail,
                disbursedOn, null);

        final Integer installmentNumber = null;
        for (final LoanCharge charge : loan.getActiveCharges()) {
            LocalDate actualDisbursementDate = loan.getActualDisbursementDate(charge);
            if ((charge.getCharge().getChargeTimeType().equals(ChargeTimeType.DISBURSEMENT.getValue())
                    && disbursedOn.equals(actualDisbursementDate) && !charge.isWaived() && !charge.isFullyPaid())
                    || (charge.getCharge().getChargeTimeType().equals(ChargeTimeType.TRANCHE_DISBURSEMENT.getValue())
                            && disbursedOn.equals(actualDisbursementDate) && !charge.isWaived() && !charge.isFullyPaid())) {
                if (totalFeeChargesDueAtDisbursement.isGreaterThanZero() && !charge.getChargePaymentMode().isPaymentModeAccountTransfer()) {
                    charge.markAsFullyPaid();
                    final LoanChargePaidBy loanChargePaidBy = new LoanChargePaidBy(chargesPayment, charge, charge.amount(),
                            installmentNumber);
                    chargesPayment.getLoanChargesPaid().add(loanChargePaidBy);
                    // Treat all such charge amounts as fee portion (no longer misusing interest portion here)
                    feeAndPenaltyPortion = feeAndPenaltyPortion.plus(charge.amount());
                    if (charge.hasTax()) {
                        taxesPaid = taxesPaid.plus(charge.getTaxAmount());
                        final LoanChargePaidBy taxChargePaidBy = new LoanChargePaidBy(chargesPayment, charge, charge.getTaxAmount(),
                                installmentNumber);
                        taxPaymentTransaction.getLoanChargesPaid().add(taxChargePaidBy);
                        charge.markAsFullyPaidWithTaxes();
                    }
                }
            } else if (disbursedOn.equals(loan.getActualDisbursementDate())
                    && loan.isNoneOrCashOrUpfrontAccrualAccountingEnabledOnLoanProduct()) {
                loan.handleChargeAppliedTransaction(charge, disbursedOn);
            }
        }

        if (feeAndPenaltyPortion.isGreaterThanZero()) {
            final Money zero = Money.zero(loan.getCurrency());
            chargesPayment.updateComponentsAndTotal(zero, zero, feeAndPenaltyPortion, zero);
            chargesPayment.updateLoan(loan);
            loan.addLoanTransaction(chargesPayment);
            loan.updateLoanOutstandingBalances();
        }

        if (taxesPaid.isGreaterThanZero()) {
            final Money zero = Money.zero(loan.getCurrency());
            taxPaymentTransaction.updateComponentsAndTotal(zero, zero, taxesPaid, zero);
            taxPaymentTransaction.updateLoan(loan);
            loan.addLoanTransaction(taxPaymentTransaction);
            loan.updateLoanOutstandingBalances();
        }

        final LocalDate expectedDate = loan.getExpectedFirstRepaymentOnDate();
        loanDisbursementValidator.validateDisburseDate(loan, disbursedOn, expectedDate);
    }
}
