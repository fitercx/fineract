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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;

/**
 * Targeted LPI / paid-charge undo: move the freed penalty (or fee) onto principal (then interest) on the installment(s)
 * and repayment(s) that actually paid the charge.
 *
 * <p>
 * Must not call {@code reprocessTransactions}. A full-history replay recasts every repayment whose stored P/I/penalty
 * split no longer matches the current schedule — that is what distorted Cloud Fifty One prod loan 1 (8 historical
 * repayments reversed + recast, Jul leftover dumped as advance on Aug EMI).
 *
 * <p>
 * Keeps LMS-107 Model A: on a partially-paid loan the freed amount reduces outstanding (no phantom overpayment, no
 * savings refund). On a fully-repaid loan leftover cash is a genuine overpayment and is refunded by the caller.
 */
@Slf4j
public final class ReversePaidChargeReallocator {

    private ReversePaidChargeReallocator() {}

    public static void reallocate(final Loan loan, final LoanCharge loanCharge, final BigDecimal totalAmountPaid,
            final LocalDate reversalDate) {
        if (loan == null || loanCharge == null || totalAmountPaid == null || totalAmountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        if (currency == null) {
            return;
        }
        final Money freed = Money.of(currency, totalAmountPaid);
        final boolean penalty = loanCharge.isPenaltyCharge();

        unpayChargeAndDropDue(loan, loanCharge, freed, reversalDate, penalty);
        final Money leftover = applyFreedAmountToOutstanding(loan, freed, reversalDate);
        moveChargePortionToPrincipalOnPayingTransactions(loanCharge, freed, currency, penalty);

        if (leftover.isGreaterThanZero()) {
            log.info(
                    "Reversed charge {} on loan {}: {} could not be absorbed by outstanding principal/interest and remains a genuine overpayment",
                    loanCharge.getId(), loan.getId(), leftover.getAmount());
        } else {
            log.info("Reversed charge {} on loan {}: {} re-applied to outstanding (no full-history reprocess)", loanCharge.getId(),
                    loan.getId(), totalAmountPaid);
        }
    }

    private static void unpayChargeAndDropDue(final Loan loan, final LoanCharge loanCharge, final Money freed, final LocalDate reversalDate,
            final boolean penalty) {
        Money remaining = freed;
        for (final LoanRepaymentScheduleInstallment installment : installmentsForCharge(loan, loanCharge)) {
            if (remaining.isZero()) {
                break;
            }
            final Money unpaid = penalty ? installment.unpayPenaltyChargesComponent(reversalDate, remaining)
                    : installment.unpayFeeChargesComponent(reversalDate, remaining);
            if (unpaid != null && unpaid.isGreaterThanZero()) {
                final Money zero = Money.zero(remaining.getCurrency());
                if (penalty) {
                    installment.addToChargePortion(zero, zero, zero, zero, zero, zero, unpaid.negated(), zero, zero);
                } else {
                    installment.addToChargePortion(unpaid.negated(), zero, zero, zero, zero, zero, zero, zero, zero);
                }
                remaining = remaining.minus(unpaid);
            }
        }
    }

    private static Money applyFreedAmountToOutstanding(final Loan loan, final Money freed, final LocalDate reversalDate) {
        Money remaining = freed;
        final List<LoanRepaymentScheduleInstallment> installments = sortedInstallments(loan);
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (remaining.isZero()) {
                break;
            }
            remaining = remaining.minus(installment.payPrincipalComponent(reversalDate, remaining));
        }
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (remaining.isZero()) {
                break;
            }
            remaining = remaining.minus(installment.payInterestComponent(reversalDate, remaining));
        }
        return remaining;
    }

    private static void moveChargePortionToPrincipalOnPayingTransactions(final LoanCharge loanCharge, final Money freed,
            final MonetaryCurrency currency, final boolean penalty) {
        final Set<LoanChargePaidBy> paidBySet = loanCharge.getLoanChargePaidBySet();
        if (paidBySet == null || paidBySet.isEmpty()) {
            return;
        }
        Money remaining = freed;
        for (final LoanChargePaidBy paidBy : paidBySet) {
            if (remaining.isZero()) {
                break;
            }
            final LoanTransaction txn = paidBy.getLoanTransaction();
            if (txn == null || txn.isReversed() || !txn.isRepaymentLikeType()) {
                continue;
            }
            final Money chargeOnTxn = penalty ? txn.getPenaltyChargesPortion(currency) : txn.getFeeChargesPortion(currency);
            if (chargeOnTxn == null || !chargeOnTxn.isGreaterThanZero()) {
                continue;
            }
            final Money paidByAmount = paidBy.getAmount() != null ? Money.of(currency, paidBy.getAmount()) : chargeOnTxn;
            final Money move = min(remaining, min(chargeOnTxn, paidByAmount));
            if (!move.isGreaterThanZero()) {
                continue;
            }
            if (penalty) {
                txn.updateComponents(move, Money.zero(currency), Money.zero(currency), move.negated());
            } else {
                txn.updateComponents(move, Money.zero(currency), move.negated(), Money.zero(currency));
            }
            shiftMappingsChargeToPrincipal(txn, move, currency, penalty);
            paidBy.setAmount(BigDecimal.ZERO);
            remaining = remaining.minus(move);
        }
    }

    private static void shiftMappingsChargeToPrincipal(final LoanTransaction txn, final Money move, final MonetaryCurrency currency,
            final boolean penalty) {
        final Set<LoanTransactionToRepaymentScheduleMapping> mappings = txn.getLoanTransactionToRepaymentScheduleMappings();
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        Money remaining = move;
        for (final LoanTransactionToRepaymentScheduleMapping mapping : mappings) {
            if (remaining.isZero()) {
                break;
            }
            final Money chargeOnMap = penalty ? mapping.getPenaltyChargesPortion(currency) : mapping.getFeeChargesPortion(currency);
            if (chargeOnMap == null || !chargeOnMap.isGreaterThanZero()) {
                continue;
            }
            final Money take = min(remaining, chargeOnMap);
            if (penalty) {
                mapping.updateComponents(take, Money.zero(currency), Money.zero(currency), take.negated());
            } else {
                mapping.updateComponents(take, Money.zero(currency), take.negated(), Money.zero(currency));
            }
            remaining = remaining.minus(take);
        }
    }

    /**
     * Only the installment(s) that actually carried this charge. Do not walk the rest of the schedule — that would
     * unpay sibling LPI / fees on other EMIs (waive, due-date auto-waive, and SI allocations must keep those).
     */
    private static List<LoanRepaymentScheduleInstallment> installmentsForCharge(final Loan loan, final LoanCharge loanCharge) {
        final List<LoanRepaymentScheduleInstallment> preferred = new ArrayList<>();
        final LoanOverdueInstallmentCharge overdue = loanCharge.getOverdueInstallmentCharge();
        if (overdue != null && overdue.getInstallment() != null) {
            preferred.add(overdue.getInstallment());
        }
        final Set<LoanChargePaidBy> paidBySet = loanCharge.getLoanChargePaidBySet();
        if (paidBySet != null) {
            for (final LoanChargePaidBy paidBy : paidBySet) {
                if (paidBy == null || paidBy.getInstallmentNumber() == null) {
                    continue;
                }
                for (final LoanRepaymentScheduleInstallment installment : sortedInstallments(loan)) {
                    if (paidBy.getInstallmentNumber().equals(installment.getInstallmentNumber()) && !preferred.contains(installment)) {
                        preferred.add(installment);
                    }
                }
            }
        }
        if (!preferred.isEmpty()) {
            return preferred;
        }
        // Paid-by has no installment number (older rows): use only mappings on the repayment(s) that paid
        // this charge — never the rest of the schedule.
        if (paidBySet != null) {
            for (final LoanChargePaidBy paidBy : paidBySet) {
                final LoanTransaction txn = paidBy == null ? null : paidBy.getLoanTransaction();
                if (txn == null || txn.isReversed() || !txn.isRepaymentLikeType()
                        || txn.getLoanTransactionToRepaymentScheduleMappings() == null) {
                    continue;
                }
                for (final LoanTransactionToRepaymentScheduleMapping mapping : txn.getLoanTransactionToRepaymentScheduleMappings()) {
                    if (mapping.getLoanRepaymentScheduleInstallment() != null
                            && !preferred.contains(mapping.getLoanRepaymentScheduleInstallment())) {
                        preferred.add(mapping.getLoanRepaymentScheduleInstallment());
                    }
                }
            }
        }
        return preferred;
    }

    private static List<LoanRepaymentScheduleInstallment> sortedInstallments(final Loan loan) {
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        if (installments == null || installments.isEmpty()) {
            return List.of();
        }
        final List<LoanRepaymentScheduleInstallment> sorted = new ArrayList<>(installments);
        sorted.sort(Comparator.comparing(LoanRepaymentScheduleInstallment::getInstallmentNumber, Comparator.nullsLast(Integer::compareTo)));
        return sorted;
    }

    private static Money min(final Money left, final Money right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isGreaterThan(right) ? right : left;
    }
}
