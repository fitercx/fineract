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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.Test;

/**
 * Fix 1: foreclosure of a LOC (payable/receivable drawdown) loan must be blocked once the loan is on or past its
 * earliest unpaid installment due date — that is no longer an early-settlement/foreclosure scenario. Non-LOC loans and
 * fully-paid loans are unaffected.
 */
class LocForeclosureValidatorTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 7, 30);

    private Loan loanWithEarliestUnpaidDueDate(final LocalDate dueDate) {
        final Loan loan = mock(Loan.class);
        when(loan.getId()).thenReturn(3747L);
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.isNotFullyPaidOff()).thenReturn(true);
        when(installment.getDueDate()).thenReturn(dueDate);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(installment));
        return loan;
    }

    private Optional<LoanLineOfCreditParams> locParams() {
        return Optional.of(mock(LoanLineOfCreditParams.class));
    }

    @Test
    void blocksWhenForeclosureDateEqualsEarliestUnpaidDueDate() {
        final Loan loan = loanWithEarliestUnpaidDueDate(DUE_DATE);
        assertThatThrownBy(() -> LocForeclosureValidator.validateNotDueOrOverdue(loan, DUE_DATE, locParams()))
                .isInstanceOf(GeneralPlatformDomainRuleException.class);
    }

    @Test
    void blocksWhenForeclosureDateAfterEarliestUnpaidDueDate() {
        final Loan loan = loanWithEarliestUnpaidDueDate(DUE_DATE);
        assertThatThrownBy(() -> LocForeclosureValidator.validateNotDueOrOverdue(loan, DUE_DATE.plusDays(5), locParams()))
                .isInstanceOf(GeneralPlatformDomainRuleException.class);
    }

    @Test
    void allowsWhenForeclosureDateBeforeEarliestUnpaidDueDate() {
        final Loan loan = loanWithEarliestUnpaidDueDate(DUE_DATE);
        assertThatCode(() -> LocForeclosureValidator.validateNotDueOrOverdue(loan, DUE_DATE.minusDays(1), locParams()))
                .doesNotThrowAnyException();
    }

    @Test
    void noOpForNonLocLoanEvenWhenOverdue() {
        final Loan loan = loanWithEarliestUnpaidDueDate(DUE_DATE);
        assertThatCode(() -> LocForeclosureValidator.validateNotDueOrOverdue(loan, DUE_DATE.plusDays(5), Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    void noOpWhenAllInstallmentsFullyPaid() {
        final Loan loan = mock(Loan.class);
        final LoanRepaymentScheduleInstallment paid = mock(LoanRepaymentScheduleInstallment.class);
        when(paid.isNotFullyPaidOff()).thenReturn(false);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(paid));
        assertThatCode(() -> LocForeclosureValidator.validateNotDueOrOverdue(loan, DUE_DATE, locParams()))
                .doesNotThrowAnyException();
    }
}
