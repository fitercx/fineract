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
package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Covers the in-memory DPD measure used by the COB step and the post-allocation re-evaluation. */
class DpdMaxDaysPastDueServiceTest {

    private static final MonetaryCurrency AED = new MonetaryCurrency("AED", 2, null);
    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 14);

    private JdbcTemplate jdbcTemplate;
    private DpdMaxDaysPastDueService underTest;
    private Loan loan;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        underTest = new DpdMaxDaysPastDueService(jdbcTemplate);
        loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(AED);
    }

    /** {@code Money.of} needs a wired-up MoneyHelper, so the outstanding balance is stubbed rather than built. */
    private LoanRepaymentScheduleInstallment installment(final LocalDate dueDate, final boolean owesSomething, final boolean downPayment) {
        final Money outstanding = mock(Money.class);
        when(outstanding.isGreaterThanZero()).thenReturn(owesSomething);
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getDueDate()).thenReturn(dueDate);
        when(installment.isDownPayment()).thenReturn(downPayment);
        when(installment.getTotalOutstanding(AED)).thenReturn(outstanding);
        return installment;
    }

    /** Stubbing has to finish before {@code when(...)} on the loan starts, hence the local list. */
    private void givenSchedule(final LoanRepaymentScheduleInstallment... installments) {
        final List<LoanRepaymentScheduleInstallment> schedule = List.of(installments);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(schedule);
    }

    @Test
    void measuresFromTheOldestOverdueInstallmentThatStillOwesSomething() {
        givenSchedule( //
                installment(LocalDate.of(2026, 6, 1), false, false), // settled, must be skipped
                installment(LocalDate.of(2026, 7, 1), true, false), // oldest still owing
                installment(LocalDate.of(2026, 8, 3), true, false), //
                installment(LocalDate.of(2026, 10, 1), true, false)); // not due yet

        assertThat(underTest.calculateMaxDpd(loan, AS_OF)).isEqualTo(75);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void returnsZeroWhenEveryOverdueInstallmentIsSettled() {
        givenSchedule( //
                installment(LocalDate.of(2026, 6, 1), false, false), //
                installment(LocalDate.of(2026, 7, 1), false, false), //
                installment(LocalDate.of(2026, 10, 1), true, false));

        assertThat(underTest.calculateMaxDpd(loan, AS_OF)).isZero();
    }

    @Test
    void ignoresDownPaymentInstallments() {
        givenSchedule( //
                installment(LocalDate.of(2026, 6, 1), true, true), //
                installment(LocalDate.of(2026, 8, 3), true, false));

        assertThat(underTest.calculateMaxDpd(loan, AS_OF)).isEqualTo(42);
    }

    @Test
    void returnsZeroForAMissingLoanOrDate() {
        assertThat(underTest.calculateMaxDpd((Loan) null, AS_OF)).isZero();
        assertThat(underTest.calculateMaxDpd(loan, null)).isZero();
    }
}
