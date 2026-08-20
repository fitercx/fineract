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

import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * Guarantees a foreclosure settlement never exceeds what the loan can actually absorb, so the loan closes as <em>Closed
 * (obligations met)</em> instead of <em>Overpaid</em> and no surplus is over-withdrawn from the linked savings account.
 * <p>
 * <b>Why this is needed.</b> The foreclosure amount in {@code CustomLoanAccountDomainServiceJpa#foreCloseLoan} is
 * assembled up-front from a mix of sources that do not all line up with the (possibly rewritten) repayment schedule the
 * settlement is finally allocated against:
 * <ul>
 * <li><b>Factor Rate</b> products take fees/taxes from the loan summary
 * ({@code LoanSummary#getTotalFeeChargesOutstanding()}), which can exceed the fee still allocatable on the schedule
 * &rarr; the surplus fee is booked as overpayment (observed on UAT loans 15109 &rarr; 1,320 and 11188 &rarr;
 * 10,500).</li>
 * <li><b>LPI / penalty</b> is summed from active charges ({@link ForeclosurePenaltyCalculator}); a charge whose owning
 * installment is already complete (or is deactivated by the {@code updateInstallmentsPostDate} schedule rewrite) has no
 * open installment bucket to receive the payment &rarr; the penalty is over-collected as overpayment (observed on UAT
 * RBF loan 15628 &rarr; 91.75).</li>
 * </ul>
 * The transaction processor books exactly {@code rawAmount - Σ(installment.totalOutstanding)} as overpayment, so
 * clamping the settlement total to {@link #allocatableOutstanding(Loan, MonetaryCurrency)} drives the overpayment to
 * zero for <em>every</em> product without changing any correctly-priced settlement (when the raw amount already fits
 * within the allocatable outstanding this reconciler is a no-op).
 * <p>
 * <b>Where the excess is absorbed.</b> Only non-principal components are reduced, in reverse waterfall order (penalty
 * &rarr; tax &rarr; fee &rarr; interest); principal is never touched, so the loan still settles in full and closes
 * cleanly. The reduced amount is the LPI/unearned-fee that early foreclosure is meant to waive anyway; the caller logs
 * it and records it on the audit note. Any residual active charge left on a completed installment is then cleared by
 * {@code LoanChargeSettlementUtils#closeIfFullySettled}.
 * <p>
 * This must be invoked at the point the settlement amount is built (i.e. AFTER {@code updateInstallmentsPostDate} has
 * rewritten the schedule for non-Factor-Rate loans), so {@link #allocatableOutstanding} reflects the exact buckets the
 * processor will allocate against.
 */
public final class ForeclosureAmountReconciler {

    private ForeclosureAmountReconciler() {}

    /**
     * Result of clamping the raw foreclosure components to the loan's allocatable outstanding. When nothing needed
     * clamping, the original components are returned and {@link #waived()} is zero.
     */
    public record Result(Money principal, Money interest, Money fee, Money penalty, Money tax, Money waived) {

        public Money total() {
            return principal.plus(interest).plus(fee).plus(penalty).plus(tax);
        }

        public boolean wasReduced() {
            return waived.isGreaterThanZero();
        }
    }

    /**
     * Sum of {@code getTotalOutstanding()} across every current repayment-schedule installment. This is precisely what
     * the waterfall transaction processor can allocate a foreclosure repayment to; anything paid beyond it becomes
     * overpayment.
     */
    public static Money allocatableOutstanding(final Loan loan, final MonetaryCurrency currency) {
        Money total = Money.zero(currency);
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            total = total.plus(installment.getTotalOutstanding(currency));
        }
        return total;
    }

    /**
     * Clamps the raw foreclosure components so their total never exceeds {@link #allocatableOutstanding}. Principal is
     * preserved; the excess is taken from penalty, then tax, then fee, then interest.
     */
    public static Result reconcile(final Loan loan, final MonetaryCurrency currency, final Money principal, final Money interest,
            final Money fee, final Money penalty, final Money tax) {
        final Money zero = Money.zero(currency);
        final Money rawTotal = principal.plus(interest).plus(fee).plus(penalty).plus(tax);
        final Money cap = allocatableOutstanding(loan, currency);

        // Fail open: if the schedule reports no allocatable outstanding (e.g. installments not loaded, or a
        // post-maturity all-paid loan carrying only a residual loan-level charge), do NOT trim - clamping to zero here
        // would wrongly wipe a legitimate settlement. Overpayment can only arise when there IS a positive allocatable
        // amount to compare against, and the execution path re-checks with a fully-loaded schedule.
        if (!cap.isGreaterThanZero() || !rawTotal.isGreaterThan(cap)) {
            return new Result(principal, interest, fee, penalty, tax, zero);
        }

        Money excess = rawTotal.minus(cap);
        final Money waived = excess;

        // Reduce lowest-priority components first; never reduce principal (the loan must settle in full to close).
        final Reduction penaltyReduction = reduce(penalty, excess);
        excess = penaltyReduction.remainingExcess();
        final Reduction taxReduction = reduce(tax, excess);
        excess = taxReduction.remainingExcess();
        final Reduction feeReduction = reduce(fee, excess);
        excess = feeReduction.remainingExcess();
        final Reduction interestReduction = reduce(interest, excess);
        excess = interestReduction.remainingExcess();

        return new Result(principal, interestReduction.remaining(), feeReduction.remaining(), penaltyReduction.remaining(),
                taxReduction.remaining(), waived.minus(excess));
    }

    private static Reduction reduce(final Money component, final Money excess) {
        if (!excess.isGreaterThanZero() || !component.isGreaterThanZero()) {
            return new Reduction(component, excess);
        }
        if (component.isGreaterThan(excess)) {
            return new Reduction(component.minus(excess), Money.zero(excess.getCurrency()));
        }
        return new Reduction(Money.zero(component.getCurrency()), excess.minus(component));
    }

    private record Reduction(Money remaining, Money remainingExcess) {
    }
}
