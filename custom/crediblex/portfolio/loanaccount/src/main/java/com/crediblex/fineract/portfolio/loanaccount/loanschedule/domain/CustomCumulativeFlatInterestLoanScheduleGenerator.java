package com.crediblex.fineract.portfolio.loanaccount.loanschedule.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.CumulativeFlatInterestLoanScheduleGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.PaymentPeriodsInOneYearCalculator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.PrincipalInterest;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ScheduledDateGenerator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CustomCumulativeFlatInterestLoanScheduleGenerator extends CumulativeFlatInterestLoanScheduleGenerator {

    public CustomCumulativeFlatInterestLoanScheduleGenerator(ScheduledDateGenerator scheduledDateGenerator,
            PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator) {
        super(scheduledDateGenerator, paymentPeriodsInOneYearCalculator);
    }

    @Override
    public PrincipalInterest calculatePrincipalInterestComponentsForPeriod(final PaymentPeriodsInOneYearCalculator calculator,
            final BigDecimal interestCalculationGraceOnRepaymentPeriodFraction, final Money totalCumulativePrincipal,
            Money totalCumulativeInterest, Money totalInterestDueForLoan, final Money cumulatingInterestPaymentDueToGrace,
            final Money outstandingBalance, final LoanApplicationTerms loanApplicationTerms, final int periodNumber, final MathContext mc,
            @SuppressWarnings("unused") TreeMap<LocalDate, Money> principalVariation,
            @SuppressWarnings("unused") Map<LocalDate, Money> compoundingMap, LocalDate periodStartDate, LocalDate periodEndDate,
            @SuppressWarnings("unused") Collection<LoanTermVariationsData> termVariations) {

        // As long as its a line of credit loan, deduct interest from principal
        // NEW: Check if interest should be deducted from principal
        if (loanApplicationTerms.getIsLineOfCredit()) {
            return calculateFlatInterestWithDeduction(calculator, interestCalculationGraceOnRepaymentPeriodFraction,
                    totalCumulativePrincipal, totalCumulativeInterest, totalInterestDueForLoan, cumulatingInterestPaymentDueToGrace,
                    loanApplicationTerms, periodNumber, mc, periodStartDate, periodEndDate);
        }

        final PrincipalInterest result = loanApplicationTerms.calculateTotalInterestForPeriod(calculator,
                interestCalculationGraceOnRepaymentPeriodFraction, periodNumber, mc, cumulatingInterestPaymentDueToGrace,
                outstandingBalance, periodStartDate, periodEndDate);
        Money interestForThisInstallment = result.interest();

        Money principalForThisInstallment = loanApplicationTerms.calculateTotalPrincipalForPeriod(calculator, outstandingBalance,
                periodNumber, mc, interestForThisInstallment);

        // update cumulative fields for principal & interest
        final Money interestBroughtForwardDueToGrace = result.interestPaymentDueToGrace();
        final Money totalCumulativePrincipalToDate = totalCumulativePrincipal.plus(principalForThisInstallment);
        final Money totalCumulativeInterestToDate = totalCumulativeInterest.plus(interestForThisInstallment);

        // adjust if needed
        principalForThisInstallment = loanApplicationTerms.adjustPrincipalIfLastRepaymentPeriod(principalForThisInstallment,
                totalCumulativePrincipalToDate, periodNumber);

        // totalCumulativeInterest from partial schedule generation for multi
        // rescheduling
        /*
         * if (loanApplicationTerms.getPartialTotalCumulativeInterest() != null &&
         * loanApplicationTerms.getTotalInterestDue() != null) { totalInterestDueForLoan =
         * loanApplicationTerms.getTotalInterestDue(); totalInterestDueForLoan =
         * totalInterestDueForLoan.plus(loanApplicationTerms. getPartialTotalCumulativeInterest()); }
         */
        interestForThisInstallment = loanApplicationTerms.adjustInterestIfLastRepaymentPeriod(interestForThisInstallment,
                totalCumulativeInterestToDate, totalInterestDueForLoan, periodNumber);

        if (loanApplicationTerms.getIsLineOfCredit() && loanApplicationTerms.getIsReceivableLineOfCredit()) {
            principalForThisInstallment = principalForThisInstallment.minus(interestForThisInstallment);
        }

        return new PrincipalInterest(principalForThisInstallment, interestForThisInstallment, interestBroughtForwardDueToGrace);
    }

    /**
     * NEW METHOD: Calculate flat interest with deduction from fixed installment
     *
     * For Receivable Line of Credit / Discounted Loans: - Total Interest = Principal × Rate × Time (calculated once) -
     * Total Interest is DEDUCTED from disbursement upfront - Amount Disbursed = Principal - Total Interest - Fixed
     * Installment = Principal / Number of Periods - Interest per Period = Total Interest / Number of Periods - Actual
     * Principal per Period = Fixed Installment - Interest per Period
     *
     * Example: $10,000 loan, 12% annual, 10 months - Total Interest = $1,000 - Disbursed = $9,000 (customer receives
     * this) - Fixed Installment = $1,000 - Interest per period = $100 - Principal per period = $900 - After 10 periods:
     * $900 × 10 = $9,000 (matches disbursement!)
     */
    private PrincipalInterest calculateFlatInterestWithDeduction(final PaymentPeriodsInOneYearCalculator calculator,
            final BigDecimal interestCalculationGraceOnRepaymentPeriodFraction, final Money totalCumulativeInterest,
            final Money totalInterestDueForLoan, final Money cumulatingInterestPaymentDueToGrace, final Money outstandingBalance,
            final LoanApplicationTerms loanApplicationTerms, final int periodNumber, final MathContext mc, final LocalDate periodStartDate,
            final LocalDate periodEndDate) {

        // Step 1: Get the NOMINAL principal (loan amount)
        Money nominalPrincipal = loanApplicationTerms.getPrincipal();
        int totalPeriods = loanApplicationTerms.getNumberOfRepayments();

        // Step 2: Calculate FIXED installment amount (based on NOMINAL principal)
        Money fixedInstallmentAmount = nominalPrincipal.dividedBy(totalPeriods, mc);

        // Handle installment multiples if configured
        if (loanApplicationTerms.getInstallmentAmountInMultiplesOf() != null) {
            fixedInstallmentAmount = Money.roundToMultiplesOf(fixedInstallmentAmount,
                    loanApplicationTerms.getInstallmentAmountInMultiplesOf());
        }

        // Step 3: Calculate INTEREST for this period
        // For flat rate, interest is same in each period (total interest / periods)
        final PrincipalInterest result = loanApplicationTerms.calculateTotalInterestForPeriod(calculator,
                interestCalculationGraceOnRepaymentPeriodFraction, periodNumber, mc, cumulatingInterestPaymentDueToGrace,
                outstandingBalance, periodStartDate, periodEndDate);

        Money interestForThisInstallment = result.interest();
        Money cumulatingInterestDueToGrace = result.interestPaymentDueToGrace();

        // Step 4: Calculate ACTUAL principal = Fixed Installment - Interest
        // This represents the portion of the DISBURSED amount being repaid
        Money principalForThisInstallment = fixedInstallmentAmount.minus(interestForThisInstallment);

        // Safety check: Principal cannot be negative
        if (principalForThisInstallment.isLessThanZero()) {
            // Interest exceeds fixed installment - set principal to zero
            principalForThisInstallment = principalForThisInstallment.zero();
        }

        final Money interestBroughtForwardDueToGrace = cumulatingInterestDueToGrace;

        // Adjust interest for last period if needed (standard flat rate adjustment)
        interestForThisInstallment = loanApplicationTerms.adjustInterestIfLastRepaymentPeriod(interestForThisInstallment,
                totalCumulativeInterest.plus(interestForThisInstallment), totalInterestDueForLoan, periodNumber);

        return new PrincipalInterest(principalForThisInstallment, interestForThisInstallment, interestBroughtForwardDueToGrace);
    }

    @Override
    public Money getPrincipalToBeScheduled(final LoanApplicationTerms loanApplicationTerms) {
        Money principalToBeScheduled;
        if (loanApplicationTerms.isMultiDisburseLoan()) {
            if (loanApplicationTerms.getTotalDisbursedAmount().isGreaterThanZero()) {
                principalToBeScheduled = loanApplicationTerms.getTotalMultiDisbursedAmount();
            } else if (loanApplicationTerms.getApprovedPrincipal().isGreaterThanZero()) {
                principalToBeScheduled = loanApplicationTerms.getApprovedPrincipal();
            } else {
                principalToBeScheduled = loanApplicationTerms.getPrincipal();
            }
        } else {
            principalToBeScheduled = loanApplicationTerms.getPrincipal();
        }

        if (loanApplicationTerms.getIsLineOfCredit() && loanApplicationTerms.getIsReceivableLineOfCredit()
                && loanApplicationTerms.getInterestMethod().isFlat()) {
            Money totalInterest = loanApplicationTerms.getTotalInterestDue();

            if (totalInterest != null && totalInterest.isGreaterThanZero()) {
                principalToBeScheduled = principalToBeScheduled.minus(totalInterest);
            }
        }

        return principalToBeScheduled.minus(loanApplicationTerms.getDownPaymentAmount());
    }
}
