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
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.FineractStyleLoanRepaymentScheduleTransactionProcessor;
import org.springframework.core.annotation.Order;

/**
 * CredX processor for mifos-standard-strategy that uses {@link CredXLoanRepaymentScheduleProcessingWrapper} so LPI is
 * assigned to the same month's row. Only loans disbursed on or after {@code custom.lpi-same-month.enabled-from-date}
 * get this behavior; existing loans keep next-month LPI.
 *
 * <p>
 * A ThreadLocal captures the {@code disbursementDate} forwarded by
 * {@link #reprocessLoanTransactions(LocalDate, List, MonetaryCurrency, List, Set)} so that
 * {@link #createLoanRepaymentScheduleProcessingWrapper(List)} can use it even when
 * {@code installments.get(0).getLoan()} is null (e.g. lazy-loading not triggered in the repayment reprocess path).
 * Without this, the base-class fallback would silently return the DEFAULT date-based wrapper, redistributing LPI
 * charges away from their linked installments and causing "same-month" behaviour to break on every user repayment.
 * </p>
 */
@Slf4j
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class CredXFineractStyleLoanRepaymentScheduleTransactionProcessor extends FineractStyleLoanRepaymentScheduleTransactionProcessor {

    private final LpiSameMonthProperties lpiSameMonthProperties;

    /** Captures disbursementDate for the duration of a reprocessLoanTransactions call on the current thread. */
    private final ThreadLocal<LocalDate> disbursementDateContext = new ThreadLocal<>();

    public CredXFineractStyleLoanRepaymentScheduleTransactionProcessor(ExternalIdFactory externalIdFactory,
            LpiSameMonthProperties lpiSameMonthProperties) {
        super(externalIdFactory);
        this.lpiSameMonthProperties = lpiSameMonthProperties;
    }

    /**
     * Stores the disbursementDate in a ThreadLocal before delegating to the base implementation, then clears it. This
     * ensures {@link #createLoanRepaymentScheduleProcessingWrapper} always has access to the correct date even when the
     * installment's back-reference to {@link Loan} is null.
     */
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

    /**
     * Returns the CredX wrapper (LPI on linked installment's row) when the loan qualifies, otherwise the default
     * date-based wrapper.
     *
     * <p>
     * Disbursement date resolution order:
     * <ol>
     * <li>ThreadLocal set by {@link #reprocessLoanTransactions} — always present when called from a full
     * reprocess.</li>
     * <li>Fallback via {@code installments.get(0).getLoan().getDisbursementDate()} — present when called from other
     * paths where the installment back-reference IS loaded.</li>
     * </ol>
     * </p>
     */
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
