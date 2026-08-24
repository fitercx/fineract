package com.crediblex.fineract.portfolio.loanaccount.util;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * Resolves where an overdue/LPI charge is displayed in the repayment schedule.
 * <p>
 * The overdue-installment relation records which EMI caused the LPI. It is audit metadata used by LPI regeneration and
 * reversal idempotency; it is not the schedule display bucket for every later daily occurrence. Raw Fineract allocates
 * charge amounts by effective-date windows: a charge effective exactly on an EMI due date stays on that EMI, while a
 * later occurrence is shown in the next period whose due date includes it. Keeping this distinction is required so LPI
 * application and reprocessing do not distort repayment, paid-LPI reversal, or foreclosure workflows.
 */
public final class OverdueChargeScheduleAllocationUtils {

    private OverdueChargeScheduleAllocationUtils() {}

    /**
     * Resolves an effective charge date against installments ordered by installment number/due date.
     *
     * @return the matching installment number, the last installment for a post-maturity charge, or {@code null} when
     *         the date/schedule cannot be resolved
     */
    public static Integer resolveInstallmentNumber(final LocalDate chargeEffectiveDate,
            final List<LoanRepaymentScheduleInstallment> sortedInstallments) {
        if (chargeEffectiveDate == null || sortedInstallments == null || sortedInstallments.isEmpty()) {
            return null;
        }

        LocalDate previousDueDate = null;
        LoanRepaymentScheduleInstallment lastValid = null;
        for (final LoanRepaymentScheduleInstallment current : sortedInstallments) {
            if (current == null || current.getInstallmentNumber() == null || current.getDueDate() == null) {
                continue;
            }

            final LocalDate currentDueDate = current.getDueDate();
            final LocalDate lowerBound = current.getFromDate() != null ? current.getFromDate() : previousDueDate;
            final boolean afterLowerBound = lowerBound == null || chargeEffectiveDate.isAfter(lowerBound);
            if (afterLowerBound && !chargeEffectiveDate.isAfter(currentDueDate)) {
                return current.getInstallmentNumber();
            }

            previousDueDate = currentDueDate;
            lastValid = current;
        }

        return lastValid != null && chargeEffectiveDate.isAfter(lastValid.getDueDate()) ? lastValid.getInstallmentNumber() : null;
    }
}
