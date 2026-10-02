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
     * Daily LPI dated strictly before {@code transactionDate} can be stored on an installment that is not due yet.
     * Moves that still-outstanding amount onto the latest contractual EMI due on or before the value date, so the
     * repayment takes it before that EMI's interest. Later days stay where they are. Foreclosure does not call this.
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

        final java.util.Map<Integer, Money> preDatePenaltyByInstallment = new java.util.HashMap<>();
        if (loan.getLoanCharges() != null) {
            for (final LoanCharge charge : loan.getLoanCharges()) {
                if (charge == null || !charge.isActive() || !charge.isOverdueInstallmentCharge() || charge.isWaived()) {
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
                final Integer installmentNumber = OverdueChargeScheduleAllocationUtils.resolveInstallmentNumber(chargeDate, installments);
                if (installmentNumber == null || installmentNumber.equals(target.getInstallmentNumber())) {
                    continue;
                }
                preDatePenaltyByInstallment.merge(installmentNumber, outstanding, (left, right) -> left.plus(right));
            }
        }
        if (preDatePenaltyByInstallment.isEmpty()) {
            return false;
        }

        final Money zero = Money.zero(currency);
        boolean moved = false;
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment == null || installment == target || installment.getDueDate() == null
                    || !installment.getDueDate().isAfter(transactionDate) || installment.getInstallmentNumber() == null) {
                continue;
            }
            final Money fromCharges = preDatePenaltyByInstallment.get(installment.getInstallmentNumber());
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
            moved = true;
        }
        return moved;
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
