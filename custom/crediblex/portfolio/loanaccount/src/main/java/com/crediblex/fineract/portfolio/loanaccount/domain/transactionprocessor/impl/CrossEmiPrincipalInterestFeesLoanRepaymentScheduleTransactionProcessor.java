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
package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.AbstractLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.LastEmiSpillPenaltyHookRegistry;

/**
 * Allocates a repayment across overdue installments only: all overdue principal, then all overdue interest, then all
 * overdue fees (and tax), then overdue penalties (LPI). Within a bucket the earliest overdue installment is paid first.
 * An installment due today or later is left untouched.
 */
public class CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor
        extends AbstractLoanRepaymentScheduleTransactionProcessor {

    public static final String STRATEGY_CODE = "cross-emi-principal-interest-fees-strategy";
    public static final String STRATEGY_NAME = "Principal, Interest, Fees across all EMIs";

    public CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor(final ExternalIdFactory externalIdFactory) {
        super(externalIdFactory);
    }

    @Override
    public String getCode() {
        return STRATEGY_CODE;
    }

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    protected Money processTransaction(final LoanTransaction loanTransaction, final MonetaryCurrency currency,
            final List<LoanRepaymentScheduleInstallment> installments, final Set<LoanCharge> charges, final Money amountToProcess) {
        if (loanTransaction.isChargesWaiver() || loanTransaction.isInterestWaiver() || loanTransaction.isChargePayment()
                || !(loanTransaction.isRepayment() || loanTransaction.isRecoveryRepayment())) {
            return super.processTransaction(loanTransaction, currency, installments, charges, amountToProcess);
        }

        if (loanTransaction.getLoan() != null && !loanTransaction.getLoan().isForeclosure()) {
            LastEmiSpillPenaltyHookRegistry.foldOntoLastEmi(loanTransaction.getLoan(), loanTransaction.getTransactionDate());
        }

        final LocalDate transactionDate = loanTransaction.getTransactionDate();
        final List<LoanRepaymentScheduleInstallment> ordered = new ArrayList<>();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (isOverdue(installment, transactionDate)) {
                ordered.add(installment);
            }
        }
        ordered.sort((left, right) -> left.getDueDate().compareTo(right.getDueDate()));

        Money remaining = amountToProcess != null ? amountToProcess : loanTransaction.getAmount(currency);
        final Map<LoanRepaymentScheduleInstallment, ComponentPortions> portionsByInstallment = new LinkedHashMap<>();
        remaining = payBucket(loanTransaction, ordered, remaining, portionsByInstallment, Bucket.PRINCIPAL);
        remaining = payBucket(loanTransaction, ordered, remaining, portionsByInstallment, Bucket.INTEREST);
        remaining = payBucket(loanTransaction, ordered, remaining, portionsByInstallment, Bucket.FEE);
        remaining = payBucket(loanTransaction, ordered, remaining, portionsByInstallment, Bucket.TAX);
        remaining = payBucket(loanTransaction, ordered, remaining, portionsByInstallment, Bucket.PENALTY);

        final List<LoanTransactionToRepaymentScheduleMapping> mappings = new ArrayList<>();
        for (final Map.Entry<LoanRepaymentScheduleInstallment, ComponentPortions> entry : portionsByInstallment.entrySet()) {
            final ComponentPortions portions = entry.getValue();
            if (portions.total().isGreaterThanZero()) {
                mappings.add(LoanTransactionToRepaymentScheduleMapping.createFrom(loanTransaction, entry.getKey(), portions.principal,
                        portions.interest, portions.fee, portions.penalty));
            }
        }
        loanTransaction.updateLoanTransactionToRepaymentScheduleMappings(mappings);
        return remaining;
    }

    private Money payBucket(final LoanTransaction loanTransaction, final List<LoanRepaymentScheduleInstallment> installments,
            final Money remainingAmount, final Map<LoanRepaymentScheduleInstallment, ComponentPortions> portionsByInstallment,
            final Bucket bucket) {
        Money remaining = remainingAmount;
        final LocalDate transactionDate = loanTransaction.getTransactionDate();
        final MonetaryCurrency currency = remaining.getCurrency();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (!remaining.isGreaterThanZero() || installment.isDownPayment()) {
                continue;
            }
            final Money paid = switch (bucket) {
                case PRINCIPAL -> installment.payPrincipalComponent(transactionDate, remaining);
                case INTEREST -> installment.payInterestComponent(transactionDate, remaining);
                case FEE -> installment.payFeeChargesComponent(transactionDate, remaining);
                case TAX -> installment.payTaxChargesComponent(transactionDate, remaining);
                case PENALTY -> installment.payPenaltyChargesComponent(transactionDate, remaining);
            };
            if (!paid.isGreaterThanZero()) {
                continue;
            }
            remaining = remaining.minus(paid);
            final ComponentPortions portions = portionsByInstallment.computeIfAbsent(installment,
                    ignored -> new ComponentPortions(currency));
            portions.add(bucket, paid);
            final Money zero = Money.zero(currency);
            switch (bucket) {
                case PRINCIPAL -> loanTransaction.updateComponents(paid, zero, zero, zero);
                case INTEREST -> loanTransaction.updateComponents(zero, paid, zero, zero);
                case FEE -> loanTransaction.updateComponents(zero, zero, paid, zero);
                case TAX -> loanTransaction.updateComponents(zero, zero, zero, zero, paid);
                case PENALTY -> loanTransaction.updateComponents(zero, zero, zero, paid);
            }
        }
        return remaining;
    }

    @Override
    protected Money handleTransactionThatIsPaymentInAdvanceOfInstallment(final LoanRepaymentScheduleInstallment currentInstallment,
            final List<LoanRepaymentScheduleInstallment> installments, final LoanTransaction loanTransaction, final Money paymentInAdvance,
            final List<LoanTransactionToRepaymentScheduleMapping> transactionMappings, final Set<LoanCharge> charges) {
        return handleTransactionThatIsOnTimePaymentOfInstallment(currentInstallment, loanTransaction, paymentInAdvance, transactionMappings,
                charges);
    }

    @Override
    protected Money handleTransactionThatIsALateRepaymentOfInstallment(final LoanRepaymentScheduleInstallment currentInstallment,
            final List<LoanRepaymentScheduleInstallment> installments, final LoanTransaction loanTransaction,
            final Money transactionAmountUnprocessed, final List<LoanTransactionToRepaymentScheduleMapping> transactionMappings,
            final Set<LoanCharge> charges) {
        return handleTransactionThatIsOnTimePaymentOfInstallment(currentInstallment, loanTransaction, transactionAmountUnprocessed,
                transactionMappings, charges);
    }

    /**
     * Charge payments and waivers stay on the installment they are posted against. A normal repayment does not use this
     * method; {@link #processTransaction} allocates it across EMIs.
     */
    @Override
    protected Money handleTransactionThatIsOnTimePaymentOfInstallment(final LoanRepaymentScheduleInstallment currentInstallment,
            final LoanTransaction loanTransaction, final Money transactionAmountUnprocessed,
            final List<LoanTransactionToRepaymentScheduleMapping> transactionMappings, final Set<LoanCharge> charges) {
        final LocalDate transactionDate = loanTransaction.getTransactionDate();
        final MonetaryCurrency currency = transactionAmountUnprocessed.getCurrency();
        Money transactionAmountRemaining = transactionAmountUnprocessed;
        Money principalPortion = Money.zero(currency);
        Money interestPortion = Money.zero(currency);
        Money feeChargesPortion = Money.zero(currency);
        Money penaltyChargesPortion = Money.zero(currency);

        if (loanTransaction.isChargesWaiver()) {
            penaltyChargesPortion = currentInstallment.waivePenaltyChargesComponent(transactionDate,
                    loanTransaction.getPenaltyChargesPortion(currency));
            transactionAmountRemaining = transactionAmountRemaining.minus(penaltyChargesPortion);
            feeChargesPortion = currentInstallment.waiveFeeChargesComponent(transactionDate,
                    loanTransaction.getFeeChargesPortion(currency));
            transactionAmountRemaining = transactionAmountRemaining.minus(feeChargesPortion);
        } else if (loanTransaction.isInterestWaiver()) {
            interestPortion = currentInstallment.waiveInterestComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(interestPortion);
            loanTransaction.updateComponents(principalPortion, interestPortion, feeChargesPortion, penaltyChargesPortion);
        } else if (loanTransaction.isChargePayment()) {
            if (loanTransaction.isPenaltyPayment()) {
                penaltyChargesPortion = currentInstallment.payPenaltyChargesComponent(transactionDate, transactionAmountRemaining);
                transactionAmountRemaining = transactionAmountRemaining.minus(penaltyChargesPortion);
            } else {
                feeChargesPortion = currentInstallment.payFeeChargesComponent(transactionDate, transactionAmountRemaining);
                transactionAmountRemaining = transactionAmountRemaining.minus(feeChargesPortion);
            }
            loanTransaction.updateComponents(principalPortion, interestPortion, feeChargesPortion, penaltyChargesPortion);
        } else {
            principalPortion = currentInstallment.payPrincipalComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(principalPortion);
            interestPortion = currentInstallment.payInterestComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(interestPortion);
            feeChargesPortion = currentInstallment.payFeeChargesComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(feeChargesPortion);
            penaltyChargesPortion = currentInstallment.payPenaltyChargesComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(penaltyChargesPortion);
            loanTransaction.updateComponents(principalPortion, interestPortion, feeChargesPortion, penaltyChargesPortion);
        }
        if (principalPortion.plus(interestPortion).plus(feeChargesPortion).plus(penaltyChargesPortion).isGreaterThanZero()) {
            transactionMappings.add(LoanTransactionToRepaymentScheduleMapping.createFrom(loanTransaction, currentInstallment,
                    principalPortion, interestPortion, feeChargesPortion, penaltyChargesPortion));
        }
        return transactionAmountRemaining;
    }

    @Override
    protected Money handleRefundTransactionPaymentOfInstallment(final LoanRepaymentScheduleInstallment currentInstallment,
            final LoanTransaction loanTransaction, final Money transactionAmountUnprocessed,
            final List<LoanTransactionToRepaymentScheduleMapping> transactionMappings) {
        final LocalDate transactionDate = loanTransaction.getTransactionDate();
        Money transactionAmountRemaining = transactionAmountUnprocessed;
        Money principalPortion = Money.zero(transactionAmountRemaining.getCurrency());
        Money interestPortion = Money.zero(transactionAmountRemaining.getCurrency());
        Money feeChargesPortion = Money.zero(transactionAmountRemaining.getCurrency());
        Money penaltyChargesPortion = Money.zero(transactionAmountRemaining.getCurrency());

        if (transactionAmountRemaining.isGreaterThanZero()) {
            penaltyChargesPortion = currentInstallment.unpayPenaltyChargesComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(penaltyChargesPortion);
        }
        if (transactionAmountRemaining.isGreaterThanZero()) {
            feeChargesPortion = currentInstallment.unpayFeeChargesComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(feeChargesPortion);
        }
        Money taxChargesPortion = Money.zero(transactionAmountRemaining.getCurrency());
        if (transactionAmountRemaining.isGreaterThanZero()) {
            taxChargesPortion = currentInstallment.unpayTaxChargesComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(taxChargesPortion);
        }
        if (transactionAmountRemaining.isGreaterThanZero()) {
            interestPortion = currentInstallment.unpayInterestComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(interestPortion);
        }
        if (transactionAmountRemaining.isGreaterThanZero()) {
            principalPortion = currentInstallment.unpayPrincipalComponent(transactionDate, transactionAmountRemaining);
            transactionAmountRemaining = transactionAmountRemaining.minus(principalPortion);
        }

        loanTransaction.updateComponents(principalPortion, interestPortion, feeChargesPortion, penaltyChargesPortion, taxChargesPortion);
        if (principalPortion.plus(interestPortion).plus(feeChargesPortion).plus(penaltyChargesPortion).isGreaterThanZero()) {
            transactionMappings.add(LoanTransactionToRepaymentScheduleMapping.createFrom(loanTransaction, currentInstallment,
                    principalPortion, interestPortion, feeChargesPortion, penaltyChargesPortion));
        }
        return transactionAmountRemaining;
    }

    public static boolean isOverdue(final LoanRepaymentScheduleInstallment installment, final LocalDate asOf) {
        return installment != null && asOf != null && !installment.isDownPayment() && !installment.isAdditional()
                && installment.getDueDate() != null && DateUtils.isAfter(asOf, installment.getDueDate());
    }

    private enum Bucket {
        PRINCIPAL, INTEREST, FEE, TAX, PENALTY
    }

    private static final class ComponentPortions {

        private Money principal;
        private Money interest;
        private Money fee;
        private Money tax;
        private Money penalty;

        private ComponentPortions(final MonetaryCurrency currency) {
            this.principal = Money.zero(currency);
            this.interest = Money.zero(currency);
            this.fee = Money.zero(currency);
            this.tax = Money.zero(currency);
            this.penalty = Money.zero(currency);
        }

        private void add(final Bucket bucket, final Money paid) {
            switch (bucket) {
                case PRINCIPAL -> this.principal = this.principal.plus(paid);
                case INTEREST -> this.interest = this.interest.plus(paid);
                case FEE -> this.fee = this.fee.plus(paid);
                case TAX -> this.tax = this.tax.plus(paid);
                case PENALTY -> this.penalty = this.penalty.plus(paid);
            }
        }

        private Money total() {
            return this.principal.plus(this.interest).plus(this.fee).plus(this.tax).plus(this.penalty);
        }
    }
}
