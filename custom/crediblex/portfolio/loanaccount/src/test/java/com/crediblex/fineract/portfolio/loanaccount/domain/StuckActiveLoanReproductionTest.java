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
package com.crediblex.fineract.portfolio.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.util.LoanChargeSettlementUtils;
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
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the production defect behind loan {@code 000000397} (and 16 other loans found by the sweep): a loan that
 * is economically fully repaid (outstanding = 0, not overpaid) is left stuck in {@link LoanStatus#ACTIVE} instead of
 * transitioning to {@link LoanStatus#CLOSED_OBLIGATIONS_MET}.
 *
 * <p>
 * Root cause: the custom overdue/bulk charge-settlement paths in {@code CredXLoanChargeWritePlatformServiceImpl}
 * (deactivate / bulk-waive / window-waive / reversePaid) drive an overdue-interest (LPI) penalty charge to
 * {@code amountOutstanding == 0} <b>without</b> setting its {@code paid}/{@code waived} flag, then refresh status only
 * through {@link Loan#updateLoanSummaryAndStatus()}. That funnels into base {@code Loan.handleLoanRepaymentInFull()},
 * whose "all charges paid" test reads the paid/waived <b>flags</b> (not the outstanding amount). The loan is therefore
 * neither "all charges paid" nor "overpaid", so it silently stays ACTIVE.
 *
 * <p>
 * {@link #reproduce_baseStatusRefresh_leavesFullyPaidLoanStuckActive()} confirms the hypothesis; {@link
 * #fix_closeIfFullySettled_closesTheStuckActiveLoan()} confirms the amount-based close that the fix wires into those
 * paths resolves it.
 */
class StuckActiveLoanReproductionTest {

    private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, 6, 19);

    /**
     * Builds loan 000000397's exact end-state: fully repaid (summary outstanding = 0, not overpaid), status ACTIVE, and
     * one still-active LPI penalty charge whose outstanding is 0 but whose paid/waived flag was never set.
     */
    private Loan buildStuckActiveLoan() {
        final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);

        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LoanProductRelatedDetail loanProductRelatedDetail = mock(LoanProductRelatedDetail.class);
        when(loanProduct.getLoanProductRelatedDetail()).thenReturn(loanProductRelatedDetail);
        when(loanProductRelatedDetail.getCurrency()).thenReturn(currency);

        // Summary reports the loan as fully repaid (total outstanding == 0).
        final LoanSummary summary = mock(LoanSummary.class);
        when(summary.isRepaidInFull(any())).thenReturn(true);

        // The "dead-zone" charge: active, amount > 0, outstanding 0, but paid/waived flags NOT set - exactly the state
        // the custom bulk/overdue settlement paths leave a fully-collected/waived LPI charge in.
        final LoanCharge lpiCharge = mock(LoanCharge.class);
        when(lpiCharge.isActive()).thenReturn(true);
        when(lpiCharge.amount()).thenReturn(new BigDecimal("218.84"));
        when(lpiCharge.isPaid()).thenReturn(false);
        when(lpiCharge.isWaived()).thenReturn(false);
        when(lpiCharge.amountOutstanding()).thenReturn(BigDecimal.ZERO);
        when(lpiCharge.getTaxAmountOutstanding()).thenReturn(BigDecimal.ZERO);

        return new LoanBuilder(loanProduct) //
                .withId(397L) //
                .withLoanStatus(LoanStatus.ACTIVE) //
                .withCharges(Set.of(lpiCharge)) //
                .withSummary(summary) //
                .build();
    }

    @Test
    void reproduce_baseStatusRefresh_leavesFullyPaidLoanStuckActive() {
        final Loan loan = buildStuckActiveLoan();
        final LoanLifecycleStateMachine stateMachine = mock(LoanLifecycleStateMachine.class);

        // This is what the broken custom settlement paths funnel into via loan.updateLoanSummaryAndStatus().
        loan.doPostLoanTransactionChecks(SETTLEMENT_DATE, stateMachine);

        // BUG: repaid-in-full + a charge whose paid/waived flag is unset + status ACTIVE -> both branches of
        // handleLoanRepaymentInFull() are skipped, so the loan is left stuck ACTIVE and never closed.
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(loan.getClosedOnDate()).isNull();
        verify(stateMachine, never()).transition(eq(LoanEvent.REPAID_IN_FULL), any());
    }

    @Test
    void fix_closeIfFullySettled_closesTheStuckActiveLoan() {
        final Loan loan = buildStuckActiveLoan();
        final LoanLifecycleStateMachine stateMachine = mock(LoanLifecycleStateMachine.class);

        // The amount-based close that the fix adds to the custom settlement paths (already used by the correct
        // single-charge waive path). It treats a zero-outstanding charge as settled regardless of its flags.
        final boolean closed = LoanChargeSettlementUtils.closeIfFullySettled(loan, SETTLEMENT_DATE, stateMachine);

        assertThat(closed).isTrue();
        assertThat(loan.getClosedOnDate()).isEqualTo(SETTLEMENT_DATE);
        verify(stateMachine).transition(LoanEvent.REPAID_IN_FULL, loan);
    }
}
