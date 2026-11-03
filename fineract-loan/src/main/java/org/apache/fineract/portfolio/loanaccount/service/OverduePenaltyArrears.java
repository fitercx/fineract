package org.apache.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;

/**
 * Overdue penalty for the General tab.
 * <p>
 * The schedule stores only the due-date day's penalty on an installment and keeps every later day on the next period.
 * Once an installment is cleared, penalty paid on that row stays above penalty charged, so the row outstanding is
 * negative. Adding that negative into the loan overdue total makes a later installment's payment look like the negative
 * grew. Unpaid overdue charges dated before the business date are the amount still due.
 */
public final class OverduePenaltyArrears {

    private OverduePenaltyArrears() {}

    /**
     * @return unpaid overdue-installment charges dated strictly before {@code businessDate}, or null when this loan has
     *         no such charges and the caller should use the schedule figure (never below zero)
     */
    public static BigDecimal unpaidBefore(final Loan loan, final LocalDate businessDate) {
        if (loan == null || businessDate == null || loan.getCurrency() == null || loan.getCharges() == null) {
            return null;
        }
        boolean sawOverdueCharge = false;
        BigDecimal total = BigDecimal.ZERO;
        for (final LoanCharge charge : loan.getCharges()) {
            if (charge == null || !charge.isOverdueInstallmentCharge() || !charge.isActive() || charge.isWaived()) {
                continue;
            }
            sawOverdueCharge = true;
            final LocalDate dueDate = charge.getDueLocalDate();
            if (dueDate == null || !dueDate.isBefore(businessDate)) {
                continue;
            }
            final Money outstanding = charge.getAmountOutstanding(loan.getCurrency());
            if (outstanding != null && outstanding.isGreaterThanZero()) {
                total = total.add(outstanding.getAmount());
            }
        }
        return sawOverdueCharge ? total : null;
    }
}
