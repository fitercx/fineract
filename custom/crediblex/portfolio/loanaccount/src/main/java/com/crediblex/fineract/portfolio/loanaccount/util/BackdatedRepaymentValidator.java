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
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;

/**
 * Single source of truth for whether a BACKDATED (value date in the past) loan repayment may be recorded, regardless of
 * which entry point money reaches the loan through - a direct repayment or a savings-to-loan "Transfer funds"
 * settlement should both be governed by the exact same rule.
 * <p>
 * Two independent guards are applied:
 * <ol>
 * <li>{@link #validateBackdatedRepaymentAllowed}: for interest-recalculation-enabled loan products, the interest
 * schedule is recomputed from the payment date forward, so backdating would retroactively ripple into later
 * installments; such repayments are rejected outright. Non interest-recalculation products (every real production loan
 * product today - RBF, Payables Facility, Receivables Facility, Short Term Loan) keep a fixed per-period interest on
 * the schedule, so only the LPI needs adjusting and backdating is allowed - this check is therefore a safe no-op for
 * all of them and only guards the (currently unused in prod) interest-recalculation configuration.</li>
 * <li>{@link #validateWithinBackdateLimit}: a general "how far back is too far back" guard, applied regardless of
 * product configuration - see its javadoc.</li>
 * </ol>
 * See BUG_REPORT.md Finding #1 (backdated transfer bypassed the interest-recalculation check) and the "Backdate limit"
 * finding (calendar previously allowed picking any date back to year 2000 with no floor at all).
 */
public final class BackdatedRepaymentValidator {

    /**
     * How many days before the business date an operator may backdate a repayment, transfer, or foreclosure. Chosen as
     * a static, hardcoded business-policy cap (not admin-configurable) - see BUG_REPORT.md "Backdate limit" finding.
     * Investigation confirmed core Fineract's own "cannot be before the last transaction date" guard
     * (`LoanTransactionValidator#validateActivityNotBeforeLastTransactionDate`) is gated behind interest-recalculation
     * being enabled and is therefore a no-op for every real prod product, so - unlike foreclosure, which core Fineract
     * already unconditionally blocks before the loan's last non-waiver transaction date via
     * {@code LoanForeclosureValidator} - repayments/transfers had NO floor at all before this change.
     */
    public static final int MAX_BACKDATE_DAYS = 30;

    private BackdatedRepaymentValidator() {}

    public static void validateBackdatedRepaymentAllowed(final Loan loan, final LocalDate transactionDate) {
        if (loan.isInterestBearingAndInterestRecalculationEnabled()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.backdated.repayment.not.allowed.interest.recalculation",
                    "Backdated repayment is not allowed for loan " + loan.getId()
                            + " because interest recalculation is enabled on the loan product. Please record the repayment with today's date.",
                    loan.getId(), transactionDate);
        }
        validateWithinBackdateLimit(loan, transactionDate, "repayment");
    }

    /**
     * Rejects a transaction date that is further in the past than {@link #computeEarliestAllowedTransactionDate}
     * allows, regardless of product/interest-recalculation configuration. Applies to every loan product (including
     * RBF/Payables Facility/Receivables Facility/Short Term Loan) since, unlike the interest-recalculation check above,
     * this is a general operational-risk guard, not a mechanical-correctness one: the further back a
     * repayment/transfer/foreclosure is dated, the more already-recorded transactions between that date and today get
     * silently reprocessed/reallocated, which is a real (if today mechanically self-consistent) operational risk for a
     * human operator to reason about and audit.
     */
    public static void validateWithinBackdateLimit(final Loan loan, final LocalDate transactionDate, final String actionLabel) {
        final LocalDate earliestAllowed = computeEarliestAllowedTransactionDate(loan);
        if (transactionDate != null && DateUtils.isBefore(transactionDate, earliestAllowed)) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.backdate.limit.exceeded", "The " + actionLabel + " date ("
                    + transactionDate + ") for loan " + loan.getId() + " is too far in the past. " + "Backdating is only allowed up to "
                    + MAX_BACKDATE_DAYS
                    + " days before today (or the loan's disbursement date, whichever is later) - the earliest allowed date "
                    + "for this loan right now is " + earliestAllowed
                    + ". This limit protects the repayment schedule and balances from being distorted by very old backdated entries.",
                    loan.getId(), transactionDate, earliestAllowed);
        }
    }

    /**
     * The earliest transaction date this loan may currently be backdated to: {@link #MAX_BACKDATE_DAYS} days before the
     * business date, or the loan's disbursement date if that is later (a loan can never have a transaction before it
     * was disbursed - enforced independently elsewhere, but folded in here too so the UI's calendar minDate is always
     * at least as tight as every other backend rule). Recomputed fresh on every call so it always reflects the current
     * business date - callers should not cache the result across requests.
     */
    public static LocalDate computeEarliestAllowedTransactionDate(final Loan loan) {
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        final LocalDate staticFloor = businessDate.minusDays(MAX_BACKDATE_DAYS);
        final LocalDate disbursementDate = loan.getDisbursementDate();
        return disbursementDate != null && DateUtils.isAfter(disbursementDate, staticFloor) ? disbursementDate : staticFloor;
    }
}
