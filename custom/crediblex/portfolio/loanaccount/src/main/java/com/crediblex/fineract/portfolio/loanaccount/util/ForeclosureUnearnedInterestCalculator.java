package com.crediblex.fineract.portfolio.loanaccount.util;

import java.math.BigDecimal;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;

/**
 * Computes interest that was scheduled on the loan but not earned/collected because the loan was foreclosed early.
 */
public final class ForeclosureUnearnedInterestCalculator {

    private ForeclosureUnearnedInterestCalculator() {}

    /**
     * Must be called before the foreclosure schedule rewrite removes future installments.
     */
    public static BigDecimal computeFromInstallments(final Loan loan, final Money interestPayableAtForeclosure) {
        if (loan == null || interestPayableAtForeclosure == null) {
            return BigDecimal.ZERO;
        }

        final MonetaryCurrency currency = loan.getCurrency();
        Money totalScheduledInterest = Money.zero(currency);
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            totalScheduledInterest = totalScheduledInterest.plus(installment.getInterestCharged(currency));
        }

        Money interestAlreadyPaid = Money.zero(currency);
        final LoanSummary summary = loan.getSummary();
        if (summary != null && summary.getTotalInterestRepaid() != null) {
            interestAlreadyPaid = Money.of(currency, summary.getTotalInterestRepaid());
        }

        final Money totalInterestCollectedOrDue = interestAlreadyPaid.plus(interestPayableAtForeclosure);
        final Money unearnedInterest = totalScheduledInterest.minus(totalInterestCollectedOrDue);
        if (unearnedInterest.isLessThanZero()) {
            return BigDecimal.ZERO;
        }
        return unearnedInterest.getAmount();
    }

    /**
     * Fallback for foreclosed loans where the value was not stored at closure time.
     */
    public static BigDecimal computeFromOriginalScheduleHistory(final BigDecimal originalScheduleInterest, final BigDecimal interestPaid,
            final BigDecimal interestOutstanding) {
        final BigDecimal paid = interestPaid != null ? interestPaid : BigDecimal.ZERO;
        final BigDecimal outstanding = interestOutstanding != null ? interestOutstanding : BigDecimal.ZERO;
        final BigDecimal scheduled = originalScheduleInterest != null ? originalScheduleInterest : BigDecimal.ZERO;
        final BigDecimal unearned = scheduled.subtract(paid).subtract(outstanding);
        return unearned.compareTo(BigDecimal.ZERO) > 0 ? unearned : BigDecimal.ZERO;
    }
}
