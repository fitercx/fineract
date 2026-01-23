package com.crediblex.fineract.infrastructure.commands.utils;

import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * LoanStatusAggregationUtils:
 * - resolveInstallmentStatus: derives the status for a single installment (used by LoanTransactionInstallmentUtils when building affectedInstallments payloads).
 * - computeCustomLoanStatusForLoan: aggregates all installments of a loan to derive a loan-level custom status (used by repayment and disbursement flows in CustomLoanWritePlatformServiceJpaRepositoryImpl).
 * This utility is transaction-agnostic and safe to use anywhere the schedule exists (repayment, disbursement, schedule updates).
 */
public final class LoanStatusAggregationUtils {

    private LoanStatusAggregationUtils() {}

    public enum InstallmentStatus {
        DISBURSEMENT, SCHEDULED, DUE, OVERDUE, LATE_FEE_APPLIED, PAID, PARTIAL_PAID
    }

    /**
     * Resolve installment status based on payment state and dates.
     */
    public static InstallmentStatus resolveInstallmentStatus(LoanRepaymentScheduleInstallment installment, Loan loan) {
        if (installment.isObligationsMet()) {
            return InstallmentStatus.PAID;
        }
        if (installment.getPenaltyChargesOutstanding(loan.getCurrency()).isGreaterThanZero()) {
            return InstallmentStatus.LATE_FEE_APPLIED;
        }
        boolean hasOutstanding = installment.getTotalOutstanding(loan.getCurrency()).isGreaterThanZero();
        boolean hasPaidAmount = installment.getPrincipalCompleted(loan.getCurrency()).isGreaterThanZero()
                || installment.getInterestPaid(loan.getCurrency()).isGreaterThanZero()
                || installment.getFeeChargesPaid(loan.getCurrency()).isGreaterThanZero();
        LocalDate currentDate = DateUtils.getLocalDateOfTenant();
        if (installment.getDueDate().isBefore(currentDate)) {
            return InstallmentStatus.OVERDUE;
        }
        if (hasOutstanding && hasPaidAmount) {
            return InstallmentStatus.PARTIAL_PAID;
        }
        if (installment.getDueDate().equals(currentDate)) {
            return InstallmentStatus.DUE;
        }
        return InstallmentStatus.SCHEDULED;
    }

    /**
     * Compute aggregate custom loan status from ALL installments of the loan.
     * Precedence:
     * - If any installment is LATE_FEE_APPLIED => PAST_MATURITY
     * - Else if any installment is OVERDUE => PAST_DUE
     * - Else => INVALID
     */
    public static CustomLoanStatus computeCustomLoanStatusForLoan(Loan loan) {
        if (loan == null || loan.getRepaymentScheduleInstallments() == null) {
            return CustomLoanStatus.INVALID;
        }
        boolean hasOverdue = false;
        for (LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            InstallmentStatus status = resolveInstallmentStatus(installment, loan);
            if (status == InstallmentStatus.LATE_FEE_APPLIED) {
                return CustomLoanStatus.PAST_MATURITY;
            }
            if (status == InstallmentStatus.OVERDUE) {
                hasOverdue = true;
            }
        }
        return hasOverdue ? CustomLoanStatus.PAST_DUE : CustomLoanStatus.INVALID;
    }
}
