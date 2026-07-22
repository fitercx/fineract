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

/**
 * Single source of truth for "how much penalty is actually payable to foreclose this loan as of this date", shared by
 * both the foreclosure TEMPLATE (the amount quoted to the user before they submit,
 * {@code CredXLoanReadPlatformServiceImpl#retrieveLoanForeclosureTemplate}) and the actual foreclosure SETTLEMENT (the
 * amount really withdrawn from the linked savings account and applied to the loan,
 * {@code CustomLoanAccountDomainServiceJpa#foreCloseLoan}).
 * <p>
 * {@code Loan#fetchLoanForeclosureDetail()} computes the penalty payable by summing each installment's CACHED
 * {@code penaltyChargesOutstanding} schedule field. On a multi-installment loan with 2+ separately-overdue installments
 * (each carrying its own daily-accruing LPI charges), that cache can go stale/inflated relative to what the loan's
 * actual active charges total - the same class of stale-installment-penalty-cache bug fixed for reverse-LPI in
 * {@code CredXLoanChargeWritePlatformServiceImpl} (see BUG_REPORT.md Finding #2). Left unfixed here, that stale figure
 * drove a REAL over-withdrawal from the linked savings account during foreclosure settlement, leaving the loan stuck
 * "Overpaid" instead of "Closed" (see BUG_REPORT.md Finding #3). Summing directly from {@code loan.getActiveCharges()}
 * (the source of truth) instead avoids that, and using the exact same computation for the template AND the settlement
 * guarantees the amount quoted to the user always matches what is actually withdrawn.
 */
public final class ForeclosurePenaltyCalculator {

    private ForeclosurePenaltyCalculator() {}

    /**
     * Sums {@code getAmountOutstanding()} across the loan's own ACTIVE penalty charges that are due on/before
     * {@code foreClosureDate}. For an overdue-installment LPI charge, "due on/before the foreclosure date" is evaluated
     * against its OWNING installment's due date (via the authoritative {@code LoanOverdueInstallmentCharge} link, same
     * approach as the Finding #2 fix), not the charge's own due date - which for these charges is deliberately dated
     * AFTER the installment's due date, during its arrears period. This mirrors, and is deliberately consistent with,
     * {@code Loan#retrieveIncomeOutstandingTillDate}'s own semantics of "which installments are due-through this date",
     * so a backdated foreclosure still excludes LPI that only accrued strictly after the foreclosure date.
     */
    public static Money computePenaltyPayableFromActiveCharges(final Loan loan, final LocalDate foreClosureDate,
            final MonetaryCurrency currency) {
        Money totalPenaltyPayable = Money.zero(currency);
        for (final LoanCharge loanCharge : loan.getActiveCharges()) {
            if (!loanCharge.isPenaltyCharge()) {
                continue;
            }
            final LocalDate effectiveDueDate = resolveEffectiveDueDateForForeclosure(loanCharge);
            if (effectiveDueDate != null && DateUtils.isAfter(effectiveDueDate, foreClosureDate)) {
                continue;
            }
            totalPenaltyPayable = totalPenaltyPayable.plus(loanCharge.getAmountOutstanding(currency));
        }
        return totalPenaltyPayable;
    }

    private static LocalDate resolveEffectiveDueDateForForeclosure(final LoanCharge loanCharge) {
        if (loanCharge.isOverdueInstallmentCharge() && loanCharge.getOverdueInstallmentCharge() != null
                && loanCharge.getOverdueInstallmentCharge().getInstallment() != null) {
            return loanCharge.getOverdueInstallmentCharge().getInstallment().getDueDate();
        }
        return loanCharge.getDueDate();
    }
}
