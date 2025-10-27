package com.crediblex.fineract.portfolio.loanaccount.service.schedule;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelPeriod;
import org.apache.fineract.portfolio.loanaccount.service.schedule.LoanScheduleComponent;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CustomLoanScheduleComponent extends LoanScheduleComponent {

    @Override
    public void updateLoanSchedule(Loan loan, final LoanScheduleModel modifiedLoanSchedule) {
        final List<LoanScheduleModelPeriod> periods = modifiedLoanSchedule.getPeriods();
        for (final LoanScheduleModelPeriod scheduledLoanInstallment : modifiedLoanSchedule.getPeriods()) {
            if (scheduledLoanInstallment.isRepaymentPeriod() || scheduledLoanInstallment.isDownPaymentPeriod()) {
                LoanRepaymentScheduleInstallment existingInstallment = findByInstallmentNumber(loan.getRepaymentScheduleInstallments(),
                        scheduledLoanInstallment.periodNumber());
                if (existingInstallment == null) {
                    final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan,
                            scheduledLoanInstallment.periodNumber(), scheduledLoanInstallment.periodFromDate(),
                            scheduledLoanInstallment.periodDueDate(), scheduledLoanInstallment.principalDue(),
                            loan.isReceivableLocLoan() ? BigDecimal.ZERO : scheduledLoanInstallment.interestDue(),
                            scheduledLoanInstallment.feeChargesDue(), scheduledLoanInstallment.penaltyChargesDue(),
                            scheduledLoanInstallment.isRecalculatedInterestComponent(),
                            scheduledLoanInstallment.getLoanCompoundingDetails(), scheduledLoanInstallment.rescheduleInterestPortion(),
                            scheduledLoanInstallment.isDownPaymentPeriod());
                    loan.addLoanRepaymentScheduleInstallment(installment);
                } else {
                    existingInstallment.copyFrom(scheduledLoanInstallment);
                }
            }
        }
        // Review Installments removed
        loan.getRepaymentScheduleInstallments().removeIf(i -> !existInstallment(periods, i.getInstallmentNumber()));

        loan.updateLoanScheduleDependentDerivedFields();
        loan.updateLoanSummaryDerivedFields();
    }
}
