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

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * Fix 1: blocks foreclosure of a LOC (payable/receivable drawdown) loan once it is on or past its earliest unpaid
 * installment due date. Foreclosure means <em>early</em> settlement; once the loan is due/overdue it is no longer a
 * foreclosure scenario (record a repayment instead). Enforcing this makes the complex "penalty payable on foreclosure"
 * math unreachable for LOC loans. Scope is limited to LOC loans (those carrying a {@link LoanLineOfCreditParams} row);
 * every other product is left untouched, mirroring the pure-static, no-Spring-wiring pattern of
 * {@code BackdatedRepaymentValidator}. Called from both the foreclosure template (preview) and the actual foreclosure
 * execution so the UI and the hard backend block always agree.
 */
public final class LocForeclosureValidator {

    private LocForeclosureValidator() {}

    public static void validateNotDueOrOverdue(final Loan loan, final LocalDate foreclosureDate,
            final Optional<LoanLineOfCreditParams> locParams) {
        // Non-LOC loan: no-op (presence of the LOC params row = payable-or-receivable, the only two LocProductTypes).
        if (locParams.isEmpty()) {
            return;
        }

        LocalDate earliestUnpaidDueDate = null;
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (installment.isNotFullyPaidOff()) {
                earliestUnpaidDueDate = installment.getDueDate();
                break;
            }
        }

        // Fully paid (nothing unpaid): not a due/overdue case - let the normal flow handle it.
        if (earliestUnpaidDueDate == null) {
            return;
        }

        // Block on-or-after the earliest unpaid due date (i.e. !isBefore). On the due date the loan is already due, so
        // foreclosure is no longer "early".
        if (!DateUtils.isBefore(foreclosureDate, earliestUnpaidDueDate)) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.foreclosure.not.allowed.on.or.after.due.date",
                    "Loan " + loan.getId() + " cannot be foreclosed on " + foreclosureDate
                            + " because it is on or past its earliest unpaid installment due date (" + earliestUnpaidDueDate
                            + "). Foreclosure is only allowed before the loan is due; record a repayment instead.",
                    loan.getId(), foreclosureDate, earliestUnpaidDueDate);
        }
    }
}
