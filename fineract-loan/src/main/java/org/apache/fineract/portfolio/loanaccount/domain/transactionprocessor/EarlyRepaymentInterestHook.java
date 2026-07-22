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
package org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor;

import java.time.LocalDate;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * Extension point for deployment-specific early-repayment interest handling, invoked by
 * {@link AbstractLoanRepaymentScheduleTransactionProcessor} for every strategy. No default (core) implementation is
 * provided; deployments that want this behaviour register a hook via {@link EarlyRepaymentInterestHookRegistry}.
 */
public interface EarlyRepaymentInterestHook {

    /**
     * Called before reprocessing an installment's derived components, so a previously applied reduction can be
     * undone/restored prior to being recomputed for the current set of transactions.
     */
    void restoreBeforeReprocessing(LoanRepaymentScheduleInstallment installment);

    /**
     * Called when a transaction is being applied to {@code installment} ahead of its due date, so implementations may
     * reduce the installment's charged interest for the unused remainder of the period.
     */
    void reduceForEarlyPayment(LoanRepaymentScheduleInstallment installment, LocalDate paymentDate, MonetaryCurrency currency);
}
