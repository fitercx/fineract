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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.util.ReversePaidChargeReallocator;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Reproduces the production defect behind loan {@code 000002091}: reversing an already-paid overdue-interest (LPI)
 * penalty on a <b>partially-paid</b> loan via the custom {@code reversePaid} command leaves the freed paid amount
 * unallocated, so the loan reports a <b>phantom overpayment</b> equal to the reversed charge (prod
 * {@code total_overpaid_derived = 483.87}) while the schedule still shows real outstanding.
 *
 * <p>
 * Root-cause arithmetic ({@link Loan#calculateTotalOverpayment()}): overpayment is the gap between the
 * <b>transaction-side</b> total ({@link Loan#getTotalPaidInRepayments()}) and the <b>installment-side</b> total (sum of
 * {@code principalCompleted + interestPaid + feeChargesPaid + penaltyChargesPaid}). Today The original reversePaid path
 * called {@link LoanRepaymentScheduleInstallment#unpayPenaltyChargesComponent} — which lowers the installment side —
 * but left the original repayment transaction untouched (its audit CHARGE_ADJUSTMENT is posted with a zero amount). The
 * transaction side therefore stayed put, so the reversed amount surfaced as overpayment. LMS-107 fixed that by
 * re-applying the freed amount to principal. The follow-up must do that <b>locally</b> (unpay penalty + pay principal +
 * move the txn penalty→principal) and must not {@code reprocessTransactions} the whole loan.
 *
 * <p>
 * {@link #reproduce_reversePaidWithoutReprocess_producesPhantomOverpayment()} confirms the defect;
 * {@link #fix_reprocessReallocatesFreedPenaltyToPrincipal_noOverpaymentAndOutstandingReduced()} confirms that unpaying
 * the penalty and paying that amount onto principal removes the phantom overpayment and reduces principal outstanding —
 * the "Model A" mutation {@code ReversePaidChargeReallocator} applies without a full-history reprocess.
 */
class ReversePaidPhantomOverpaymentTest {

    private static final MonetaryCurrency AED = new MonetaryCurrency("AED", 2, 0);

    private static final LocalDate DISBURSEMENT = LocalDate.of(2026, 4, 30);
    private static final LocalDate DUE = LocalDate.of(2026, 7, 29);
    private static final LocalDate REVERSAL = LocalDate.of(2026, 7, 30);

    // Loan 2091's exact figures.
    private static final BigDecimal PRINCIPAL = new BigDecimal("776824.04");
    private static final BigDecimal INTEREST = new BigDecimal("28731.85");
    private static final BigDecimal PENALTY = new BigDecimal("483.87"); // overdue LPI charge 10751
    private static final BigDecimal PRINCIPAL_PAID = new BigDecimal("216353.21"); // portion of the SI sweep applied to
                                                                                  // principal
    private static final BigDecimal SWEEP = new BigDecimal("216837.08"); // SI 1806 partial sweep = PRINCIPAL_PAID +
                                                                         // PENALTY

    private MockedStatic<MoneyHelper> moneyHelperMock;

    @BeforeEach
    void setUp() {
        // Money arithmetic (payPrincipalComponent / unpayPenaltyChargesComponent / calculateTotalOverpayment) relies on
        // MoneyHelper's static MathContext, which is Spring-wired at runtime but null in a plain unit test.
        moneyHelperMock = mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
    }

    @AfterEach
    void tearDown() {
        if (moneyHelperMock != null) {
            moneyHelperMock.close();
        }
    }

    /**
     * Builds loan 2091's pre-reversal state: a partially-paid bullet loan whose single overdue installment has the LPI
     * penalty fully paid (via reprocessing) plus a partial principal payment, funded by one SI-sweep repayment
     * transaction. In this state the loan is correctly NOT overpaid.
     */
    private Loan buildPartiallyPaidLoanWithPaidPenalty() {
        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LoanProductRelatedDetail detail = mock(LoanProductRelatedDetail.class);
        when(loanProduct.getLoanProductRelatedDetail()).thenReturn(detail);
        when(detail.getCurrency()).thenReturn(AED);

        // The SI 1806 sweep: a single repayment-like transaction whose amount (216,837.08) is what the
        // transaction-side total getTotalPaidInRepayments() sums. It is never reversed by the reversal flow.
        // Precompute the Money OUTSIDE the when(...) so the mocked-static MoneyHelper interaction it performs does not
        // interleave with the ongoing stubbing (Mockito would otherwise flag an UnfinishedStubbingException).
        final Money sweepAmount = Money.of(AED, SWEEP);
        final LoanTransaction sweep = mock(LoanTransaction.class);
        when(sweep.isRepaymentLikeType()).thenReturn(true);
        when(sweep.isReversed()).thenReturn(false);
        when(sweep.getAmount(AED)).thenReturn(sweepAmount);

        final Loan loan = new LoanBuilder(loanProduct) //
                .withId(2091L) //
                .withLoanStatus(LoanStatus.ACTIVE) //
                .withLoanTransaction(sweep) //
                .build();

        // Real installment carrying the paid state: penalty 483.87 fully paid + 216,353.21 principal paid.
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1, DISBURSEMENT, DUE, PRINCIPAL,
                INTEREST, BigDecimal.ZERO, BigDecimal.ZERO, PENALTY, false, null, BigDecimal.ZERO);
        installment.payPenaltyChargesComponent(DUE, Money.of(AED, PENALTY));
        installment.payPrincipalComponent(DUE, Money.of(AED, PRINCIPAL_PAID));
        loan.getRepaymentScheduleInstallments().add(installment);

        return loan;
    }

    @Test
    void reproduce_reversePaidWithoutReprocess_producesPhantomOverpayment() {
        final Loan loan = buildPartiallyPaidLoanWithPaidPenalty();
        final LoanRepaymentScheduleInstallment installment = loan.getRepaymentScheduleInstallments().get(0);

        // Precondition: a correctly-paid partial loan has NO overpayment (transaction side == installment side).
        assertThat(loan.calculateTotalOverpayment().getAmount()).isEqualByComparingTo("0.00");

        // What reversePaidLoanCharge does to the domain today: drop the penalty from the installment's paid aggregate
        // (unpayPenaltyChargesComponent) WITHOUT reprocessing the freed amount down the waterfall and WITHOUT touching
        // the original sweep transaction (the audit CHARGE_ADJUSTMENT it posts carries a zero amount).
        installment.unpayPenaltyChargesComponent(REVERSAL, Money.of(AED, PENALTY));

        // BUG: transaction-side total (216,837.08) now exceeds installment-side total (216,353.21) by exactly the
        // reversed penalty, which base calculateTotalOverpayment() reports as a phantom overpayment.
        // Matches production total_overpaid_derived = 483.87 on loan 2091.
        assertThat(loan.calculateTotalOverpayment().getAmount()).isEqualByComparingTo("483.87");
    }

    @Test
    void fix_reprocessReallocatesFreedPenaltyToPrincipal_noOverpaymentAndOutstandingReduced() {
        final Loan loan = buildPartiallyPaidLoanWithPaidPenalty();
        final LoanRepaymentScheduleInstallment installment = loan.getRepaymentScheduleInstallments().get(0);

        final BigDecimal principalOutstandingBefore = installment.getPrincipalOutstanding(AED).getAmount();

        // The fix: reprocessTransactions re-runs the payment waterfall with the penalty charge removed, so the 483.87
        // that had been applied to the (now-gone) penalty is re-applied to the next bucket — principal. The net
        // installment mutation for this loan is exactly: unpay the penalty, then pay that amount onto principal.
        installment.unpayPenaltyChargesComponent(REVERSAL, Money.of(AED, PENALTY));
        installment.payPrincipalComponent(REVERSAL, Money.of(AED, PENALTY));

        // No phantom overpayment: transaction side and installment side match again.
        assertThat(loan.calculateTotalOverpayment().getAmount()).isEqualByComparingTo("0.00");

        // ...and the freed amount reduced principal outstanding instead of becoming excess cash (Model A).
        assertThat(installment.getPrincipalOutstanding(AED).getAmount()).isEqualByComparingTo(principalOutstandingBefore.subtract(PENALTY));
    }

    @Test
    void targetedReallocator_movesPenaltyToPrincipalOnSameTxn_withoutFullHistoryReplay() {
        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LoanProductRelatedDetail detail = mock(LoanProductRelatedDetail.class);
        when(loanProduct.getLoanProductRelatedDetail()).thenReturn(detail);
        when(detail.getCurrency()).thenReturn(AED);

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(REVERSAL);
            mockedDateUtils.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class)))
                    .thenAnswer(invocation -> invocation.<LocalDate>getArgument(0).isBefore(invocation.getArgument(1)));
            mockedDateUtils.when(() -> DateUtils.isAfter(any(LocalDate.class), any(LocalDate.class)))
                    .thenAnswer(invocation -> invocation.<LocalDate>getArgument(0).isAfter(invocation.getArgument(1)));

            final Money sweepAmount = Money.of(AED, SWEEP);
            final LoanTransaction sweep = LoanTransaction.repayment(mock(Office.class), sweepAmount, null, DUE, ExternalId.empty());
            sweep.updateComponents(Money.of(AED, PRINCIPAL_PAID), Money.zero(AED), Money.zero(AED), Money.of(AED, PENALTY));

            final Loan loan = new LoanBuilder(loanProduct).withId(2091L).withLoanStatus(LoanStatus.ACTIVE).withLoanTransaction(sweep)
                    .build();
            final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1, DISBURSEMENT, DUE, PRINCIPAL,
                    INTEREST, BigDecimal.ZERO, BigDecimal.ZERO, PENALTY, false, null, BigDecimal.ZERO);
            installment.payPenaltyChargesComponent(DUE, Money.of(AED, PENALTY));
            installment.payPrincipalComponent(DUE, Money.of(AED, PRINCIPAL_PAID));
            loan.getRepaymentScheduleInstallments().add(installment);

            final LoanCharge charge = mock(LoanCharge.class);
            when(charge.isPenaltyCharge()).thenReturn(true);
            when(charge.getId()).thenReturn(10751L);
            final LoanChargePaidBy paidBy = new LoanChargePaidBy(sweep, charge, PENALTY, 1);
            final Set<LoanChargePaidBy> paidBySet = new HashSet<>();
            paidBySet.add(paidBy);
            when(charge.getLoanChargePaidBySet()).thenReturn(paidBySet);
            when(charge.getOverdueInstallmentCharge()).thenReturn(null);

            final BigDecimal principalOutstandingBefore = installment.getPrincipalOutstanding(AED).getAmount();

            ReversePaidChargeReallocator.reallocate(loan, charge, PENALTY, REVERSAL);

            assertThat(loan.calculateTotalOverpayment().getAmount()).isEqualByComparingTo("0.00");
            assertThat(installment.getPrincipalOutstanding(AED).getAmount())
                    .isEqualByComparingTo(principalOutstandingBefore.subtract(PENALTY));
            assertThat(installment.getPenaltyChargesPaid(AED).getAmount()).isEqualByComparingTo("0.00");
            assertThat(sweep.getPrincipalPortion(AED).getAmount()).isEqualByComparingTo(PRINCIPAL_PAID.add(PENALTY));
            assertThat(sweep.getPenaltyChargesPortion(AED).getAmount()).isEqualByComparingTo("0.00");
        }
    }
}
