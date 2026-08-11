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

import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * Fix 2 pure-logic helpers for auto-waiving post-due-date LPI on a LOC (payable/receivable) repayment made ON an
 * installment due date. Kept as a stateless util (no Spring wiring) shared by the write path (the actual auto-waive in
 * {@code CustomLoanWritePlatformServiceJpaRepositoryImpl#makeLoanRepayment}) and the read path (the live-preview
 * penalty adjustment in {@code CredXLoanReadPlatformServiceImpl#retrieveLoanTransactionTemplate}).
 * <p>
 * The write path waives LPI strictly AFTER the value/due date (processing-delay days only). Callers that preview the
 * waivable amount should therefore pass {@code onDate.plusDays(1)} as {@code fromDate}.
 */
public final class LocDueDateRepaymentUtils {

    private LocDueDateRepaymentUtils() {}

    /** True when {@code date} exactly equals any installment's due date on the loan. */
    public static boolean isOnInstallmentDueDate(final Loan loan, final LocalDate date) {
        if (date == null) {
            return false;
        }
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (DateUtils.isEqual(date, installment.getDueDate())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sum of outstanding OVERDUE_INSTALLMENT (LPI) charges whose OWN due date falls in {@code [fromDate, toDate]}. This
     * deliberately mirrors the exact set {@code CredXLoanChargeWritePlatformServiceImpl#waiveOverdueChargesInWindow}
     * waives (same charge-time, same inclusive window on the charge's own {@code dueDate}, same
     * active/!waived/!paid/outstanding&gt;0 filter), so the previewed penalty equals what remains after the auto-waive.
     * {@link Loan#getActiveCharges()} already restricts to active charges.
     */
    public static Money sumWaivableOverdueLpi(final Loan loan, final LocalDate fromDate, final LocalDate toDate,
            final MonetaryCurrency currency) {
        Money total = Money.zero(currency);
        for (final LoanCharge charge : loan.getActiveCharges()) {
            if (!charge.isOverdueInstallmentCharge()) {
                continue;
            }
            final LocalDate chargeDueDate = charge.getDueLocalDate();
            if (chargeDueDate == null || DateUtils.isBefore(chargeDueDate, fromDate) || DateUtils.isAfter(chargeDueDate, toDate)) {
                continue;
            }
            if (charge.isWaived() || charge.isPaid()) {
                continue;
            }
            final Money outstanding = charge.getAmountOutstanding(currency);
            if (outstanding.isGreaterThanZero()) {
                total = total.plus(outstanding);
            }
        }
        return total;
    }
}
