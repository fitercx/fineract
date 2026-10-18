package com.crediblex.fineract.portfolio.loanaccount.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * Installments a backdated partial repayment will finish.
 * <p>
 * Allocation is oldest installment first: that installment's LPI dated strictly before the value date, then its fees,
 * tax, interest and principal. When the amount finishes an installment and money remains, that installment's LPI on or
 * after the value date is waived. The remainder then follows the same order on the next installment. An installment
 * this payment does not finish keeps its later LPI.
 */
public final class PartialBackdatedClearedInstallments {

    private static final BigDecimal CLEAR_TOLERANCE = new BigDecimal("0.01");

    private PartialBackdatedClearedInstallments() {}

    public static Set<Integer> installmentNumbersClearedBy(final Loan loan, final LocalDate settlementDate,
            final BigDecimal transactionAmount) {
        if (loan == null || settlementDate == null || transactionAmount == null || loan.getCurrency() == null
                || loan.getRepaymentScheduleInstallments() == null) {
            return Set.of();
        }
        final MonetaryCurrency currency = loan.getCurrency();
        Money remaining = Money.of(currency, transactionAmount);
        if (!remaining.isGreaterThanZero()) {
            return Set.of();
        }
        final Money tolerance = Money.of(currency, CLEAR_TOLERANCE);
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(loan.getRepaymentScheduleInstallments());
        installments.sort(Comparator.comparing(
                installment -> installment.getInstallmentNumber() == null ? Integer.MAX_VALUE : installment.getInstallmentNumber()));

        final Set<Integer> cleared = new LinkedHashSet<>();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment == null || installment.getInstallmentNumber() == null) {
                continue;
            }
            final Money obligation = obligationBeforeValueDate(loan, installment, settlementDate, currency);
            if (!obligation.isGreaterThanZero()) {
                continue;
            }
            if (remaining.plus(tolerance).isLessThan(obligation)) {
                break;
            }
            remaining = remaining.minus(obligation);
            cleared.add(installment.getInstallmentNumber());
            if (!remaining.isGreaterThanZero()) {
                break;
            }
        }
        return cleared;
    }

    private static Money obligationBeforeValueDate(final Loan loan, final LoanRepaymentScheduleInstallment installment,
            final LocalDate settlementDate, final MonetaryCurrency currency) {
        Money obligation = preValueDatePenalty(loan, installment.getInstallmentNumber(), settlementDate, currency);
        obligation = obligation.plus(installment.getFeeChargesOutstanding(currency));
        obligation = obligation.plus(installment.getTaxChargesOutstanding(currency));
        obligation = obligation.plus(installment.getInterestOutstanding(currency));
        obligation = obligation.plus(installment.getPrincipalOutstanding(currency));
        return obligation;
    }

    private static Money preValueDatePenalty(final Loan loan, final Integer installmentNumber, final LocalDate settlementDate,
            final MonetaryCurrency currency) {
        Money total = Money.zero(currency);
        if (loan.getCharges() == null) {
            return total;
        }
        for (final LoanCharge charge : loan.getCharges()) {
            if (!isUnpaidOverdueCharge(charge, currency)) {
                continue;
            }
            final LoanOverdueInstallmentCharge link = charge.getOverdueInstallmentCharge();
            if (link == null || link.getInstallment() == null || !installmentNumber.equals(link.getInstallment().getInstallmentNumber())) {
                continue;
            }
            final LocalDate dueDate = charge.getDueLocalDate();
            if (dueDate == null || !dueDate.isBefore(settlementDate)) {
                continue;
            }
            total = total.plus(charge.getAmountOutstanding(currency));
        }
        return total;
    }

    private static boolean isUnpaidOverdueCharge(final LoanCharge charge, final MonetaryCurrency currency) {
        if (charge == null || !charge.isActive() || charge.isWaived() || !charge.isOverdueInstallmentCharge()) {
            return false;
        }
        final Money outstanding = charge.getAmountOutstanding(currency);
        return outstanding != null && outstanding.isGreaterThanZero();
    }
}
