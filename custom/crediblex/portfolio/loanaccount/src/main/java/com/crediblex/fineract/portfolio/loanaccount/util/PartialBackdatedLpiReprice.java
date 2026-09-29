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

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * Recomputes unpaid percentage LPI dated on or after a partial backdated repayment from the principal (and interest,
 * where the charge says so) still outstanding after that payment.
 * <p>
 * Days strictly before the value date keep the rate they accrued at — that principal was still outstanding then, and
 * this repayment collects them. Later unpaid days are still on the loan because a partial payment does not waive them;
 * they must follow the reduced balance (LMS-150).
 */
public final class PartialBackdatedLpiReprice {

    private PartialBackdatedLpiReprice() {}

    /**
     * @return {@code true} when at least one charge amount changed
     */
    public static boolean repriceUnpaidChargesOnOrAfter(final Loan loan, final LocalDate fromDate) {
        if (loan == null || fromDate == null || loan.getCurrency() == null || loan.getLoanCharges() == null) {
            return false;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        boolean changed = false;
        for (final LoanCharge charge : loan.getLoanCharges()) {
            if (!isRepriceable(charge, fromDate, currency)) {
                continue;
            }
            final BigDecimal rate = charge.amountOrPercentage();
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            final BigDecimal base = baseAmount(loan, charge, currency);
            if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            final BigDecimal repriced = LoanCharge.percentageOf(base, rate);
            final Money current = charge.getAmount(currency);
            if (current != null && current.getAmount() != null && current.getAmount().compareTo(repriced) == 0) {
                continue;
            }
            charge.update(rate, charge.getDueLocalDate(), base, null, BigDecimal.ZERO);
            changed = true;
        }
        return changed;
    }

    private static boolean isRepriceable(final LoanCharge charge, final LocalDate fromDate, final MonetaryCurrency currency) {
        if (charge == null || !charge.isActive() || charge.isWaived() || !charge.isOverdueInstallmentCharge()) {
            return false;
        }
        final ChargeCalculationType calculation = charge.getChargeCalculation();
        if (calculation != ChargeCalculationType.PERCENT_OF_AMOUNT && calculation != ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST
                && calculation != ChargeCalculationType.PERCENT_OF_INTEREST) {
            return false;
        }
        final LocalDate due = charge.getDueLocalDate();
        if (due == null || due.isBefore(fromDate)) {
            return false;
        }
        final Money paid = charge.getAmountPaid(currency);
        return paid == null || !paid.isGreaterThanZero();
    }

    private static BigDecimal baseAmount(final Loan loan, final LoanCharge charge, final MonetaryCurrency currency) {
        LoanRepaymentScheduleInstallment installment = null;
        if (charge.getOverdueInstallmentCharge() != null) {
            installment = charge.getOverdueInstallmentCharge().getInstallment();
        }
        final ChargeCalculationType calculation = charge.getChargeCalculation();
        if (installment != null) {
            if (calculation == ChargeCalculationType.PERCENT_OF_INTEREST) {
                return installment.getInterestOutstanding(currency).getAmount();
            }
            Money base = installment.getPrincipalOutstanding(currency);
            if (calculation == ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST) {
                base = base.plus(installment.getInterestOutstanding(currency));
            }
            return base.getAmount();
        }
        if (loan.getSummary() == null || loan.getSummary().getTotalPrincipalOutstanding() == null) {
            return null;
        }
        if (calculation == ChargeCalculationType.PERCENT_OF_AMOUNT) {
            return loan.getSummary().getTotalPrincipalOutstanding();
        }
        return null;
    }
}
