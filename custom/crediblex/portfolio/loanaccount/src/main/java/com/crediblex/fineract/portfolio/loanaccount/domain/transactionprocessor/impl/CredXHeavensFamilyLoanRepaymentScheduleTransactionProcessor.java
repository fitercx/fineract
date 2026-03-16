package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl;

import com.crediblex.fineract.portfolio.loanaccount.configuration.LpiSameMonthProperties;
import com.crediblex.fineract.portfolio.loanaccount.domain.CredXLoanRepaymentScheduleProcessingWrapper;
import java.util.List;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleProcessingWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.HeavensFamilyLoanRepaymentScheduleTransactionProcessor;
import org.springframework.core.annotation.Order;

@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class CredXHeavensFamilyLoanRepaymentScheduleTransactionProcessor extends HeavensFamilyLoanRepaymentScheduleTransactionProcessor {

    private final LpiSameMonthProperties lpiSameMonthProperties;

    public CredXHeavensFamilyLoanRepaymentScheduleTransactionProcessor(ExternalIdFactory externalIdFactory,
            LpiSameMonthProperties lpiSameMonthProperties) {
        super(externalIdFactory);
        this.lpiSameMonthProperties = lpiSameMonthProperties;
    }

    @Override
    protected LoanRepaymentScheduleProcessingWrapper createLoanRepaymentScheduleProcessingWrapper(
            List<LoanRepaymentScheduleInstallment> installments) {
        if (lpiSameMonthProperties != null && !installments.isEmpty()) {
            Loan loan = installments.get(0).getLoan();
            if (loan != null && lpiSameMonthProperties.isEnabledForDisbursementDate(loan.getDisbursementDate())) {
                return new CredXLoanRepaymentScheduleProcessingWrapper();
            }
        }
        return new LoanRepaymentScheduleProcessingWrapper();
    }
}
