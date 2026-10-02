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
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;

/**
 * Decides whether a repayment that would otherwise waive LPI should leave those charges alive instead.
 * <p>
 * A full close still waives LPI dated on or after the value date (LMS-125). A partial payment, backdated or on the
 * installment due date, leaves the loan active. Later LPI stays on the same charge rows, outstanding, at the reduced
 * principal. Those rows are not fully waived, and the penalty job is not asked to create replacements. When the close
 * amount cannot be computed, this returns false and the caller keeps the existing waive.
 */
public final class BackdatedSettlementLpiPolicy {

    private static final BigDecimal CLOSE_TOLERANCE = new BigDecimal("0.01");

    private BackdatedSettlementLpiPolicy() {}

    public static boolean isPartialBackdatedRepayment(final Loan loan, final LocalDate settlementDate, final BigDecimal transactionAmount) {
        return keepsLaterLpi(loan, settlementDate, transactionAmount, false);
    }

    /**
     * @param onInstallmentDueDate
     *            true when the value date is an installment due date, including a payment recorded today
     */
    public static boolean keepsLaterLpi(final Loan loan, final LocalDate settlementDate, final BigDecimal transactionAmount,
            final boolean onInstallmentDueDate) {
        if (loan == null || settlementDate == null || transactionAmount == null || loan.getCurrency() == null) {
            return false;
        }
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        if (businessDate == null) {
            return false;
        }
        final boolean waiveWouldApply = settlementDate.isBefore(businessDate) || onInstallmentDueDate;
        if (!waiveWouldApply) {
            return false;
        }
        final Money required = amountRequiredToClose(loan, settlementDate);
        if (required == null || !required.isGreaterThanZero()) {
            return false;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        final Money amount = Money.of(currency, transactionAmount).plus(Money.of(currency, CLOSE_TOLERANCE));
        return amount.isLessThan(required);
    }

    /**
     * Principal + interest + fees + tax still outstanding, plus LPI dated strictly before {@code settlementDate}. LPI
     * on or after that date is not part of the close amount — a full payment waives it.
     */
    static Money amountRequiredToClose(final Loan loan, final LocalDate settlementDate) {
        if (loan == null || loan.getCurrency() == null || loan.getSummary() == null) {
            return null;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        final LoanSummary summary = loan.getSummary();
        Money required = Money.of(currency, zero(summary.getTotalPrincipalOutstanding()));
        required = required.plus(zero(summary.getTotalInterestOutstanding()));
        required = required.plus(zero(summary.getTotalFeeChargesOutstanding()));
        required = required.plus(zero(summary.getTotalTaxChargesOutstanding()));
        if (loan.getActiveCharges() != null && settlementDate != null) {
            required = required.plus(
                    ForeclosurePenaltyCalculator.computePenaltyQuotedForSettlementDate(loan, settlementDate, currency, false));
        }
        return required;
    }

    private static BigDecimal zero(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
