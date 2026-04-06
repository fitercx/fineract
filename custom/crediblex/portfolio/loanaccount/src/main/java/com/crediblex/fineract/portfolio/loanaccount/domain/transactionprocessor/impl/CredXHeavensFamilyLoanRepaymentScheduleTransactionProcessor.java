package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl;

import com.crediblex.fineract.portfolio.loanaccount.configuration.LpiSameMonthProperties;
import com.crediblex.fineract.portfolio.loanaccount.domain.CredXLoanRepaymentScheduleProcessingWrapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleProcessingWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.HeavensFamilyLoanRepaymentScheduleTransactionProcessor;
import org.springframework.core.annotation.Order;

@Slf4j
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class CredXHeavensFamilyLoanRepaymentScheduleTransactionProcessor extends HeavensFamilyLoanRepaymentScheduleTransactionProcessor {

    private final LpiSameMonthProperties lpiSameMonthProperties;
    private final ThreadLocal<LocalDate> disbursementDateContext = new ThreadLocal<>();

    public CredXHeavensFamilyLoanRepaymentScheduleTransactionProcessor(ExternalIdFactory externalIdFactory,
            LpiSameMonthProperties lpiSameMonthProperties) {
        super(externalIdFactory);
        this.lpiSameMonthProperties = lpiSameMonthProperties;
    }

    @Override
    public ChangedTransactionDetail reprocessLoanTransactions(final LocalDate disbursementDate,
            final List<LoanTransaction> transactionsPostDisbursement, final MonetaryCurrency currency,
            final List<LoanRepaymentScheduleInstallment> installments, final Set<LoanCharge> charges) {
        disbursementDateContext.set(disbursementDate);
        try {
            return super.reprocessLoanTransactions(disbursementDate, transactionsPostDisbursement, currency, installments, charges);
        } finally {
            disbursementDateContext.remove();
        }
    }

    @Override
    protected LoanRepaymentScheduleProcessingWrapper createLoanRepaymentScheduleProcessingWrapper(
            List<LoanRepaymentScheduleInstallment> installments) {
        LocalDate disbDate = disbursementDateContext.get();

        if (disbDate == null && !installments.isEmpty()) {
            Loan loan = installments.get(0).getLoan();
            if (loan != null) {
                disbDate = loan.getDisbursementDate();
            }
        }

        if (disbDate != null && lpiSameMonthProperties != null && lpiSameMonthProperties.isEnabledForDisbursementDate(disbDate)) {
            log.debug("LPI same-month: using CredX wrapper for disbursementDate={}", disbDate);
            return new CredXLoanRepaymentScheduleProcessingWrapper();
        }

        log.debug("LPI same-month: using DEFAULT wrapper (disbDate={}, propertiesPresent={})", disbDate, lpiSameMonthProperties != null);
        return new LoanRepaymentScheduleProcessingWrapper();
    }
}
