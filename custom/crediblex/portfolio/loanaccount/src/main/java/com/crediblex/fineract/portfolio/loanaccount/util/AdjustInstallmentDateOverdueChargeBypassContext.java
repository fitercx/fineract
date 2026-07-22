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
package com.crediblex.fineract.portfolio.loanaccount.util;

/**
 * Narrow, request-scoped escape hatch that lets the "Adjust Installment Date" custom endpoint's internal create+approve
 * reschedule-request calls skip the generic Loan Reschedule engine's {@code not.allowed.due.to.overdue.charges} guard
 * (see {@code CredXLoanRescheduleRequestDataValidator#validateForOverdueCharges}).
 * <p>
 * Background: that engine-level guard rejects rescheduling installment N whenever ANY active, unpaid overdue
 * (penalty/fee/tax) charge has a due date after installment N's period-start - i.e. it also fires when an EARLIER,
 * already-overdue installment (N-1, N-2, ...) still carries unresolved LPI, even though the installment actually being
 * moved (N) is not itself overdue. That is the correct, conservative rule for the full manual "Loan Reschedule > Change
 * Repayment Date" workflow (grace/EMI/interest-rate changes reviewed as a formal request), but it is stricter than the
 * rule the "Adjust Installment Date" feature was actually designed and tested against:
 * {@code CustomLoanWritePlatformServiceJpaRepositoryImpl#validateNoOverdueChargesForInstallment} only blocks adjusting
 * an installment that is ITSELF overdue with outstanding charges - which is also exactly what the Angular dialog
 * enforces client-side (only the overdue installment's row is disabled in the picker; later, not-yet-due installments
 * remain selectable). Without this bypass, selecting a later installment in the UI succeeds client-side but then fails
 * server-side with "Failed data validation due to: not.allowed.due.to.overdue.charges." purely because an earlier
 * installment has unrelated, unresolved LPI - blocking a legitimate date correction even though:
 * <ul>
 * <li>the LPI charges themselves are untouched by the date move (they stay exactly as charged/outstanding), and</li>
 * <li>the schedule regeneration recalculates interest for the moved installment and every later one from the new date
 * forward, so "coming EMIs" interest is handled correctly regardless of unrelated arrears on earlier installments.</li>
 * </ul>
 * The flag is set only for the duration of the internal create+approve calls this feature issues (never for the
 * standard, user-facing Loan Reschedule request flow), always cleared in a {@code finally} block, and is thread-scoped
 * so it cannot leak across concurrent requests.
 */
public final class AdjustInstallmentDateOverdueChargeBypassContext {

    private static final ThreadLocal<Boolean> BYPASS = new ThreadLocal<>();

    private AdjustInstallmentDateOverdueChargeBypassContext() {}

    public static boolean isBypassed() {
        return Boolean.TRUE.equals(BYPASS.get());
    }

    public static void enable() {
        BYPASS.set(Boolean.TRUE);
    }

    public static void clear() {
        BYPASS.remove();
    }
}
