package com.crediblex.fineract.portfolio.dpdrepayment.service;

import com.crediblex.fineract.portfolio.dpdrepayment.DpdRepaymentConstants;
import com.crediblex.fineract.portfolio.dpdrepayment.data.DpdRepaymentProductConfigData;
import com.crediblex.fineract.portfolio.dpdrepayment.data.DpdRepaymentStatusData;
import com.crediblex.fineract.portfolio.dpdrepayment.domain.transactionprocessor.DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the effective repayment strategy at transaction time. Does not mutate
 * {@code m_loan.loan_transaction_strategy_code}, so LPI accrual and historical transactions are not replayed when DPD
 * crosses the threshold.
 * <p>
 * LPI job isolation is enforced separately by {@code CredXLoanChargeWritePlatformServiceImpl
 * #shouldReprocessTransactionsAfterOverdueChargeApply()} ({@code false}), which prevents the overdue-charge path from
 * calling {@code ReprocessLoanTransactionsService.reprocessTransactions()}.
 */
@Service
@RequiredArgsConstructor
public class DpdRepaymentStrategyResolver {

    private final DpdRepaymentProductConfigService productConfigService;
    private final DpdRepaymentGlobalConfigService globalConfigService;
    private final DpdMaxDaysPastDueService maxDaysPastDueService;
    private final LoanRepaymentScheduleTransactionProcessorFactory transactionProcessorFactory;

    public String resolveEffectiveStrategyCode(final Loan loan, final LocalDate asOfDate) {
        final DpdRepaymentStatusData status = resolveStatus(loan, asOfDate);
        return status.getEffectiveRepaymentStrategyCode();
    }

    public DpdRepaymentStatusData resolveStatus(final Loan loan, final LocalDate asOfDate) {
        if (loan == null) {
            return inactiveStatus(null, null);
        }
        return resolveStatus(loan.getId(), loan.getLoanProduct() != null ? loan.getLoanProduct().getId() : null,
                loan.getTransactionProcessingStrategyCode(), asOfDate);
    }

    public DpdRepaymentStatusData resolveStatus(final Long loanId, final Long loanProductId, final String baseStrategyCode,
            final LocalDate asOfDate) {
        final LocalDate effectiveDate = asOfDate != null ? asOfDate : DateUtils.getBusinessLocalDate();
        if (loanId == null || loanProductId == null || baseStrategyCode == null) {
            return inactiveStatus(baseStrategyCode, loanProductId);
        }

        final DpdRepaymentProductConfigData productConfig = productConfigService.findByLoanProductId(loanProductId)
                .orElse(DpdRepaymentProductConfigData.builder().loanProductId(loanProductId).enableDpdPrincipalOnlyRepayment(false)
                        .build());

        final int threshold = globalConfigService.getPrincipalOnlyThreshold();
        final int maxDpd = maxDaysPastDueService.calculateMaxDpd(loanId, effectiveDate);
        final boolean active = productConfig.isEnableDpdPrincipalOnlyRepayment() && maxDpd > threshold;
        final String effectiveStrategyCode = active ? DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE
                : baseStrategyCode;
        final String effectiveStrategyName = transactionProcessorFactory.determineProcessor(effectiveStrategyCode).getName();

        return DpdRepaymentStatusData.builder().maxDpd(maxDpd).dpdPrincipalOnlyActive(active)
                .effectiveRepaymentStrategyCode(effectiveStrategyCode).effectiveRepaymentStrategyName(effectiveStrategyName)
                .baseRepaymentStrategyCode(baseStrategyCode).dpdThreshold(threshold)
                .productFeatureEnabled(productConfig.isEnableDpdPrincipalOnlyRepayment()).build();
    }

    private DpdRepaymentStatusData inactiveStatus(final String baseStrategyCode, final Long loanProductId) {
        return DpdRepaymentStatusData.builder().maxDpd(0).dpdPrincipalOnlyActive(false).effectiveRepaymentStrategyCode(baseStrategyCode)
                .effectiveRepaymentStrategyName(
                        baseStrategyCode != null ? transactionProcessorFactory.determineProcessor(baseStrategyCode).getName() : null)
                .baseRepaymentStrategyCode(baseStrategyCode).dpdThreshold(globalConfigService.getPrincipalOnlyThreshold())
                .productFeatureEnabled(false).build();
    }
}
