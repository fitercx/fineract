package com.crediblex.fineract.portfolio.loanaccount.util;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * Keeps installment {@code penaltyChargesOutstanding} in line with unpaid overdue/LPI {@link LoanCharge} rows so a
 * repayment allocates that LPI instead of booking it as an overpayment.
 * <p>
 * CredX skips full transaction reprocess after the daily LPI job, and core used to add a post-maturity installment with
 * a dummy {@code 1.00} penalty (then replace it, dropping accumulated amounts). The charge rows remain; the schedule
 * does not. This helper copies the gap onto the overdue EMI (due before the charge), not the dummy
 * {@code GRACE_PERIOD_APPLIED} row, so the schedule Overdue Interest column and arrears include real LPI.
 */
public final class InstallmentPenaltySyncUtils {

    private InstallmentPenaltySyncUtils() {}

    /**
     * @return {@code true} when installment penalty charged was increased (caller should flush)
     */
    public static boolean syncOutstandingOverduePenaltyOntoSchedule(final Loan loan) {
        if (loan == null || loan.getLoanCharges() == null || loan.getLoanCharges().isEmpty()) {
            return false;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        Money chargeOutstanding = Money.zero(currency);
        LocalDate latestUnpaidChargeDate = null;
        for (final LoanCharge charge : loan.getLoanCharges()) {
            if (charge == null || !charge.isActive() || !charge.isOverdueInstallmentCharge() || charge.isWaived()) {
                continue;
            }
            final Money outstanding = charge.getAmountOutstanding(currency);
            if (!outstanding.isGreaterThanZero()) {
                continue;
            }
            chargeOutstanding = chargeOutstanding.plus(outstanding);
            final LocalDate due = charge.getDueLocalDate();
            if (due != null && (latestUnpaidChargeDate == null || due.isAfter(latestUnpaidChargeDate))) {
                latestUnpaidChargeDate = due;
            }
        }
        if (!chargeOutstanding.isGreaterThanZero()) {
            return false;
        }

        // Single EMI, or a payment against the last EMI: LPI posted after that due date lives on a later
        // schedule row. The strategy pays penalty, then interest, then principal per EMI, so it never
        // reaches that row while the EMI still has interest or principal. Move the unpaid amount onto
        // the EMI so this repayment collects it. Totals stay the same; only the row changes.
        final boolean folded = foldSpillPenaltyOntoLastEmi(loan, currency);

        Money scheduleOutstanding = Money.zero(currency);
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            scheduleOutstanding = scheduleOutstanding.plus(installment.getPenaltyChargesOutstanding(currency));
        }
        final Money gap = chargeOutstanding.minus(scheduleOutstanding);
        if (!gap.isGreaterThanZero()) {
            return folded;
        }

        final LoanRepaymentScheduleInstallment target = resolvePenaltyInstallment(loan, latestUnpaidChargeDate);
        if (target == null) {
            return false;
        }
        final Money zero = Money.zero(currency);
        target.addToChargePortion(zero, zero, zero, zero, zero, zero, gap, zero, zero);
        return true;
    }

    /**
     * Sets schedule penalty outstanding to unpaid overdue/LPI charges dated strictly before {@code settlementDate}.
     * <p>
     * A partial backdated repayment must collect those earlier days and must not pay (or waive) LPI dated on or after
     * the value date. Charges on/after that date stay active; this only parks their amount off the payable schedule for
     * the repayment that follows. {@link #syncOutstandingOverduePenaltyOntoSchedule} puts them back afterwards.
     *
     * @return {@code true} when an installment penalty portion changed
     */
    public static boolean alignSchedulePenaltyToChargesBefore(final Loan loan, final LocalDate settlementDate) {
        if (loan == null || settlementDate == null || loan.getLoanCharges() == null || loan.getCurrency() == null) {
            return false;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        Money payable = Money.zero(currency);
        LocalDate latestPayableChargeDate = null;
        for (final LoanCharge charge : loan.getLoanCharges()) {
            if (!isUnpaidOverdueCharge(charge)) {
                continue;
            }
            final LocalDate due = charge.getDueLocalDate();
            if (due != null && !due.isBefore(settlementDate)) {
                continue;
            }
            final Money outstanding = charge.getAmountOutstanding(currency);
            if (outstanding == null || !outstanding.isGreaterThanZero()) {
                continue;
            }
            payable = payable.plus(outstanding);
            if (due != null && (latestPayableChargeDate == null || due.isAfter(latestPayableChargeDate))) {
                latestPayableChargeDate = due;
            }
        }

        Money scheduleOutstanding = Money.zero(currency);
        if (loan.getRepaymentScheduleInstallments() != null) {
            for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
                if (installment == null) {
                    continue;
                }
                scheduleOutstanding = scheduleOutstanding.plus(installment.getPenaltyChargesOutstanding(currency));
            }
        }
        final Money delta = payable.minus(scheduleOutstanding);
        if (delta.isZero()) {
            return false;
        }
        final Money zero = Money.zero(currency);
        if (delta.isGreaterThanZero()) {
            final LoanRepaymentScheduleInstallment target = resolvePenaltyInstallment(loan,
                    latestPayableChargeDate != null ? latestPayableChargeDate : settlementDate);
            if (target == null) {
                return false;
            }
            target.addToChargePortion(zero, zero, zero, zero, zero, zero, delta, zero, zero);
            return true;
        }

        Money toRemove = delta.negated();
        final List<LoanRepaymentScheduleInstallment> holders = penaltyHoldersDescending(loan, currency);
        boolean changed = false;
        for (final LoanRepaymentScheduleInstallment holder : holders) {
            if (!toRemove.isGreaterThanZero()) {
                break;
            }
            final Money outstanding = holder.getPenaltyChargesOutstanding(currency);
            if (outstanding == null || !outstanding.isGreaterThanZero()) {
                continue;
            }
            final Money slice = outstanding.isGreaterThan(toRemove) ? toRemove : outstanding;
            holder.addToChargePortion(zero, zero, zero, zero, zero, zero, slice.negated(), zero, zero);
            toRemove = toRemove.minus(slice);
            changed = true;
        }
        return changed;
    }

    private static boolean isUnpaidOverdueCharge(final LoanCharge charge) {
        return charge != null && charge.isActive() && charge.isOverdueInstallmentCharge() && !charge.isWaived();
    }

    private static List<LoanRepaymentScheduleInstallment> penaltyHoldersDescending(final Loan loan, final MonetaryCurrency currency) {
        final List<LoanRepaymentScheduleInstallment> holders = new java.util.ArrayList<>();
        if (loan.getRepaymentScheduleInstallments() == null) {
            return holders;
        }
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (installment == null || installment.isDownPayment()) {
                continue;
            }
            final Money outstanding = installment.getPenaltyChargesOutstanding(currency);
            if (outstanding != null && outstanding.isGreaterThanZero()) {
                holders.add(installment);
            }
        }
        holders.sort((left, right) -> right.getPenaltyChargesOutstanding(currency).getAmount()
                .compareTo(left.getPenaltyChargesOutstanding(currency).getAmount()));
        return holders;
    }

    /**
     * Puts unmapped LPI on the overdue EMI it belongs to (due date strictly before the charge), not on the dummy
     * post-maturity {@code GRACE_PERIOD_APPLIED} row. That dummy used to hold a placeholder {@code 1.00} and is not
     * included in arrears when its due date is today.
     */
    static LoanRepaymentScheduleInstallment resolvePenaltyInstallment(final Loan loan, final LocalDate latestUnpaidChargeDate) {
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        if (installments == null || installments.isEmpty()) {
            return null;
        }
        LoanRepaymentScheduleInstallment lastNormal = null;
        LoanRepaymentScheduleInstallment owningOverdueEmi = null;
        LoanRepaymentScheduleInstallment lastAny = null;
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment == null || installment.isDownPayment()) {
                continue;
            }
            lastAny = installment;
            final boolean dummy = installment.isAdditional() || installment.isRecalculatedInterestComponent();
            if (dummy) {
                continue;
            }
            lastNormal = installment;
            if (latestUnpaidChargeDate != null && installment.getDueDate() != null
                    && latestUnpaidChargeDate.isAfter(installment.getDueDate())) {
                owningOverdueEmi = installment;
            }
        }
        return owningOverdueEmi != null ? owningOverdueEmi : (lastNormal != null ? lastNormal : lastAny);
    }

    /**
     * Moves penalty outstanding that sits on a non-EMI row (no principal and no interest) onto the last contractual
     * EMI, when that EMI is the only one or every earlier EMI is already paid.
     *
     * @return {@code true} when an amount was moved
     */
    public static boolean foldSpillPenaltyOntoLastEmi(final Loan loan, final MonetaryCurrency currency) {
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        if (installments == null || installments.isEmpty() || currency == null) {
            return false;
        }
        final List<LoanRepaymentScheduleInstallment> contractual = new java.util.ArrayList<>();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (isContractualEmi(installment, currency)) {
                contractual.add(installment);
            }
        }
        if (contractual.isEmpty()) {
            return false;
        }
        final LoanRepaymentScheduleInstallment lastEmi = contractual.get(contractual.size() - 1);
        for (final LoanRepaymentScheduleInstallment installment : contractual) {
            if (installment != lastEmi && !installment.isObligationsMet()) {
                return false;
            }
        }

        final Money zero = Money.zero(currency);
        boolean moved = false;
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment == null || installment == lastEmi || installment.isDownPayment() || isContractualEmi(installment, currency)) {
                continue;
            }
            final Money outstanding = installment.getPenaltyChargesOutstanding(currency);
            if (outstanding == null || !outstanding.isGreaterThanZero()) {
                continue;
            }
            lastEmi.addToChargePortion(zero, zero, zero, zero, zero, zero, outstanding, zero, zero);
            installment.addToChargePortion(zero, zero, zero, zero, zero, zero, outstanding.negated(), zero, zero);
            moved = true;
        }
        return moved;
    }

    /**
     * Stops a later repayment from posting negative LPI.
     * <p>
     * After a partial, the schedule row keeps only the due-date day while {@code penaltyChargesPaid} still includes the
     * later days that repayment collected. Outstanding is then negative, and the next repayment books that deficit as
     * negative overdue interest and the same amount of extra principal. Charged penalty is raised to the amount already
     * paid, and that increase is taken off later rows so the loan total stays the same.
     *
     * @return {@code true} when an installment penalty portion changed
     */
    public static boolean coverPenaltyPaidAboveCharged(final Loan loan) {
        if (loan == null || loan.getCurrency() == null || loan.getRepaymentScheduleInstallments() == null) {
            return false;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        final Money zero = Money.zero(currency);
        final List<LoanRepaymentScheduleInstallment> installments = new java.util.ArrayList<>();
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (installment != null && installment.getDueDate() != null) {
                installments.add(installment);
            }
        }
        installments.sort((left, right) -> left.getDueDate().compareTo(right.getDueDate()));

        boolean changed = false;
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            final Money outstanding = installment.getPenaltyChargesOutstanding(currency);
            if (outstanding == null || !outstanding.isLessThanZero()) {
                continue;
            }
            Money shortfall = outstanding.negated();
            installment.addToChargePortion(zero, zero, zero, zero, zero, zero, shortfall, zero, zero);
            changed = true;
            final List<LoanRepaymentScheduleInstallment> laterFirst = new java.util.ArrayList<>();
            for (final LoanRepaymentScheduleInstallment other : installments) {
                if (other != installment && other.getDueDate().isAfter(installment.getDueDate())) {
                    laterFirst.add(other);
                }
            }
            laterFirst.sort((left, right) -> right.getDueDate().compareTo(left.getDueDate()));
            for (final LoanRepaymentScheduleInstallment holder : laterFirst) {
                if (!shortfall.isGreaterThanZero()) {
                    break;
                }
                final Money holderOutstanding = holder.getPenaltyChargesOutstanding(currency);
                if (holderOutstanding == null || !holderOutstanding.isGreaterThanZero()) {
                    continue;
                }
                final Money slice = holderOutstanding.isGreaterThan(shortfall) ? shortfall : holderOutstanding;
                holder.addToChargePortion(zero, zero, zero, zero, zero, zero, slice.negated(), zero, zero);
                shortfall = shortfall.minus(slice);
            }
        }
        return changed;
    }

    /**
     * Puts unpaid LPI dated strictly before {@code transactionDate} on the installment the repayment strategy will pay,
     * immediately before allocation. Later days stay where they are. Foreclosure does not call this.
     * <p>
     * The schedule shows each daily charge in the period that contains its effective date, so only the first day stays
     * on the EMI that caused it. With one overdue EMI, the later days sit on a row that is not due yet and are folded
     * onto that EMI. With several overdue EMIs those later days sit on the next overdue EMI, which is already due, so
     * the oldest EMI keeps a single day. The strategy then pays that day, then the EMI's interest and principal, and
     * never reaches the rest of its LPI. Charges linked to an EMI due on or before the value date are moved back onto
     * that EMI. Anything without a link still folds onto the latest EMI due on or before the value date.
     * <p>
     * A previous partial can leave an EMI's penalty outstanding negative: the schedule keeps only the due-date day,
     * while the payment already collected later days against it. Adding the pre-date LPI on top of that deficit would
     * drop the same number of days off the end (LMS-150, loan 000014570: 21–22 Sep left unpaid). The deficit is added
     * back so each EMI's payable penalty equals its own unpaid LPI days strictly before the value date.
     *
     * @return {@code true} when an amount was moved
     */
    public static boolean foldPreValueDatePenaltyOntoDueEmi(final Loan loan, final LocalDate transactionDate) {
        if (loan == null || transactionDate == null || loan.getCurrency() == null || loan.getRepaymentScheduleInstallments() == null) {
            return false;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        LoanRepaymentScheduleInstallment target = null;
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (!isContractualEmi(installment, currency) || installment.getDueDate() == null
                    || installment.getDueDate().isAfter(transactionDate)) {
                continue;
            }
            if (target == null || installment.getDueDate().isAfter(target.getDueDate())) {
                target = installment;
            }
        }
        if (target == null || target.getInstallmentNumber() == null) {
            return false;
        }

        final Money zero = Money.zero(currency);
        Money unpaidUnlinked = zero;
        final java.util.Map<Integer, Money> unlinkedByInstallment = new java.util.HashMap<>();
        final java.util.Map<Integer, Money> ownedByInstallment = new java.util.HashMap<>();
        final List<PenaltyShift> ownerShifts = new java.util.ArrayList<>();
        if (loan.getLoanCharges() != null) {
            for (final LoanCharge charge : loan.getLoanCharges()) {
                if (!isUnpaidOverdueCharge(charge)) {
                    continue;
                }
                final LocalDate chargeDate = charge.getDueLocalDate();
                if (chargeDate == null || !chargeDate.isBefore(transactionDate)) {
                    continue;
                }
                final Money outstanding = charge.getAmountOutstanding(currency);
                if (outstanding == null || !outstanding.isGreaterThanZero()) {
                    continue;
                }
                final LoanRepaymentScheduleInstallment owner = owningInstallmentDueOnOrBefore(charge, installments, transactionDate,
                        currency);
                final Integer displayNumber = OverdueChargeScheduleAllocationUtils.resolveInstallmentNumber(chargeDate, installments);
                if (owner != null && owner.getInstallmentNumber() != null) {
                    ownedByInstallment.merge(owner.getInstallmentNumber(), outstanding, (left, right) -> left.plus(right));
                    if (displayNumber != null && !displayNumber.equals(owner.getInstallmentNumber())) {
                        ownerShifts.add(new PenaltyShift(displayNumber, owner.getInstallmentNumber(), outstanding));
                    }
                    continue;
                }
                unpaidUnlinked = unpaidUnlinked.plus(outstanding);
                if (displayNumber == null || displayNumber.equals(target.getInstallmentNumber())) {
                    continue;
                }
                unlinkedByInstallment.merge(displayNumber, outstanding, (left, right) -> left.plus(right));
            }
        }

        boolean moved = false;
        for (final PenaltyShift shift : ownerShifts) {
            moved |= movePenalty(installmentByNumber(installments, shift.fromNumber), installmentByNumber(installments, shift.toNumber),
                    shift.amount, zero, currency);
        }
        for (final java.util.Map.Entry<Integer, Money> owned : ownedByInstallment.entrySet()) {
            final LoanRepaymentScheduleInstallment owner = installmentByNumber(installments, owned.getKey());
            if (owner == null || owned.getValue() == null) {
                continue;
            }
            final Money projected = owner.getPenaltyChargesOutstanding(currency) == null ? zero
                    : owner.getPenaltyChargesOutstanding(currency);
            if (!owned.getValue().isGreaterThan(projected)) {
                continue;
            }
            Money extra = owned.getValue().minus(projected);
            owner.addToChargePortion(zero, zero, zero, zero, zero, zero, extra, zero, zero);
            extra = peelSurplusAboveOwned(installments, owner, extra, ownedByInstallment, zero, currency);
            moved = true;
        }

        final Money targetOutstandingAtStart = target.getPenaltyChargesOutstanding(currency) == null ? zero
                : target.getPenaltyChargesOutstanding(currency);
        Money movedTotal = zero;
        final List<LoanRepaymentScheduleInstallment> sources = new java.util.ArrayList<>();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment == null || installment == target || installment.getDueDate() == null
                    || !installment.getDueDate().isAfter(transactionDate) || installment.getInstallmentNumber() == null) {
                continue;
            }
            final Money fromCharges = unlinkedByInstallment.get(installment.getInstallmentNumber());
            if (fromCharges == null || !fromCharges.isGreaterThanZero()) {
                continue;
            }
            final Money onSchedule = installment.getPenaltyChargesOutstanding(currency);
            if (onSchedule == null || !onSchedule.isGreaterThanZero()) {
                continue;
            }
            final Money amount = onSchedule.isGreaterThan(fromCharges) ? fromCharges : onSchedule;
            target.addToChargePortion(zero, zero, zero, zero, zero, zero, amount, zero, zero);
            installment.addToChargePortion(zero, zero, zero, zero, zero, zero, amount.negated(), zero, zero);
            movedTotal = movedTotal.plus(amount);
            sources.add(installment);
            moved = true;
        }

        // Payable unlinked LPI must land on the latest due EMI. A negative starting balance would otherwise cancel the
        // tail of that amount (already-paid days the schedule no longer charges on this EMI).
        final Money projected = targetOutstandingAtStart.plus(movedTotal);
        if (unpaidUnlinked.isGreaterThanZero() && unpaidUnlinked.isGreaterThan(projected)) {
            Money extra = unpaidUnlinked.minus(projected);
            target.addToChargePortion(zero, zero, zero, zero, zero, zero, extra, zero, zero);
            for (final LoanRepaymentScheduleInstallment source : sources) {
                extra = peelPenaltyOutstanding(source, extra, zero, currency);
            }
            if (extra.isGreaterThanZero()) {
                for (final LoanRepaymentScheduleInstallment installment : installments) {
                    if (installment == null || installment == target || sources.contains(installment) || installment.getDueDate() == null
                            || !installment.getDueDate().isAfter(transactionDate)) {
                        continue;
                    }
                    extra = peelPenaltyOutstanding(installment, extra, zero, currency);
                    if (!extra.isGreaterThanZero()) {
                        break;
                    }
                }
            }
            moved = true;
        }
        return moved;
    }

    /**
     * The EMI a daily LPI charge was raised for, when that EMI is already due on the value date. Later days are linked
     * to this EMI even though the schedule shows them on a later period.
     */
    private static LoanRepaymentScheduleInstallment owningInstallmentDueOnOrBefore(final LoanCharge charge,
            final List<LoanRepaymentScheduleInstallment> installments, final LocalDate transactionDate, final MonetaryCurrency currency) {
        if (charge.getOverdueInstallmentCharge() == null || charge.getOverdueInstallmentCharge().getInstallment() == null
                || charge.getOverdueInstallmentCharge().getInstallment().getInstallmentNumber() == null) {
            return null;
        }
        final Integer number = charge.getOverdueInstallmentCharge().getInstallment().getInstallmentNumber();
        final LoanRepaymentScheduleInstallment owner = installmentByNumber(installments, number);
        if (owner == null || !isContractualEmi(owner, currency) || owner.getDueDate() == null
                || owner.getDueDate().isAfter(transactionDate)) {
            return null;
        }
        return owner;
    }

    private static LoanRepaymentScheduleInstallment installmentByNumber(final List<LoanRepaymentScheduleInstallment> installments,
            final Integer number) {
        if (number == null || installments == null) {
            return null;
        }
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment != null && number.equals(installment.getInstallmentNumber())) {
                return installment;
            }
        }
        return null;
    }

    private static boolean movePenalty(final LoanRepaymentScheduleInstallment from, final LoanRepaymentScheduleInstallment to,
            final Money amount, final Money zero, final MonetaryCurrency currency) {
        if (from == null || to == null || from == to || amount == null || !amount.isGreaterThanZero()) {
            return false;
        }
        final Money onSchedule = from.getPenaltyChargesOutstanding(currency);
        if (onSchedule == null || !onSchedule.isGreaterThanZero()) {
            return false;
        }
        final Money slice = onSchedule.isGreaterThan(amount) ? amount : onSchedule;
        to.addToChargePortion(zero, zero, zero, zero, zero, zero, slice, zero, zero);
        from.addToChargePortion(zero, zero, zero, zero, zero, zero, slice.negated(), zero, zero);
        return true;
    }

    /**
     * Removes {@code extra} from later rows without taking LPI that those rows own themselves.
     */
    private static Money peelSurplusAboveOwned(final List<LoanRepaymentScheduleInstallment> installments,
            final LoanRepaymentScheduleInstallment owner, Money extra, final java.util.Map<Integer, Money> ownedByInstallment,
            final Money zero, final MonetaryCurrency currency) {
        final List<LoanRepaymentScheduleInstallment> laterFirst = new java.util.ArrayList<>();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment != null && installment != owner) {
                laterFirst.add(installment);
            }
        }
        laterFirst.sort((left, right) -> {
            if (left.getDueDate() == null) {
                return 1;
            }
            if (right.getDueDate() == null) {
                return -1;
            }
            return right.getDueDate().compareTo(left.getDueDate());
        });
        for (final LoanRepaymentScheduleInstallment installment : laterFirst) {
            if (extra == null || !extra.isGreaterThanZero()) {
                break;
            }
            final Money floor = installment.getInstallmentNumber() == null ? zero
                    : ownedByInstallment.getOrDefault(installment.getInstallmentNumber(), zero);
            final Money onSchedule = installment.getPenaltyChargesOutstanding(currency);
            if (onSchedule == null || !onSchedule.isGreaterThan(floor)) {
                continue;
            }
            final Money surplus = onSchedule.minus(floor);
            final Money slice = surplus.isGreaterThan(extra) ? extra : surplus;
            installment.addToChargePortion(zero, zero, zero, zero, zero, zero, slice.negated(), zero, zero);
            extra = extra.minus(slice);
        }
        return extra == null ? zero : extra;
    }

    private static final class PenaltyShift {

        private final Integer fromNumber;
        private final Integer toNumber;
        private final Money amount;

        private PenaltyShift(final Integer fromNumber, final Integer toNumber, final Money amount) {
            this.fromNumber = fromNumber;
            this.toNumber = toNumber;
            this.amount = amount;
        }
    }

    private static Money peelPenaltyOutstanding(final LoanRepaymentScheduleInstallment installment, final Money remaining, final Money zero,
            final MonetaryCurrency currency) {
        if (installment == null || remaining == null || !remaining.isGreaterThanZero()) {
            return remaining == null ? zero : remaining;
        }
        final Money onSchedule = installment.getPenaltyChargesOutstanding(currency);
        if (onSchedule == null || !onSchedule.isGreaterThanZero()) {
            return remaining;
        }
        final Money slice = onSchedule.isGreaterThan(remaining) ? remaining : onSchedule;
        installment.addToChargePortion(zero, zero, zero, zero, zero, zero, slice.negated(), zero, zero);
        return remaining.minus(slice);
    }

    private static boolean isContractualEmi(final LoanRepaymentScheduleInstallment installment, final MonetaryCurrency currency) {
        if (installment == null || installment.isDownPayment() || installment.isAdditional()
                || installment.isRecalculatedInterestComponent()) {
            return false;
        }
        final Money principal = installment.getPrincipal(currency);
        final Money interest = installment.getInterestCharged(currency);
        return (principal != null && principal.isGreaterThanZero()) || (interest != null && interest.isGreaterThanZero());
    }
}
