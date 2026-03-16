package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl;

import com.crediblex.fineract.portfolio.loanaccount.configuration.LpiSameMonthProperties;
import com.crediblex.fineract.portfolio.loanaccount.domain.CredXLoanRepaymentScheduleProcessingWrapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleProcessingWrapper;

/**
 * CredX processor for pro-rata-mifos-standard-strategy that uses {@link CredXLoanRepaymentScheduleProcessingWrapper} so
 * LPI is assigned to the same month's row. Only loans disbursed on or after
 * {@code custom.lpi-same-month.enabled-from-date} get this behavior; existing loans keep next-month LPI.
 */
@Slf4j
public class CredXProRataFineractStyleLoanRepaymentScheduleTransactionProcessor
        extends ProRataFineractStyleLoanRepaymentScheduleTransactionProcessor {

    private final LpiSameMonthProperties lpiSameMonthProperties;

    public CredXProRataFineractStyleLoanRepaymentScheduleTransactionProcessor(ExternalIdFactory externalIdFactory,
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
                log.debug("LPI same-month: using CredX wrapper for loanId={}, disbursementDate={} (pro-rata)", loan.getId(),
                        loan.getDisbursementDate());
                return new CredXLoanRepaymentScheduleProcessingWrapper();
            }
        }
        return new LoanRepaymentScheduleProcessingWrapper();
    }
}
