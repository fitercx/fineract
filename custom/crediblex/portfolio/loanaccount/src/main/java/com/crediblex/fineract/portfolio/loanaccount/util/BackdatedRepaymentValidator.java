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
import java.util.List;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

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

    /*
     * How many days before the business date an operator may backdate is administrator-configurable per tenant - see
     * BackdateWindowSettings. Investigation confirmed core Fineract's own "cannot be before the last transaction date"
     * guard (`LoanTransactionValidator#validateActivityNotBeforeLastTransactionDate`) is gated behind
     * interest-recalculation being enabled and is therefore a no-op for every real prod product, so - unlike
     * foreclosure, which core Fineract already unconditionally blocks before the loan's last non-waiver transaction
     * date via {@code LoanForeclosureValidator} - repayments/transfers had NO floor at all before this guard existed.
     */

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
        final Integer maxBackdateDays = BackdateWindowSettings.maxBackdateDays();
        final LocalDate earliestAllowed = computeEarliestAllowedTransactionDate(loan, maxBackdateDays);
        if (transactionDate != null && DateUtils.isBefore(transactionDate, earliestAllowed)) {
            final String limitRule = maxBackdateDays == null
                    ? "Backdating is only allowed back to the start of the loan's first instalment period"
                    : "Backdating is only allowed up to " + maxBackdateDays
                            + " days before today (or the loan's disbursement date, whichever is later)";
            throw new GeneralPlatformDomainRuleException("error.msg.loan.backdate.limit.exceeded", "The " + actionLabel + " date ("
                    + transactionDate + ") for loan " + loan.getId() + " is too far in the past. " + limitRule
                    + " - the earliest allowed date for this loan right now is " + earliestAllowed
                    + ". This limit protects the repayment schedule and balances from being distorted by very old backdated entries.",
                    loan.getId(), transactionDate, earliestAllowed);
        }
    }

    /**
     * The earliest transaction date this loan may currently be backdated to. Recomputed fresh on every call so it
     * always reflects both the current business date and the current {@link BackdateWindowSettings} configuration -
     * callers should not cache the result across requests.
     * <p>
     * With a configured day limit this is that many days before the business date, or the loan's disbursement date if
     * that is later. With the limit switched off it is the start of the loan's first instalment period, so long-lived
     * loans remain settleable - still never earlier than disbursement, since a loan can have no transaction before it
     * was disbursed.
     */
    public static LocalDate computeEarliestAllowedTransactionDate(final Loan loan) {
        return computeEarliestAllowedTransactionDate(loan, BackdateWindowSettings.maxBackdateDays());
    }

    /**
     * @param maxBackdateDays
     *            days before the business date the transaction may be dated, or {@code null} to allow backdating to the
     *            start of the loan's first instalment period. Visible for testing so both windows can be asserted
     *            without a database.
     */
    static LocalDate computeEarliestAllowedTransactionDate(final Loan loan, final Integer maxBackdateDays) {
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        final LocalDate disbursementDate = loan.getDisbursementDate();
        if (maxBackdateDays == null) {
            final LocalDate loanStartDate = computeLoanStartDate(loan, disbursementDate);
            // A loan with neither a schedule nor a disbursement date cannot be repaid at all; fall back to the default
            // window rather than returning null, which every caller would have to guard against.
            return loanStartDate != null ? loanStartDate : businessDate.minusDays(BackdateWindowSettings.DEFAULT_MAX_BACKDATE_DAYS);
        }
        final LocalDate dayLimitFloor = businessDate.minusDays(maxBackdateDays);
        return disbursementDate != null && DateUtils.isAfter(disbursementDate, dayLimitFloor) ? disbursementDate : dayLimitFloor;
    }

    /**
     * Start of the loan's first instalment period - the "start of EMI" date an operator sees on the repayment schedule.
     * Falls back to the disbursement date when the schedule has not been generated, and is clamped to never precede
     * disbursement.
     */
    private static LocalDate computeLoanStartDate(final Loan loan, final LocalDate disbursementDate) {
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        LocalDate loanStartDate = disbursementDate;
        if (installments != null && !installments.isEmpty()) {
            final LocalDate firstPeriodStart = installments.get(0).getFromDate();
            if (firstPeriodStart != null) {
                loanStartDate = firstPeriodStart;
            }
        }
        if (loanStartDate != null && disbursementDate != null && DateUtils.isBefore(loanStartDate, disbursementDate)) {
            return disbursementDate;
        }
        return loanStartDate;
    }
}
