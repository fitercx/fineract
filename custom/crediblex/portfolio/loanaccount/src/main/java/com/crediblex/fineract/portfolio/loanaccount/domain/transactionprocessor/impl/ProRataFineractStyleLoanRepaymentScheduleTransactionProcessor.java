package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.FineractStyleLoanRepaymentScheduleTransactionProcessor;

public class ProRataFineractStyleLoanRepaymentScheduleTransactionProcessor extends FineractStyleLoanRepaymentScheduleTransactionProcessor {

    public static final String STRATEGY_CODE = "pro-rata-mifos-standard-strategy";
    public static final String STRATEGY_NAME = "Pro-Rata Penalties, Fees, Interest, Principal order";

    public ProRataFineractStyleLoanRepaymentScheduleTransactionProcessor(ExternalIdFactory externalIdFactory) {
        super(externalIdFactory);
    }

    @Override
    public String getCode() {
        return STRATEGY_CODE;
    }

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    protected Money handleTransactionThatIsPaymentInAdvanceOfInstallment(final LoanRepaymentScheduleInstallment currentInstallment,
            final List<LoanRepaymentScheduleInstallment> installments, final LoanTransaction loanTransaction, final Money paymentInAdvance,
            List<LoanTransactionToRepaymentScheduleMapping> transactionMappings, Set<LoanCharge> charges) {

        // Calculate pro-rated interest based on actual days
        MonetaryCurrency currency = loanTransaction.getLoan().getCurrency();
        Money proRatedInterest = calculateProRatedInterest(currentInstallment, loanTransaction.getTransactionDate(), currency);

        return handleAdvancePaymentWithProRatedInterest(currentInstallment, loanTransaction, paymentInAdvance, proRatedInterest,
                transactionMappings);
    }

    private Money calculateProRatedInterest(LoanRepaymentScheduleInstallment installment, LocalDate paymentDate,
            MonetaryCurrency currency) {

        LocalDate fromDate = installment.getFromDate();
        LocalDate dueDate = installment.getDueDate();
        Money fullInterest = installment.getInterestCharged(currency);

        // Calculate actual days vs scheduled days
        long totalDays = ChronoUnit.DAYS.between(fromDate, dueDate);
        long actualDays = ChronoUnit.DAYS.between(fromDate, paymentDate);

        if (actualDays >= totalDays) {
            // Payment made on or after due date - use full interest
            return fullInterest;
        }

        // Pro-rate the interest
        BigDecimal proRataRatio = BigDecimal.valueOf(actualDays).divide(BigDecimal.valueOf(totalDays), MathContext.DECIMAL64);

        return fullInterest.multipliedBy(proRataRatio);
    }

    /**
     * Handles advance payment with pro-rated interest using Mifos-style payment allocation. Payment order: Penalties →
     * Fees → Interest (pro-rated) → Principal
     */
    private Money handleAdvancePaymentWithProRatedInterest(final LoanRepaymentScheduleInstallment currentInstallment,
            final LoanTransaction loanTransaction, final Money transactionAmountUnprocessed, final Money proRatedInterest,
            List<LoanTransactionToRepaymentScheduleMapping> transactionMappings) {

        final LocalDate transactionDate = loanTransaction.getTransactionDate();
        final MonetaryCurrency currency = transactionAmountUnprocessed.getCurrency();
        Money transactionAmountRemaining = transactionAmountUnprocessed;

        Money principalPortion = Money.zero(currency);
        Money interestPortion = Money.zero(currency);
        Money feeChargesPortion = Money.zero(currency);
        Money penaltyChargesPortion = Money.zero(currency);

        // 1. Pay penalty charges first
        penaltyChargesPortion = currentInstallment.payPenaltyChargesComponent(transactionDate, transactionAmountRemaining);
        transactionAmountRemaining = transactionAmountRemaining.minus(penaltyChargesPortion);

        // 2. Pay fee charges
        if (transactionAmountRemaining.isGreaterThanZero()) {
            feeChargesPortion = currentInstallment.payFeeChargesComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(feeChargesPortion);
        }

        // 3. Pay pro-rated interest (not full interest)
        if (transactionAmountRemaining.isGreaterThanZero()) {
            Money interestOutstanding = currentInstallment.getInterestOutstanding(currency);
            Money interestAlreadyPaid = currentInstallment.getInterestPaid(currency);

            // Calculate how much pro-rated interest remains to be paid
            Money interestToPay = proRatedInterest.minus(interestAlreadyPaid);

            if (currentInstallment.isRecievableLineOfCreditInstallment()) {
                interestToPay = proRatedInterest;
            }

            // Ensure we don't pay more than outstanding
            if (interestToPay.isGreaterThan(interestOutstanding) && !currentInstallment.isRecievableLineOfCreditInstallment()) {
                interestToPay = interestOutstanding;
            }

            // Ensure we don't pay more than transaction amount remaining
            if (interestToPay.isGreaterThan(transactionAmountRemaining)) {
                interestToPay = transactionAmountRemaining;
            }

            // Only pay if there's interest to pay
            if (interestToPay.isGreaterThanZero()) {
                interestPortion = currentInstallment.payInterestComponent(transactionDate, interestToPay);
                transactionAmountRemaining = transactionAmountRemaining.minus(interestPortion);
            }
        }

        // 4. Pay principal last
        if (transactionAmountRemaining.isGreaterThanZero()) {

            Money principalToPay = transactionAmountRemaining;
            if (currentInstallment.isRecievableLineOfCreditInstallment()) {
                principalToPay = currentInstallment.getPrincipal(currency).minus(currentInstallment.getPrincipalCompleted(currency))
                        .minus(currentInstallment.getInterestCharged());

            }

            principalPortion = currentInstallment.payPrincipalComponent(transactionDate, principalToPay, true);
            transactionAmountRemaining = transactionAmountRemaining.minus(principalPortion);
        }

        // Update transaction components
        loanTransaction.updateComponents(principalPortion, interestPortion, feeChargesPortion, penaltyChargesPortion);

        // Create mapping if any amount was paid
        if (principalPortion.plus(interestPortion).plus(feeChargesPortion).plus(penaltyChargesPortion).isGreaterThanZero()) {
            transactionMappings.add(LoanTransactionToRepaymentScheduleMapping.createFrom(loanTransaction, currentInstallment,
                    principalPortion, interestPortion, feeChargesPortion, penaltyChargesPortion));
        }

        return transactionAmountRemaining;
    }
}
