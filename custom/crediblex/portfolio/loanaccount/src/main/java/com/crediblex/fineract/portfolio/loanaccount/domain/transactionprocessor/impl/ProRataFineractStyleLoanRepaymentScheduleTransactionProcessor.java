package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl;

import java.util.List;
import java.util.Set;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.FineractStyleLoanRepaymentScheduleTransactionProcessor;

public class ProRataFineractStyleLoanRepaymentScheduleTransactionProcessor extends FineractStyleLoanRepaymentScheduleTransactionProcessor {

    public static final String STRATEGY_CODE = "pro-rata-mifos-standard-strategy";
    public static final String STRATEGY_NAME = "Pro-Rata Penalties, Fees, Interest, Principal order";

    public ProRataFineractStyleLoanRepaymentScheduleTransactionProcessor(ExternalIdFactory externalIdFactory) {
        super(externalIdFactory);
    }

    @Override
    public String getCode() {
        return STRATEGY_CODE;
    }

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    /**
     * Early interest is reduced on {@code interestCharged} in
     * {@link org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.AbstractLoanRepaymentScheduleTransactionProcessor}
     * before this method runs (all strategies). Allocate like an on-time payment against the reduced charged amount.
     */
    @Override
    protected Money handleTransactionThatIsPaymentInAdvanceOfInstallment(final LoanRepaymentScheduleInstallment currentInstallment,
            final List<LoanRepaymentScheduleInstallment> installments, final LoanTransaction loanTransaction, final Money paymentInAdvance,
            List<LoanTransactionToRepaymentScheduleMapping> transactionMappings, Set<LoanCharge> charges) {

        return handleTransactionThatIsOnTimePaymentOfInstallment(currentInstallment, loanTransaction, paymentInAdvance, transactionMappings,
                charges);
    }
}
