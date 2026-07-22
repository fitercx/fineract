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
 * Static holder for an optional {@link EarlyRepaymentInterestHook}, following the same pattern as {@code MoneyHelper}:
 * {@code @Entity}/transaction-processor domain classes here are not Spring beans, but deployment-specific modules (e.g.
 * a custom module) can register an implementation at startup via {@link #register(EarlyRepaymentInterestHook)}. When no
 * hook is registered, calls are no-ops and core behaviour is unchanged.
 */
public final class EarlyRepaymentInterestHookRegistry {

    private static volatile EarlyRepaymentInterestHook hook;

    private EarlyRepaymentInterestHookRegistry() {}

    public static void register(final EarlyRepaymentInterestHook hookImpl) {
        hook = hookImpl;
    }

    public static void restoreBeforeReprocessing(final LoanRepaymentScheduleInstallment installment) {
        final EarlyRepaymentInterestHook current = hook;
        if (current != null) {
            current.restoreBeforeReprocessing(installment);
        }
    }

    public static void reduceForEarlyPayment(final LoanRepaymentScheduleInstallment installment, final LocalDate paymentDate,
            final MonetaryCurrency currency) {
        final EarlyRepaymentInterestHook current = hook;
        if (current != null) {
            current.reduceForEarlyPayment(installment, paymentDate, currency);
        }
    }
}
