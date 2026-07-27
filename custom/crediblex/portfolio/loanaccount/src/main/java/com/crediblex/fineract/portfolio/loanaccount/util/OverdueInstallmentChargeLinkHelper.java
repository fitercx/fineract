package com.crediblex.fineract.portfolio.loanaccount.util;

import java.math.BigDecimal;
import java.util.Set;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * CredX helpers for keeping active overdue/LPI charges linked to the current repayment schedule after schedule
 * regenerate/reschedule. Core {@code Loan.updateOverdueScheduleInstallment} can orphan join rows when the target
 * installment is missing; these helpers skip null remaps so the apply-penalty / LPI job does not NPE.
 */
public final class OverdueInstallmentChargeLinkHelper {

    private OverdueInstallmentChargeLinkHelper() {}

    public static void remapActiveOverdueInstallmentCharges(final Loan loan) {
        if (loan == null) {
            return;
        }
        final Set<LoanCharge> charges = loan.getActiveCharges();
        if (charges == null || charges.isEmpty()) {
            return;
        }
        for (final LoanCharge loanCharge : charges) {
            safeUpdateOverdueScheduleInstallment(loan, loanCharge);
        }
    }

    public static void remapAllActiveOverdueInstallmentCharges(final Loan loan) {
        if (loan == null || loan.getLoanCharges() == null) {
            return;
        }
        for (final LoanCharge loanCharge : loan.getLoanCharges()) {
            if (loanCharge != null && loanCharge.isOverdueInstallmentCharge() && loanCharge.isActive()) {
                safeUpdateOverdueScheduleInstallment(loan, loanCharge);
            }
        }
    }

    public static void safeUpdateOverdueScheduleInstallment(final Loan loan, final LoanCharge loanCharge) {
        if (loan == null || loanCharge == null || !loanCharge.isOverdueInstallmentCharge() || !loanCharge.isActive()) {
            return;
        }
        final LoanOverdueInstallmentCharge overdueInstallmentCharge = loanCharge.getOverdueInstallmentCharge();
        if (overdueInstallmentCharge == null || overdueInstallmentCharge.getInstallment() == null
                || overdueInstallmentCharge.getInstallment().getInstallmentNumber() == null) {
            return;
        }
        final Integer installmentNumber = overdueInstallmentCharge.getInstallment().getInstallmentNumber();
        final LoanRepaymentScheduleInstallment installment = loan.fetchRepaymentScheduleInstallment(installmentNumber);
        // Never remap to null: orphanRemoval on LoanCharge.overdueInstallmentCharge can drop the join row.
        if (installment != null) {
            overdueInstallmentCharge.updateLoanRepaymentScheduleInstallment(installment);
        }
    }

    public static BigDecimal calculateOverdueAmountPercentageAppliedToSafely(final Loan loan, final LoanCharge loanCharge,
            final int penaltyWaitPeriod) {
        final LoanOverdueInstallmentCharge overdueInstallmentCharge = loanCharge.getOverdueInstallmentCharge();
        if (overdueInstallmentCharge == null || overdueInstallmentCharge.getInstallment() == null) {
            return loanCharge.getAmountPercentageAppliedTo() != null ? loanCharge.getAmountPercentageAppliedTo() : BigDecimal.ZERO;
        }
        return loan.calculateOverdueAmountPercentageAppliedTo(loanCharge, penaltyWaitPeriod);
    }
}
