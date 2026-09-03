/*
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
package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.TargetedLoanChargeRefundHook;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.TargetedLoanChargeRefundHookRegistry;
import org.springframework.stereotype.Component;

/**
 * CredibleX paid-charge refund behavior used by the paid LPI reversal workflow.
 * <p>
 * A paid LPI refund is an exact undo of the charge payment: the linked charge becomes outstanding again, the original
 * installment's penalty/fee paid and late-paid totals decrease, and principal and interest remain unchanged. The
 * linked-savings credit is created by the surrounding CredibleX charge service, not by this processor.
 * <p>
 * This processor is deliberately replay-safe. It is invoked when the refund is first posted and whenever loan history
 * is reprocessed by a later repayment or foreclosure workflow. Reapplying the same targeted allocation prevents the
 * generic active-loan refund waterfall from unpaying principal or interest and prevents foreclosure amounts from being
 * calculated from a distorted schedule.
 * <p>
 * {@link #isTargetedChargeRefund(LoanTransaction)} is the workflow boundary. Transactions without both the explicit
 * fee/penalty refund marker and charge-allocation links are rejected so normal repayment, refund, reprocessing, and
 * foreclosure behavior falls back to core Fineract unchanged. New refund cases with different accounting or allocation
 * rules should be implemented as a separate method or hook implementation instead of broadening this predicate.
 */
@Component
public final class CredXTargetedLoanChargeRefundProcessor implements TargetedLoanChargeRefundHook {

    public static final String PENALTY = "P";
    public static final String FEE = "F";

    @PostConstruct
    public void register() {
        TargetedLoanChargeRefundHookRegistry.register(this);
    }

    @Override
    public boolean applyIfSupported(final LoanTransaction transaction, final MonetaryCurrency currency,
            final List<LoanRepaymentScheduleInstallment> installments) {
        if (!isTargetedChargeRefund(transaction)) {
            return false;
        }
        process(transaction, currency, installments);
        return true;
    }

    /**
     * Identifies only CredibleX paid-charge refunds that carry the persisted metadata required for deterministic
     * replay.
     */
    public static boolean isTargetedChargeRefund(final LoanTransaction transaction) {
        if (transaction == null || !transaction.isRefundForActiveLoan()) {
            return false;
        }
        final String chargeType = transaction.getChargeRefundChargeType();
        final Set<LoanChargePaidBy> chargesPaid = transaction.getLoanChargesPaid();
        return (PENALTY.equalsIgnoreCase(chargeType) || FEE.equalsIgnoreCase(chargeType)) && chargesPaid != null && !chargesPaid.isEmpty();
    }

    /**
     * Restores the fee/penalty allocation recorded on the refund transaction without changing principal or interest.
     * This method is shared by initial LPI refund processing and repayment/foreclosure transaction replay.
     */
    public static Money process(final LoanTransaction transaction, final MonetaryCurrency currency,
            final List<LoanRepaymentScheduleInstallment> installments) {
        final Money zero = Money.zero(currency);
        if (!isTargetedChargeRefund(transaction)) {
            return zero;
        }

        Money remaining = transaction.getAmount(currency);
        Money penaltyRefunded = zero;
        Money feeRefunded = zero;
        final List<LoanTransactionToRepaymentScheduleMapping> mappings = new ArrayList<>();

        transaction.resetDerivedComponents();
        for (final LoanChargePaidBy paidBy : transaction.getLoanChargesPaid()) {
            if (remaining.isZero() || paidBy == null || paidBy.getLoanCharge() == null) {
                continue;
            }
            final LoanCharge charge = paidBy.getLoanCharge();
            final LoanRepaymentScheduleInstallment installment = findInstallment(installments, paidBy.getInstallmentNumber());
            if (installment == null) {
                continue;
            }

            final BigDecimal linkedAmount = paidBy.getAmount() == null ? BigDecimal.ZERO : paidBy.getAmount().abs();
            Money amountToUndo = min(remaining, Money.of(currency, linkedAmount));
            amountToUndo = min(amountToUndo, charge.getAmountPaid(currency));
            amountToUndo = min(amountToUndo,
                    charge.isPenaltyCharge() ? installment.getPenaltyChargesPaid(currency) : installment.getFeeChargesPaid(currency));
            if (!amountToUndo.isGreaterThanZero()) {
                continue;
            }

            final Money chargeAmountUndone = charge.undoPaidOrPartiallyAmountBy(amountToUndo, paidBy.getInstallmentNumber(), zero);
            final Money installmentAmountUndone = charge.isPenaltyCharge()
                    ? installment.unpayPenaltyChargesComponent(transaction.getTransactionDate(), chargeAmountUndone)
                    : installment.unpayFeeChargesComponent(transaction.getTransactionDate(), chargeAmountUndone);
            final Money processed = min(chargeAmountUndone, installmentAmountUndone);
            if (!processed.isGreaterThanZero()) {
                continue;
            }

            final Money penaltyPortion = charge.isPenaltyCharge() ? processed : zero;
            final Money feePortion = charge.isPenaltyCharge() ? zero : processed;
            penaltyRefunded = penaltyRefunded.plus(penaltyPortion);
            feeRefunded = feeRefunded.plus(feePortion);
            remaining = remaining.minus(processed);
            mappings.add(
                    LoanTransactionToRepaymentScheduleMapping.createFrom(transaction, installment, zero, zero, feePortion, penaltyPortion));
        }

        transaction.updateComponents(zero, zero, feeRefunded, penaltyRefunded);
        transaction.updateLoanTransactionToRepaymentScheduleMappings(mappings);
        return penaltyRefunded.plus(feeRefunded);
    }

    private static LoanRepaymentScheduleInstallment findInstallment(final List<LoanRepaymentScheduleInstallment> installments,
            final Integer installmentNumber) {
        if (installments == null || installmentNumber == null) {
            return null;
        }
        return installments.stream().filter(installment -> installmentNumber.equals(installment.getInstallmentNumber())).findFirst()
                .orElse(null);
    }

    private static Money min(final Money left, final Money right) {
        return left.isGreaterThan(right) ? right : left;
    }
}
