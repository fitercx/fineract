package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.configuration.LpiSameMonthProperties;
import com.crediblex.fineract.portfolio.loanaccount.domain.CredXLoanRepaymentScheduleProcessingWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.interestpauses.service.LoanAccountTransfersService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.ReplayedTransactionBusinessEventService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsServiceImpl;

/**
 * CredX-aware override of {@link ReprocessLoanTransactionsServiceImpl}.
 *
 * <h3>Why this override is needed</h3>
 *
 * <p>
 * {@link ReprocessLoanTransactionsServiceImpl#removeLoanCharge} contains a hardcoded:
 * </p>
 *
 * <pre>
 * final LoanRepaymentScheduleProcessingWrapper wrapper = new LoanRepaymentScheduleProcessingWrapper(); // DEFAULT!
 * wrapper.reprocess(...)
 * </pre>
 *
 * <p>
 * This is called immediately after the charge is marked inactive. If the charge was <em>unpaid</em> at deletion time,
 * the method returns early (no {@code reprocessTransactions} is called), leaving the schedule permanently stamped with
 * DEFAULT date-based LPI assignment. Paid charges go on to call {@code reprocessTransactions} (which the ThreadLocal
 * fix in the CredX processor correctly routes through the CredX wrapper), but unpaid deletions bypass that path.
 * </p>
 *
 * <h3>Fix</h3>
 *
 * <p>
 * After delegating to the parent's {@code removeLoanCharge}, this override re-applies the CredX wrapper for any loan
 * disbursed on or after {@code custom.lpi-same-month.enabled-from-date}. This is the same belt-and-suspenders pattern
 * used in {@link CustomLoanDownPaymentHandlerService} and
 * {@link CredXLoanChargeWritePlatformServiceImpl#customWaiveLoanCharge}.
 * </p>
 */
@Slf4j
public class CredXReprocessLoanTransactionsService extends ReprocessLoanTransactionsServiceImpl {

    private final LpiSameMonthProperties lpiSameMonthProperties;

    public CredXReprocessLoanTransactionsService(LoanAccountService loanAccountService,
            LoanAccountTransfersService loanAccountTransfersService,
            ReplayedTransactionBusinessEventService replayedTransactionBusinessEventService,
            LoanTransactionProcessingService loadTransactionProcessingService, LpiSameMonthProperties lpiSameMonthProperties) {
        super(loanAccountService, loanAccountTransfersService, replayedTransactionBusinessEventService, loadTransactionProcessingService);
        this.lpiSameMonthProperties = lpiSameMonthProperties;
    }

    /**
     * Delegates to parent, then re-applies the CredX wrapper so the repayment schedule continues to show LPI on each
     * installment's own row regardless of whether the deleted charge was paid or unpaid.
     *
     * <p>
     * The parent's logic:
     * <ul>
     * <li>Runs DEFAULT wrapper immediately after marking the charge inactive (always).</li>
     * <li>Calls {@code reprocessTransactions} only when the charge was paid/partially paid — which, via the ThreadLocal
     * fix in {@code CredXFineractStyleLoanRepaymentScheduleTransactionProcessor}, already uses the CredX wrapper.</li>
     * </ul>
     * So unpaid-charge deletions previously had no CredX wrapper run at all; this call closes that gap.
     * </p>
     */
    @Override
    public void removeLoanCharge(final Loan loan, final LoanCharge loanCharge) {
        super.removeLoanCharge(loan, loanCharge);
        applyCredXWrapperIfNeeded(loan);
    }

    /**
     * Applies the CredX same-month LPI wrapper when the loan qualifies. Mirrors the pattern used throughout the CredX
     * charge-writing services.
     */
    private void applyCredXWrapperIfNeeded(final Loan loan) {
        if (lpiSameMonthProperties != null && lpiSameMonthProperties.isEnabledForDisbursementDate(loan.getDisbursementDate())) {
            log.debug("LPI same-month: re-applying CredX wrapper after charge removal for loanId={}", loan.getId());
            final CredXLoanRepaymentScheduleProcessingWrapper wrapper = new CredXLoanRepaymentScheduleProcessingWrapper();
            wrapper.reprocess(loan.getCurrency(), loan.getDisbursementDate(), loan.getRepaymentScheduleInstallments(),
                    loan.getActiveCharges());
        }
    }
}
