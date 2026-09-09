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

import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;

/**
 * Extension point for deployment-specific paid-charge refunds that must bypass the standard active-loan refund
 * allocation waterfall.
 * <p>
 * CredibleX uses this hook for paid LPI reversal/refund transactions. The same transaction can be replayed while
 * reprocessing loan history, applying a later repayment, or calculating/executing foreclosure. Handling it through this
 * strategy-neutral hook keeps the original principal and interest allocation unchanged in every workflow.
 * <p>
 * Implementations must return {@code true} only when they recognize and fully process their own targeted transaction.
 * Returning {@code false} is required for ordinary active-loan refunds so the existing core repayment, reprocessing,
 * and foreclosure behavior remains unchanged. A different refund workflow should use a separate hook method or
 * implementation rather than widening the recognition criteria of an existing workflow.
 */
public interface TargetedLoanChargeRefundHook {

    /**
     * Applies a recognized targeted paid-charge refund during either initial transaction processing or transaction
     * replay.
     *
     * @return {@code true} when the implementation owns and has processed the transaction; {@code false} to continue
     *         with the standard core refund workflow
     */
    boolean applyIfSupported(LoanTransaction transaction, MonetaryCurrency currency, List<LoanRepaymentScheduleInstallment> installments);
}
