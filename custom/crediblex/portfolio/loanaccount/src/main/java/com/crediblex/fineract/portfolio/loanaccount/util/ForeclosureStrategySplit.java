package com.crediblex.fineract.portfolio.loanaccount.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.FineractStyleLoanRepaymentScheduleTransactionProcessor;

/**
 * Splits a foreclosure settlement the way {@code mifos-standard-strategy} will post it: on each open installment,
 * penalties, then fees, then tax, then interest, then principal.
 * <p>
 * The foreclosure quote otherwise shows charge-level LPI and the full principal outstanding. The strategy pays the
 * schedule penalty on an open installment first, so the same cash is posted with more LPI and less principal.
 */
public final class ForeclosureStrategySplit {

    private ForeclosureStrategySplit() {}

    public record Split(Money principal, Money interest, Money fee, Money penalty, Money tax) {
    }

    public static Split allocate(final Loan loan, final LocalDate foreclosureDate, final Money cash, final Money quotedPrincipal,
            final Money quotedInterest, final Money quotedFee, final Money quotedPenalty, final Money quotedTax) {
        if (!usesStandardOrder(loan) || foreclosureDate == null || cash == null || loan.getRepaymentScheduleInstallments() == null) {
            return quoted(quotedPrincipal, quotedInterest, quotedFee, quotedPenalty, quotedTax);
        }
        final MonetaryCurrency currency = loan.getCurrency();
        final Money zero = Money.zero(currency);
        final List<Bucket> buckets = buckets(loan, foreclosureDate, currency, zero, orZero(quotedInterest, zero), orZero(quotedFee, zero),
                orZero(quotedPenalty, zero), orZero(quotedTax, zero));
        if (buckets.isEmpty()) {
            return quoted(quotedPrincipal, quotedInterest, quotedFee, quotedPenalty, quotedTax);
        }

        Money remaining = cash;
        Money principal = zero;
        Money interest = zero;
        Money fee = zero;
        Money penalty = zero;
        Money tax = zero;
        for (final Bucket bucket : buckets) {
            final Money penaltyTake = take(remaining, bucket.penalty);
            penalty = penalty.plus(penaltyTake);
            remaining = remaining.minus(penaltyTake);
            final Money feeTake = take(remaining, bucket.fee);
            fee = fee.plus(feeTake);
            remaining = remaining.minus(feeTake);
            final Money taxTake = take(remaining, bucket.tax);
            tax = tax.plus(taxTake);
            remaining = remaining.minus(taxTake);
            final Money interestTake = take(remaining, bucket.interest);
            interest = interest.plus(interestTake);
            remaining = remaining.minus(interestTake);
            final Money principalTake = take(remaining, bucket.principal);
            principal = principal.plus(principalTake);
            remaining = remaining.minus(principalTake);
        }
        if (remaining.isGreaterThanZero()) {
            principal = principal.plus(remaining);
        }
        return new Split(principal, interest, fee, penalty, tax);
    }

    private static boolean usesStandardOrder(final Loan loan) {
        return loan != null
                && FineractStyleLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE.equals(loan.transactionProcessingStrategy());
    }

    private static List<Bucket> buckets(final Loan loan, final LocalDate foreclosureDate, final MonetaryCurrency currency, final Money zero,
            final Money quotedInterest, final Money quotedFee, final Money quotedPenalty, final Money quotedTax) {
        final List<Bucket> dueBuckets = new ArrayList<>();
        LoanRepaymentScheduleInstallment straddling = null;
        Money pastPenalty = zero;
        Money pastFee = zero;
        Money pastTax = zero;
        Money pastInterest = zero;
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (installment == null || installment.isObligationsMet() || installment.isDownPayment() || installment.getDueDate() == null) {
                continue;
            }
            if (!DateUtils.isBefore(foreclosureDate, installment.getDueDate())) {
                final Bucket bucket = outstanding(installment, currency, zero);
                dueBuckets.add(bucket);
                pastPenalty = pastPenalty.plus(bucket.penalty);
                pastFee = pastFee.plus(bucket.fee);
                pastTax = pastTax.plus(bucket.tax);
                pastInterest = pastInterest.plus(bucket.interest);
            } else if (DateUtils.isDateInRangeFromInclusiveToExclusive(foreclosureDate, installment.getFromDate(),
                    installment.getDueDate())) {
                straddling = installment;
            }
        }
        if (straddling != null) {
            dueBuckets.add(straddlingBucket(straddling, currency, zero, quotedInterest.minus(pastInterest), quotedFee.minus(pastFee),
                    quotedPenalty.minus(pastPenalty), quotedTax.minus(pastTax)));
        }
        return dueBuckets;
    }

    private static Bucket straddlingBucket(final LoanRepaymentScheduleInstallment installment, final MonetaryCurrency currency,
            final Money zero, final Money interestRoom, final Money feeRoom, final Money penaltyRoom, final Money taxRoom) {
        final Bucket outstanding = outstanding(installment, currency, zero);
        return new Bucket(cap(outstanding.penalty, penaltyRoom, zero), cap(outstanding.fee, feeRoom, zero),
                cap(outstanding.tax, taxRoom, zero), cap(outstanding.interest, interestRoom, zero), outstanding.principal);
    }

    private static Money cap(final Money outstanding, final Money room, final Money zero) {
        final Money boundedRoom = room == null || room.isLessThanZero() ? zero : room;
        if (outstanding.isGreaterThan(boundedRoom)) {
            return boundedRoom;
        }
        return outstanding;
    }

    private static Bucket outstanding(final LoanRepaymentScheduleInstallment installment, final MonetaryCurrency currency,
            final Money zero) {
        return new Bucket(positive(installment.getPenaltyChargesOutstanding(currency), zero),
                positive(installment.getFeeChargesOutstanding(currency), zero),
                positive(installment.getTaxChargesOutstanding(currency), zero),
                positive(installment.getInterestOutstanding(currency), zero),
                positive(installment.getPrincipalOutstanding(currency), zero));
    }

    private static Money positive(final Money value, final Money zero) {
        if (value == null || !value.isGreaterThanZero()) {
            return zero;
        }
        return value;
    }

    private static Money take(final Money remaining, final Money available) {
        if (remaining == null || available == null || !remaining.isGreaterThanZero() || !available.isGreaterThanZero()) {
            return remaining == null ? available : remaining.zero();
        }
        return remaining.isGreaterThan(available) ? available : remaining;
    }

    private static Money orZero(final Money value, final Money zero) {
        return value == null ? zero : value;
    }

    private static Split quoted(final Money principal, final Money interest, final Money fee, final Money penalty, final Money tax) {
        return new Split(principal, interest, fee, penalty, tax);
    }

    private record Bucket(Money penalty, Money fee, Money tax, Money interest, Money principal) {
    }
}
