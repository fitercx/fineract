package com.crediblex.fineract.portfolio.loanaccount.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;

/**
 * Ensures foreclosure repayment transactions carry principal/interest/fee/penalty/tax portions for UI and reporting.
 */
public final class ForeclosureTransactionBreakdown {

    private ForeclosureTransactionBreakdown() {}

    /**
     * Sets the transaction's component breakdown from already-reconciled (overpayment-clamped) amounts. Prefer this
     * over {@link #applyIfMissing} on the foreclosure settlement path, where the amounts have been trimmed by
     * {@code ForeclosureAmountReconciler} - recomputing here from the raw schedule/summary would undo that clamp and
     * re-introduce the overpayment.
     */
    public static void applyComponents(final LoanTransaction loanTransaction, final Money principal, final Money interest, final Money fees,
            final Money penalties, final Money taxes) {
        if (loanTransaction == null || !loanTransaction.isRepayment() || !hasMissingComponentBreakdown(loanTransaction)) {
            return;
        }
        loanTransaction.updateComponentsAndTotal(principal, interest, fees, penalties, taxes);
    }

    public static void applyIfMissing(final Loan loan, final LoanTransaction loanTransaction, final LocalDate foreclosureDate) {
        if (loan == null || loanTransaction == null || !loanTransaction.isRepayment() || !hasMissingComponentBreakdown(loanTransaction)) {
            return;
        }

        final MonetaryCurrency currency = loan.getCurrency();
        final LoanRepaymentScheduleInstallment foreclosureDetail = loan.fetchLoanForeclosureDetail(foreclosureDate);

        Money principal = foreclosureDetail.getPrincipal(currency);
        Money interest = foreclosureDetail.getInterestCharged(currency);
        Money fees = foreclosureDetail.getFeeChargesCharged(currency);
        Money penalties = ForeclosurePenaltyCalculator.computePenaltyPayableFromActiveCharges(loan, foreclosureDate, currency);
        Money taxes = foreclosureDetail.getTaxChargesCharged(currency);

        if (loan.isFactorRateEnabled()) {
            final LoanSummary loanSummary = loan.getSummary();
            if (loanSummary != null) {
                fees = Money.of(currency, loanSummary.getTotalFeeChargesOutstanding());
                taxes = Money.of(currency, loanSummary.getTotalTaxChargesOutstanding());
            }
        }

        loanTransaction.updateComponentsAndTotal(principal, interest, fees, penalties, taxes);
    }

    private static boolean hasMissingComponentBreakdown(final LoanTransaction loanTransaction) {
        final BigDecimal amount = loanTransaction.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        final BigDecimal allocated = zeroIfNull(loanTransaction.getPrincipalPortion()).add(zeroIfNull(loanTransaction.getInterestPortion()))
                .add(zeroIfNull(loanTransaction.getFeeChargesPortion())).add(zeroIfNull(loanTransaction.getPenaltyChargesPortion()))
                .add(zeroIfNull(loanTransaction.getTaxChargesPortion()));
        return allocated.compareTo(BigDecimal.ZERO) == 0;
    }

    private static BigDecimal zeroIfNull(final BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
