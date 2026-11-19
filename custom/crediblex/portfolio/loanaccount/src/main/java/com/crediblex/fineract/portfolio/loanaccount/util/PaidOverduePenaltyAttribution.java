package com.crediblex.fineract.portfolio.loanaccount.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;

/**
 * Keeps paid LPI on the installment that paid it when the schedule is rebuilt by effective-date windows.
 * <p>
 * Daily LPI after an EMI's due date is shown on the next period (see {@link OverdueChargeScheduleAllocationUtils}), but
 * repayments collect it on the EMI it is linked to. A rebuild that re-buckets charged LPI by date and keeps paid where
 * it was leaves the EMI with paid above charged (negative penalty outstanding, never completed) and the later row with
 * LPI that is already paid. Only the paid part of the EMI's own linked charges is moved back, and never more than the
 * later row's outstanding, so unpaid LPI stays on its date window and loan totals do not change.
 */
public final class PaidOverduePenaltyAttribution {

    private PaidOverduePenaltyAttribution() {}

    /**
     * Only loans with paid, linked LPI can need a shift; every other loan must take the pre-existing path unchanged.
     */
    public static boolean hasPaidLinkedOverduePenalty(final Collection<LoanCharge> charges, final MonetaryCurrency currency) {
        if (charges == null || currency == null) {
            return false;
        }
        for (final LoanCharge charge : charges) {
            if (isPaidLinkedOverduePenalty(charge, currency)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param projectedPenaltyOutstanding
     *            charged - paid - waived - written off per installment number, as the rebuild would leave it
     * @param displayInstallmentNumber
     *            installment number the rebuild puts each overdue charge on
     * @return charged LPI to add per installment number (negative = remove); empty when nothing moves
     */
    public static Map<Integer, Money> shifts(final Collection<LoanCharge> charges, final Map<Integer, Money> projectedPenaltyOutstanding,
            final Function<LoanCharge, Integer> displayInstallmentNumber, final MonetaryCurrency currency) {
        final Map<Integer, Money> shifts = new HashMap<>();
        if (charges == null || charges.isEmpty() || projectedPenaltyOutstanding == null || currency == null) {
            return shifts;
        }
        final Map<Integer, Money> remaining = new HashMap<>(projectedPenaltyOutstanding);
        final List<Integer> owners = remaining.entrySet().stream().filter(e -> e.getKey() != null && e.getValue() != null)
                .filter(e -> e.getValue().isLessThanZero()).map(Map.Entry::getKey).sorted().toList();

        for (final Integer owner : owners) {
            Money deficit = remaining.get(owner).negated();
            for (final LoanCharge charge : paidChargesLinkedTo(charges, owner, currency)) {
                if (!deficit.isGreaterThanZero()) {
                    break;
                }
                final Integer display = displayInstallmentNumber.apply(charge);
                if (display == null || display.equals(owner)) {
                    continue;
                }
                final Money available = remaining.get(display);
                if (available == null || !available.isGreaterThanZero()) {
                    continue;
                }
                final Money move = min(min(deficit, charge.getAmountPaid(currency)), available);
                shifts.merge(owner, move, (left, right) -> left.plus(right));
                shifts.merge(display, move.negated(), (left, right) -> left.plus(right));
                remaining.put(display, available.minus(move));
                remaining.put(owner, remaining.get(owner).plus(move));
                deficit = deficit.minus(move);
            }
        }
        shifts.values().removeIf(Money::isZero);
        return shifts;
    }

    /**
     * Applies {@link #shifts} to the schedule as it stands now and marks installments that end up fully settled.
     *
     * @return {@code true} when any installment penalty portion changed
     */
    public static boolean apply(final Loan loan, final Function<LoanCharge, Integer> displayInstallmentNumber) {
        if (loan == null || loan.getCurrency() == null || loan.getRepaymentScheduleInstallments() == null
                || !hasPaidLinkedOverduePenalty(loan.getLoanCharges(), loan.getCurrency())) {
            return false;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        final Map<Integer, LoanRepaymentScheduleInstallment> byNumber = new HashMap<>();
        final Map<Integer, Money> projected = new HashMap<>();
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (installment == null || installment.getInstallmentNumber() == null) {
                continue;
            }
            byNumber.put(installment.getInstallmentNumber(), installment);
            projected.put(installment.getInstallmentNumber(), installment.getPenaltyChargesOutstanding(currency));
        }

        final Map<Integer, Money> shifts = shifts(loan.getLoanCharges(), projected, displayInstallmentNumber, currency);
        if (shifts.isEmpty()) {
            return false;
        }
        final Money zero = Money.zero(currency);
        for (final Map.Entry<Integer, Money> shift : shifts.entrySet()) {
            final LoanRepaymentScheduleInstallment installment = byNumber.get(shift.getKey());
            installment.addToChargePortion(zero, zero, zero, zero, zero, zero, shift.getValue(), zero, zero);
            if (installment.isObligationsMet() && installment.getObligationsMetOnDate() == null) {
                installment.checkIfRepaymentPeriodObligationsAreMet(lastSettlementDate(loan, installment), currency);
            }
        }
        return true;
    }

    private static List<LoanCharge> paidChargesLinkedTo(final Collection<LoanCharge> charges, final Integer owner,
            final MonetaryCurrency currency) {
        final List<LoanCharge> linked = new ArrayList<>();
        for (final LoanCharge charge : charges) {
            if (isPaidLinkedOverduePenalty(charge, currency) && owner.equals(linkedInstallmentNumber(charge))) {
                linked.add(charge);
            }
        }
        linked.sort(Comparator.comparing(LoanCharge::getDueLocalDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return linked;
    }

    private static boolean isPaidLinkedOverduePenalty(final LoanCharge charge, final MonetaryCurrency currency) {
        if (charge == null || !charge.isActive() || !charge.isOverdueInstallmentCharge() || !charge.isPenaltyCharge()
                || linkedInstallmentNumber(charge) == null) {
            return false;
        }
        final Money paid = charge.getAmountPaid(currency);
        return paid != null && paid.isGreaterThanZero();
    }

    private static Integer linkedInstallmentNumber(final LoanCharge charge) {
        if (charge.getOverdueInstallmentCharge() == null || charge.getOverdueInstallmentCharge().getInstallment() == null) {
            return null;
        }
        return charge.getOverdueInstallmentCharge().getInstallment().getInstallmentNumber();
    }

    private static LocalDate lastSettlementDate(final Loan loan, final LoanRepaymentScheduleInstallment installment) {
        LocalDate latest = null;
        if (loan.getLoanTransactions() == null) {
            return null;
        }
        for (final LoanTransaction transaction : loan.getLoanTransactions()) {
            if (transaction == null || transaction.isReversed() || transaction.getLoanTransactionToRepaymentScheduleMappings() == null) {
                continue;
            }
            for (final LoanTransactionToRepaymentScheduleMapping mapping : transaction.getLoanTransactionToRepaymentScheduleMappings()) {
                if (mapping != null && isSame(mapping.getLoanRepaymentScheduleInstallment(), installment)
                        && (latest == null || transaction.getTransactionDate().isAfter(latest))) {
                    latest = transaction.getTransactionDate();
                }
            }
        }
        return latest;
    }

    private static boolean isSame(final LoanRepaymentScheduleInstallment left, final LoanRepaymentScheduleInstallment right) {
        if (left == null || right == null) {
            return false;
        }
        if (left == right) {
            return true;
        }
        return left.getId() != null && left.getId().equals(right.getId());
    }

    private static Money min(final Money left, final Money right) {
        return left.isGreaterThan(right) ? right : left;
    }
}
