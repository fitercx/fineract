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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanEvent;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.junit.jupiter.api.Test;

class LoanChargeSettlementUtilsTest {

    @Test
    void hasNoPayableChargesRemainingWhenWaivedZeroTaxChargeHasStaleTaxWaivedFlag() {
        final Loan loan = mock(Loan.class);
        final LoanCharge charge = mock(LoanCharge.class);

        when(loan.getCharges()).thenReturn(Set.of(charge));
        when(charge.isActive()).thenReturn(true);
        when(charge.amount()).thenReturn(new BigDecimal("1949.100000"));
        when(charge.isPaid()).thenReturn(false);
        when(charge.isWaived()).thenReturn(false);
        when(charge.amountOutstanding()).thenReturn(BigDecimal.ZERO);
        when(charge.getTaxAmountOutstanding()).thenReturn(BigDecimal.ZERO);

        assertThat(LoanChargeSettlementUtils.hasNoPayableChargesRemaining(loan)).isTrue();
    }

    @Test
    void hasPayableChargesRemainingWhenAnyActiveChargeHasOutstandingBalance() {
        final Loan loan = mock(Loan.class);
        final LoanCharge charge = mock(LoanCharge.class);

        when(loan.getCharges()).thenReturn(Set.of(charge));
        when(charge.isActive()).thenReturn(true);
        when(charge.amount()).thenReturn(new BigDecimal("100.00"));
        when(charge.isPaid()).thenReturn(false);
        when(charge.isWaived()).thenReturn(false);
        when(charge.amountOutstanding()).thenReturn(new BigDecimal("10.00"));
        when(charge.getTaxAmountOutstanding()).thenReturn(BigDecimal.ZERO);

        assertThat(LoanChargeSettlementUtils.hasNoPayableChargesRemaining(loan)).isFalse();
    }

    @Test
    void closesActiveLoanWhenSummaryAndChargesHaveNoPayableBalance() {
        final Loan loan = mock(Loan.class);
        final LoanSummary loanSummary = mock(LoanSummary.class);
        final MonetaryCurrency currency = mock(MonetaryCurrency.class);
        final LoanLifecycleStateMachine loanLifecycleStateMachine = mock(LoanLifecycleStateMachine.class);
        final LoanCharge charge = mock(LoanCharge.class);
        final LocalDate transactionDate = LocalDate.of(2026, 6, 30);

        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getSummary()).thenReturn(loanSummary);
        when(loan.getCurrency()).thenReturn(currency);
        when(loanSummary.isRepaidInFull(currency)).thenReturn(true);
        when(loan.getCharges()).thenReturn(Set.of(charge));
        when(charge.isActive()).thenReturn(true);
        when(charge.amount()).thenReturn(new BigDecimal("1949.100000"));
        when(charge.isPaid()).thenReturn(false);
        when(charge.isWaived()).thenReturn(false);
        when(charge.amountOutstanding()).thenReturn(BigDecimal.ZERO);
        when(charge.getTaxAmountOutstanding()).thenReturn(BigDecimal.ZERO);

        assertThat(LoanChargeSettlementUtils.closeIfFullySettled(loan, transactionDate, loanLifecycleStateMachine)).isTrue();

        verify(loan).setClosedOnDate(transactionDate);
        verify(loan).setActualMaturityDate(transactionDate);
        verify(loanLifecycleStateMachine).transition(LoanEvent.REPAID_IN_FULL, loan);
    }

    @Test
    void doesNotCloseActiveLoanWhenAnyChargeHasPayableBalance() {
        final Loan loan = mock(Loan.class);
        final LoanSummary loanSummary = mock(LoanSummary.class);
        final MonetaryCurrency currency = mock(MonetaryCurrency.class);
        final LoanLifecycleStateMachine loanLifecycleStateMachine = mock(LoanLifecycleStateMachine.class);
        final LoanCharge charge = mock(LoanCharge.class);
        final LocalDate transactionDate = LocalDate.of(2026, 6, 30);

        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getSummary()).thenReturn(loanSummary);
        when(loan.getCurrency()).thenReturn(currency);
        when(loanSummary.isRepaidInFull(currency)).thenReturn(true);
        when(loan.getCharges()).thenReturn(Set.of(charge));
        when(charge.isActive()).thenReturn(true);
        when(charge.amount()).thenReturn(new BigDecimal("100.00"));
        when(charge.isPaid()).thenReturn(false);
        when(charge.isWaived()).thenReturn(false);
        when(charge.amountOutstanding()).thenReturn(new BigDecimal("10.00"));
        when(charge.getTaxAmountOutstanding()).thenReturn(BigDecimal.ZERO);

        assertThat(LoanChargeSettlementUtils.closeIfFullySettled(loan, transactionDate, loanLifecycleStateMachine)).isFalse();

        verify(loan, never()).setClosedOnDate(transactionDate);
        verify(loan, never()).setActualMaturityDate(transactionDate);
        verify(loanLifecycleStateMachine, never()).transition(LoanEvent.REPAID_IN_FULL, loan);
    }

    @Test
    void refreshSummaryStatusAndCloseIfSettledRefreshesStatusThenClosesFullySettledLoan() {
        final Loan loan = mock(Loan.class);
        final LoanSummary loanSummary = mock(LoanSummary.class);
        final MonetaryCurrency currency = mock(MonetaryCurrency.class);
        final LoanLifecycleStateMachine loanLifecycleStateMachine = mock(LoanLifecycleStateMachine.class);
        final LoanCharge charge = mock(LoanCharge.class);
        final LocalDate transactionDate = LocalDate.of(2026, 6, 19);

        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getSummary()).thenReturn(loanSummary);
        when(loan.getCurrency()).thenReturn(currency);
        when(loanSummary.isRepaidInFull(currency)).thenReturn(true);
        when(loan.getCharges()).thenReturn(Set.of(charge));
        // Zero-outstanding LPI charge whose paid/waived flag was never set - the exact stuck-active state.
        when(charge.isActive()).thenReturn(true);
        when(charge.amount()).thenReturn(new BigDecimal("218.84"));
        when(charge.isPaid()).thenReturn(false);
        when(charge.isWaived()).thenReturn(false);
        when(charge.amountOutstanding()).thenReturn(BigDecimal.ZERO);
        when(charge.getTaxAmountOutstanding()).thenReturn(BigDecimal.ZERO);

        assertThat(LoanChargeSettlementUtils.refreshSummaryStatusAndCloseIfSettled(loan, transactionDate, loanLifecycleStateMachine))
                .isTrue();

        verify(loan).updateLoanSummaryAndStatus();
        verify(loan).setClosedOnDate(transactionDate);
        verify(loanLifecycleStateMachine).transition(LoanEvent.REPAID_IN_FULL, loan);
    }

    @Test
    void refreshSummaryStatusAndCloseIfSettledDoesNotCloseLoanWithPayableCharge() {
        final Loan loan = mock(Loan.class);
        final LoanSummary loanSummary = mock(LoanSummary.class);
        final MonetaryCurrency currency = mock(MonetaryCurrency.class);
        final LoanLifecycleStateMachine loanLifecycleStateMachine = mock(LoanLifecycleStateMachine.class);
        final LoanCharge charge = mock(LoanCharge.class);
        final LocalDate transactionDate = LocalDate.of(2026, 6, 19);

        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getSummary()).thenReturn(loanSummary);
        when(loan.getCurrency()).thenReturn(currency);
        when(loanSummary.isRepaidInFull(currency)).thenReturn(true);
        when(loan.getCharges()).thenReturn(Set.of(charge));
        when(charge.isActive()).thenReturn(true);
        when(charge.amount()).thenReturn(new BigDecimal("100.00"));
        when(charge.isPaid()).thenReturn(false);
        when(charge.isWaived()).thenReturn(false);
        when(charge.amountOutstanding()).thenReturn(new BigDecimal("10.00"));
        when(charge.getTaxAmountOutstanding()).thenReturn(BigDecimal.ZERO);

        assertThat(LoanChargeSettlementUtils.refreshSummaryStatusAndCloseIfSettled(loan, transactionDate, loanLifecycleStateMachine))
                .isFalse();

        // Status is still refreshed, but the loan is not closed while a charge remains payable.
        verify(loan).updateLoanSummaryAndStatus();
        verify(loan, never()).setClosedOnDate(transactionDate);
        verify(loanLifecycleStateMachine, never()).transition(LoanEvent.REPAID_IN_FULL, loan);
    }
}
