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

        Money scheduleOutstanding = Money.zero(currency);
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            scheduleOutstanding = scheduleOutstanding.plus(installment.getPenaltyChargesOutstanding(currency));
        }
        final Money gap = chargeOutstanding.minus(scheduleOutstanding);
        if (!gap.isGreaterThanZero()) {
            return false;
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
}
