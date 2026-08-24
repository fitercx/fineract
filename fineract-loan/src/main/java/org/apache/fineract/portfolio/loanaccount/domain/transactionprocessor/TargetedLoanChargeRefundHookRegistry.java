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
 * Static holder for the optional targeted paid-charge refund hook.
 * <p>
 * Both traditional and progressive transaction processors call this registry during initial processing and full
 * transaction replay. Consequently an LPI refund keeps the same allocation when a later repayment or foreclosure
 * reprocesses the loan. Without a registered custom implementation, or when the implementation does not recognize a
 * transaction, standard core refund processing is unchanged.
 */
public final class TargetedLoanChargeRefundHookRegistry {

    private static volatile TargetedLoanChargeRefundHook hook;

    private TargetedLoanChargeRefundHookRegistry() {}

    public static void register(final TargetedLoanChargeRefundHook hookImpl) {
        hook = hookImpl;
    }

    /**
     * Gives the registered deployment-specific hook the first opportunity to process a targeted refund.
     *
     * @return {@code true} only when custom processing completed; {@code false} instructs the caller to execute the
     *         normal core refund workflow
     */
    public static boolean applyIfSupported(final LoanTransaction transaction, final MonetaryCurrency currency,
            final List<LoanRepaymentScheduleInstallment> installments) {
        final TargetedLoanChargeRefundHook current = hook;
        return current != null && current.applyIfSupported(transaction, currency, installments);
    }
}
