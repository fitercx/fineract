package com.crediblex.fineract.portfolio.dpdrepayment.service;

import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.TransactionCtx;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTermVariationsMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Wraps repayment allocation so DPD principal-only mode is applied on new repayments only. Full transaction reprocess
 * paths continue to use the loan's stored product strategy code.
 */
@Service
@Primary
public class CredXLoanTransactionProcessingService extends LoanTransactionProcessingService {

    private final DpdRepaymentStrategyResolver dpdRepaymentStrategyResolver;

    public CredXLoanTransactionProcessingService(final LoanRepaymentScheduleTransactionProcessorFactory transactionProcessorFactory,
            final LoanTermVariationsMapper loanMapper, final DpdRepaymentStrategyResolver dpdRepaymentStrategyResolver) {
        super(transactionProcessorFactory, loanMapper);
        this.dpdRepaymentStrategyResolver = dpdRepaymentStrategyResolver;
    }

    @Override
    public ChangedTransactionDetail processLatestTransaction(final String transactionProcessingStrategyCode,
            final LoanTransaction loanTransaction, final TransactionCtx ctx) {
        final String effectiveStrategyCode = dpdRepaymentStrategyResolver.resolveEffectiveStrategyCode(loanTransaction.getLoan(),
                loanTransaction.getTransactionDate());
        return super.processLatestTransaction(effectiveStrategyCode, loanTransaction, ctx);
    }
}
