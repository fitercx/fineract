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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reproduces the real UAT overpayment cases and asserts the reconciler clamps the foreclosure settlement to the
 * schedule's allocatable outstanding so the loan closes cleanly instead of Overpaid.
 */
class ForeclosureAmountReconcilerTest {

    private final MonetaryCurrency currency = mock(MonetaryCurrency.class);
    private final ConfigurationDomainService configurationDomainService = mock(ConfigurationDomainService.class);

    @BeforeEach
    void setUp() {
        when(currency.getCode()).thenReturn("AED");
        when(currency.getDigitsAfterDecimal()).thenReturn(3);
        when(currency.getCurrencyInMultiplesOf()).thenReturn(0);
        when(currency.toData()).thenReturn(new CurrencyData("AED", "UAE Dirham", 3, 0, "AED", "currency.AED"));
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", configurationDomainService);
        lenient().when(configurationDomainService.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        moneyHelper.initialize();
    }

    private Money money(final String amount) {
        return Money.of(currency, new BigDecimal(amount));
    }

    /** A single open installment carrying the given total outstanding. */
    private Loan loanWithAllocatableOutstanding(final String allocatable) {
        final Money allocatableMoney = money(allocatable);
        final Loan loan = mock(Loan.class);
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getTotalOutstanding(currency)).thenReturn(allocatableMoney);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(installment));
        return loan;
    }

    @Test
    @DisplayName("RBF loan 15628: LPI not backed by an open installment is waived, not overpaid")
    void clampsPenaltyOverInclusion() {
        // Raw = principal 32,861.39 + penalty 91.75 = 32,953.14; schedule can only absorb 32,861.39.
        final Loan loan = loanWithAllocatableOutstanding("32861.39");

        final ForeclosureAmountReconciler.Result result = ForeclosureAmountReconciler.reconcile(loan, currency, money("32861.39"),
                money("0"), money("0"), money("91.75"), money("0"));

        assertThat(result.wasReduced()).isTrue();
        assertThat(result.waived().getAmount()).isEqualByComparingTo("91.75");
        assertThat(result.penalty().getAmount()).isEqualByComparingTo("0");
        assertThat(result.principal().getAmount()).isEqualByComparingTo("32861.39");
        assertThat(result.total().getAmount()).isEqualByComparingTo("32861.39");
    }

    @Test
    @DisplayName("Factor Rate loan 15109: unearned fee beyond allocatable schedule fee is waived")
    void clampsFactorRateFeeOverInclusion() {
        // Raw = principal 60,280 + fee 27,720 = 88,000; schedule can only absorb 86,680.
        final Loan loan = loanWithAllocatableOutstanding("86680.00");

        final ForeclosureAmountReconciler.Result result = ForeclosureAmountReconciler.reconcile(loan, currency, money("60280.00"),
                money("0"), money("27720.00"), money("0"), money("0"));

        assertThat(result.wasReduced()).isTrue();
        assertThat(result.waived().getAmount()).isEqualByComparingTo("1320.00");
        assertThat(result.fee().getAmount()).isEqualByComparingTo("26400.00");
        assertThat(result.total().getAmount()).isEqualByComparingTo("86680.00");
    }

    @Test
    @DisplayName("Factor Rate loan 11188: large unearned fee waived, principal preserved")
    void clampsLargeFactorRateFee() {
        // Raw = principal 89,508.23 + fee 10,500 = 100,008.23; schedule can only absorb 89,508.23.
        final Loan loan = loanWithAllocatableOutstanding("89508.23");

        final ForeclosureAmountReconciler.Result result = ForeclosureAmountReconciler.reconcile(loan, currency, money("89508.23"),
                money("0"), money("10500.00"), money("0"), money("0"));

        assertThat(result.waived().getAmount()).isEqualByComparingTo("10500.00");
        assertThat(result.principal().getAmount()).isEqualByComparingTo("89508.23");
        assertThat(result.total().getAmount()).isEqualByComparingTo("89508.23");
    }

    @Test
    @DisplayName("Correctly-priced foreclosure (loan 15679) is left untouched")
    void noOpWhenAmountFitsAllocatable() {
        // Raw = principal 90,922.57 + interest 387.64 = 91,310.21, exactly the allocatable outstanding.
        final Loan loan = loanWithAllocatableOutstanding("91310.21");

        final ForeclosureAmountReconciler.Result result = ForeclosureAmountReconciler.reconcile(loan, currency, money("90922.57"),
                money("387.64"), money("0"), money("0"), money("0"));

        assertThat(result.wasReduced()).isFalse();
        assertThat(result.waived().getAmount()).isEqualByComparingTo("0");
        assertThat(result.interest().getAmount()).isEqualByComparingTo("387.64");
        assertThat(result.total().getAmount()).isEqualByComparingTo("91310.21");
    }

    @Test
    @DisplayName("Fails open when the schedule reports no allocatable outstanding")
    void noOpWhenNoAllocatableOutstanding() {
        final Loan loan = mock(Loan.class);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of());

        final ForeclosureAmountReconciler.Result result = ForeclosureAmountReconciler.reconcile(loan, currency, money("100.00"), money("0"),
                money("0"), money("50.00"), money("0"));

        assertThat(result.wasReduced()).isFalse();
        assertThat(result.total().getAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Excess larger than penalty spills into fee then interest, never principal")
    void reducesInReverseWaterfallOrder() {
        // Raw = principal 100 + interest 30 + fee 20 + penalty 10 = 160; allocatable 105 -> waive 55.
        final Loan loan = loanWithAllocatableOutstanding("105.00");

        final ForeclosureAmountReconciler.Result result = ForeclosureAmountReconciler.reconcile(loan, currency, money("100.00"),
                money("30.00"), money("20.00"), money("10.00"), money("0"));

        assertThat(result.waived().getAmount()).isEqualByComparingTo("55.00");
        assertThat(result.penalty().getAmount()).isEqualByComparingTo("0"); // 10 waived
        assertThat(result.fee().getAmount()).isEqualByComparingTo("0"); // 20 waived
        assertThat(result.interest().getAmount()).isEqualByComparingTo("5.00"); // 25 waived
        assertThat(result.principal().getAmount()).isEqualByComparingTo("100.00"); // never touched
        assertThat(result.total().getAmount()).isEqualByComparingTo("105.00");
    }
}
