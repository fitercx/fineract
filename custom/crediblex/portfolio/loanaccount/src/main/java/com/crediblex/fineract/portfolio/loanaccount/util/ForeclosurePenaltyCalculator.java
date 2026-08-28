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
 * Computes how much LPI/penalty is payable when settling a loan on a given value date.
 * <p>
 * The quoted/collectable amount is the sum of active penalty charges dated <em>strictly before</em> the settlement
 * date. Charges dated on the settlement date or later are auto-waived by the write path
 * ({@code CredXLoanChargeWritePlatformService#waiveOverdueChargesOnOrAfterDate} for repayments and foreclosure). Quote
 * and waiver are deliberate complements, so preview == booked amount.
 * <p>
 * Summing from {@code loan.getActiveCharges()} (not the schedule's cached {@code penaltyChargesOutstanding}) avoids
 * stale-installment-penalty-cache inflation on multi-overdue loans (see BUG_REPORT.md Finding #2/#3).
 */
public final class ForeclosurePenaltyCalculator {

    private ForeclosurePenaltyCalculator() {}

    /**
     * Penalty quoted to the operator and collected at settlement: active charges dated strictly before
     * {@code settlementDate}. Used by foreclosure/repayment templates and {@code foreCloseLoan}.
     */
    public static Money computePenaltyQuotedForSettlementDate(final Loan loan, final LocalDate settlementDate,
            final MonetaryCurrency currency) {
        Money totalPenaltyPayable = Money.zero(currency);
        for (final LoanCharge loanCharge : loan.getActiveCharges()) {
            if (!loanCharge.isPenaltyCharge()) {
                continue;
            }
            final LocalDate chargeAccrualDate = loanCharge.getDueDate();
            if (chargeAccrualDate != null && !DateUtils.isBefore(chargeAccrualDate, settlementDate)) {
                continue;
            }
            final LocalDate effectiveDueDate = resolveEffectiveDueDateForForeclosure(loanCharge);
            if (effectiveDueDate != null && DateUtils.isAfter(effectiveDueDate, settlementDate)) {
                continue;
            }
            totalPenaltyPayable = totalPenaltyPayable.plus(loanCharge.getAmountOutstanding(currency));
        }
        return totalPenaltyPayable;
    }

    /**
     * Alias for {@link #computePenaltyQuotedForSettlementDate}; kept for existing callers.
     */
    public static Money computePenaltyPayableFromActiveCharges(final Loan loan, final LocalDate foreClosureDate,
            final MonetaryCurrency currency) {
        return computePenaltyQuotedForSettlementDate(loan, foreClosureDate, currency);
    }

    private static LocalDate resolveEffectiveDueDateForForeclosure(final LoanCharge loanCharge) {
        if (loanCharge.isOverdueInstallmentCharge() && loanCharge.getOverdueInstallmentCharge() != null
                && loanCharge.getOverdueInstallmentCharge().getInstallment() != null) {
            return loanCharge.getOverdueInstallmentCharge().getInstallment().getDueDate();
        }
        return loanCharge.getDueDate();
    }
}
