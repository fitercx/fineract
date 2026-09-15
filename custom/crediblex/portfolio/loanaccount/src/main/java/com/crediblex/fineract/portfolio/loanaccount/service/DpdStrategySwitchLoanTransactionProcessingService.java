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

import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.TransactionCtx;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTermVariationsMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Re-evaluates the DPD strategy switch every time a loan transaction is allocated, which is what makes the switch and
 * the revert automatic without a scheduled job.
 *
 * <p>
 * The caller passes the strategy code it read off the loan before this call, so the switch is applied here and the
 * <em>resulting</em> code is used for the allocation. That way the very repayment that takes a loan past the threshold
 * is already applied principal-first.
 *
 * <p>
 * Only {@code processLatestTransaction} is intercepted. Replays and reprocessing deliberately keep using the loan's
 * stored strategy so historical allocations are not rewritten.
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
        final ChangedTransactionDetail result = super.processLatestTransaction(
                resolveStrategyCode(transactionProcessingStrategyCode, loanTransaction), loanTransaction, ctx);
        reevaluateAfterAllocation(loanTransaction);
        return result;
    }

    /**
     * The allocation above may have cleared the arrears that caused the switch. Re-evaluating here means the loan is
     * left in the right state immediately, rather than carrying a stale switched strategy until the next transaction
     * happens to come along - which, for a loan that has just been brought current, may be weeks away.
     */
    private void reevaluateAfterAllocation(final LoanTransaction loanTransaction) {
        final Loan loan = loanTransaction == null ? null : loanTransaction.getLoan();
        if (loan == null || loan.getId() == null) {
            return;
        }
        try {
            dpdStrategySwitchService.reevaluate(loan, loanTransaction.getTransactionDate());
        } catch (final RuntimeException e) {
            // Same rule as above: the switch must never be the reason a repayment fails.
            log.error("Could not re-evaluate the DPD strategy switch for loan {} after allocation", loan.getId(), e);
        }
    }

    private String resolveStrategyCode(final String requestedStrategyCode, final LoanTransaction loanTransaction) {
        if (loanTransaction == null) {
            return requestedStrategyCode;
        }
        final Loan loan = loanTransaction.getLoan();
        if (loan == null || loan.getId() == null) {
            // Transactions that are not attached to a persisted loan yet (some reprocess paths) carry no DPD context.
            return requestedStrategyCode;
        }
        try {
            final String effectiveStrategyCode = dpdStrategySwitchService.resolveEffectiveStrategyCode(loan,
                    loanTransaction.getTransactionDate());
            return effectiveStrategyCode != null ? effectiveStrategyCode : requestedStrategyCode;
        } catch (final RuntimeException e) {
            // A failure to evaluate the switch must never block a repayment; fall back to the loan's stored strategy.
            log.error("Could not evaluate the DPD strategy switch for loan {}; using strategy {}", loan.getId(), requestedStrategyCode, e);
            return requestedStrategyCode;
        }
    }
}
