/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.TransactionCtx;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTermVariationsMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Allocates a repayment or foreclosure with the DPD strategy that applied on that transaction's own date, and stamps
 * the code on the transaction so a later replay cannot recast it.
 *
 * <p>
 * The loan's stored strategy is the strategy in force as of the business date. A backdated repayment does not change
 * it. Foreclosure does not change it either: the foreclosure amount was quoted against the strategy already on the
 * loan, and the journals have to match that quote. Close of business and a same-day repayment still move the loan's
 * strategy, which is what the account screen shows.
 *
 * <p>
 * Replay of a stamped transaction reapplies the posted portions. Unstamped history is replayed with the pre-switch
 * strategy. Neither path reverses a journal just because the loan has since moved to principal-first.
 */
@Slf4j
@Service
@Primary
public class DpdStrategySwitchLoanTransactionProcessingService extends LoanTransactionProcessingService {

    private final DpdStrategySwitchService dpdStrategySwitchService;

    public DpdStrategySwitchLoanTransactionProcessingService(
            final LoanRepaymentScheduleTransactionProcessorFactory transactionProcessorFactory, final LoanTermVariationsMapper loanMapper,
            final DpdStrategySwitchService dpdStrategySwitchService) {
        super(transactionProcessorFactory, loanMapper);
        this.dpdStrategySwitchService = dpdStrategySwitchService;
    }

    @Override
    public ChangedTransactionDetail processLatestTransaction(final String transactionProcessingStrategyCode,
            final LoanTransaction loanTransaction, final TransactionCtx ctx) {
        final String allocationStrategy = resolveStrategyCode(transactionProcessingStrategyCode, loanTransaction);
        final ChangedTransactionDetail result = super.processLatestTransaction(allocationStrategy, loanTransaction, ctx);
        stamp(loanTransaction, allocationStrategy);
        reevaluateAfterAllocation(loanTransaction);
        return result;
    }

    @Override
    public ChangedTransactionDetail reprocessLoanTransactions(final String transactionProcessingStrategyCode,
            final LocalDate disbursementDate, final List<LoanTransaction> repaymentsOrWaivers, final MonetaryCurrency currency,
            final List<LoanRepaymentScheduleInstallment> repaymentScheduleInstallments, final Set<LoanCharge> charges) {
        final Loan loan = loanOf(repaymentsOrWaivers);
        if (loan == null || !dpdStrategySwitchService.isOptedIn(loan)) {
            return super.reprocessLoanTransactions(transactionProcessingStrategyCode, disbursementDate, repaymentsOrWaivers, currency,
                    repaymentScheduleInstallments, charges);
        }

        final String replayStrategy = dpdStrategySwitchService.replayStrategyCode(loan);
        for (final LoanTransaction transaction : repaymentsOrWaivers) {
            if (transaction.getId() != null && transaction.getDpdAllocationStrategyCode() != null) {
                transaction.lockDpdAllocation();
                continue;
            }
            if (transaction.getId() != null || !isAllocatingRepayment(transaction)) {
                continue;
            }
            final String allocationStrategy = strategyForNewTransaction(loan, transaction, transactionProcessingStrategyCode);
            transaction.setDpdAllocationStrategyCode(allocationStrategy);
            if (allocationStrategy != null && !allocationStrategy.equals(replayStrategy)) {
                transaction.setDpdAllocationProcessor(getTransactionProcessor(allocationStrategy));
            }
        }
        return super.reprocessLoanTransactions(replayStrategy, disbursementDate, repaymentsOrWaivers, currency,
                repaymentScheduleInstallments, charges);
    }

    /**
     * Same-day repayments update the loan strategy before allocation, so this payment and later ones follow it.
     * Backdated repayments and foreclosures allocate with a code that is only stamped on the transaction.
     */
    private String resolveStrategyCode(final String requestedStrategyCode, final LoanTransaction loanTransaction) {
        if (loanTransaction == null) {
            return requestedStrategyCode;
        }
        final Loan loan = loanTransaction.getLoan();
        if (loan == null || loan.getId() == null || !isAllocatingRepayment(loanTransaction)) {
            return requestedStrategyCode;
        }
        try {
            if (loan.isForeclosure()) {
                return requestedStrategyCode;
            }
            if (isBackdated(loanTransaction)) {
                final String peeked = dpdStrategySwitchService.peekAllocationStrategyCode(loan, loanTransaction.getTransactionDate(), true);
                return peeked != null ? peeked : requestedStrategyCode;
            }
            final String effectiveStrategyCode = dpdStrategySwitchService.resolveEffectiveStrategyCode(loan,
                    loanTransaction.getTransactionDate());
            return effectiveStrategyCode != null ? effectiveStrategyCode : requestedStrategyCode;
        } catch (final RuntimeException e) {
            log.error("Could not evaluate the DPD strategy switch for loan {}; using strategy {}", loan.getId(), requestedStrategyCode, e);
            return requestedStrategyCode;
        }
    }

    private String strategyForNewTransaction(final Loan loan, final LoanTransaction transaction, final String requestedStrategyCode) {
        if (loan.isForeclosure()) {
            return requestedStrategyCode;
        }
        try {
            final String peeked = dpdStrategySwitchService.peekAllocationStrategyCode(loan, transaction.getTransactionDate(),
                    isBackdated(transaction));
            return peeked != null ? peeked : requestedStrategyCode;
        } catch (final RuntimeException e) {
            log.error("Could not choose a DPD allocation strategy for loan {} during replay; using {}", loan.getId(), requestedStrategyCode,
                    e);
            return requestedStrategyCode;
        }
    }

    /**
     * Brings the loan's stored strategy in line with the business date after a same-day allocation. Skipped for
     * foreclosure, and for a backdated repayment the re-evaluation uses the business date so a value date in the past
     * cannot revert a loan that is still past the threshold today.
     */
    private void reevaluateAfterAllocation(final LoanTransaction loanTransaction) {
        final Loan loan = loanTransaction == null ? null : loanTransaction.getLoan();
        if (loan == null || loan.getId() == null || loan.isForeclosure() || !isAllocatingRepayment(loanTransaction)) {
            return;
        }
        final LocalDate asOf = isBackdated(loanTransaction) ? DateUtils.getBusinessLocalDate() : loanTransaction.getTransactionDate();
        try {
            dpdStrategySwitchService.reevaluate(loan, asOf);
        } catch (final RuntimeException e) {
            log.error("Could not re-evaluate the DPD strategy switch for loan {} after allocation", loan.getId(), e);
        }
    }

    private void stamp(final LoanTransaction loanTransaction, final String allocationStrategy) {
        if (loanTransaction != null && allocationStrategy != null && isAllocatingRepayment(loanTransaction)) {
            loanTransaction.setDpdAllocationStrategyCode(allocationStrategy);
        }
    }

    private static boolean isBackdated(final LoanTransaction loanTransaction) {
        final LocalDate transactionDate = loanTransaction.getTransactionDate();
        return transactionDate != null && transactionDate.isBefore(DateUtils.getBusinessLocalDate());
    }

    private static boolean isAllocatingRepayment(final LoanTransaction loanTransaction) {
        return loanTransaction.isRepaymentLikeType() || loanTransaction.isRecoveryRepayment();
    }

    private static Loan loanOf(final List<LoanTransaction> transactions) {
        if (transactions == null) {
            return null;
        }
        for (final LoanTransaction transaction : transactions) {
            if (transaction.getLoan() != null) {
                return transaction.getLoan();
            }
        }
        return null;
    }
}
