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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.CredXTargetedLoanChargeRefundProcessor;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.LoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.MoneyHolder;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.TransactionCtx;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.AdvancedPaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.service.schedule.LoanScheduleComponent;
import org.apache.fineract.portfolio.loanproduct.calc.EMICalculator;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Verifies that refunding a paid LPI is an exact charge undo rather than a principal reallocation. */
class ReversePaidPhantomOverpaymentTest {

    private static final MonetaryCurrency AED = new MonetaryCurrency("AED", 2, 0);
    private static final LocalDate DISBURSEMENT = LocalDate.of(2026, 4, 30);
    private static final LocalDate DUE = LocalDate.of(2026, 7, 29);
    private static final LocalDate REFUND_DATE = LocalDate.of(2026, 7, 30);
    private static final BigDecimal PRINCIPAL = new BigDecimal("776824.04");
    private static final BigDecimal INTEREST = new BigDecimal("28731.85");
    private static final BigDecimal PENALTY = new BigDecimal("483.87");
    private static final BigDecimal PRINCIPAL_PAID = new BigDecimal("216353.21");
    private static final BigDecimal SWEEP = PRINCIPAL_PAID.add(PENALTY);

    private MockedStatic<MoneyHelper> moneyHelperMock;

    @BeforeEach
    void setUp() {
        new CredXTargetedLoanChargeRefundProcessor().register();
        moneyHelperMock = mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
    }

    @AfterEach
    void tearDown() {
        moneyHelperMock.close();
    }

    @Test
    void targetedRefundUnpaysOnlyLpiAndKeepsPrincipalAndOverpaymentBalanced() {
        assertTargetedRefundKeepsPrincipalAndOverpaymentBalanced(
                new InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(mock(ExternalIdFactory.class)));
    }

    @Test
    void progressiveTargetedRefundUnpaysOnlyLpiAndKeepsPrincipalAndOverpaymentBalanced() {
        assertTargetedRefundKeepsPrincipalAndOverpaymentBalanced(new AdvancedPaymentScheduleTransactionProcessor(mock(EMICalculator.class),
                mock(LoanRepositoryWrapper.class), null, mock(ExternalIdFactory.class), mock(LoanScheduleComponent.class)));
    }

    private void assertTargetedRefundKeepsPrincipalAndOverpaymentBalanced(final LoanRepaymentScheduleTransactionProcessor processor) {
        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LoanProductRelatedDetail detail = mock(LoanProductRelatedDetail.class);
        when(loanProduct.getLoanProductRelatedDetail()).thenReturn(detail);
        when(detail.getCurrency()).thenReturn(AED);

        final Money sweepAmount = Money.of(AED, SWEEP);
        final Money penaltyAmount = Money.of(AED, PENALTY);
        final LoanTransaction repayment = mock(LoanTransaction.class);
        when(repayment.isRepaymentLikeType()).thenReturn(true);
        when(repayment.isReversed()).thenReturn(false);
        when(repayment.getAmount(AED)).thenReturn(sweepAmount);

        final Loan loan = new LoanBuilder(loanProduct).withId(2091L).withLoanStatus(LoanStatus.ACTIVE).withLoanTransaction(repayment)
                .build();
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(loan, 1, DISBURSEMENT, DUE, PRINCIPAL,
                INTEREST, BigDecimal.ZERO, BigDecimal.ZERO, PENALTY, false, null, BigDecimal.ZERO);
        installment.payPenaltyChargesComponent(REFUND_DATE, penaltyAmount);
        installment.payPrincipalComponent(REFUND_DATE, Money.of(AED, PRINCIPAL_PAID));
        loan.getRepaymentScheduleInstallments().add(installment);

        final BigDecimal principalOutstandingBefore = installment.getPrincipalOutstanding(AED).getAmount();
        final BigDecimal contractualEmiBefore = installment.getPrincipal(AED).plus(installment.getInterestCharged(AED)).getAmount();
        final BigDecimal latePaidBefore = installment.getTotalPaidLate();
        assertThat(loan.calculateTotalOverpayment().getAmount()).isEqualByComparingTo("0.00");

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class)) {
            dateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(REFUND_DATE);
            dateUtils.when(() -> DateUtils.isAfter(REFUND_DATE, DUE)).thenReturn(true);
            final LoanCharge charge = mock(LoanCharge.class);
            when(charge.isPenaltyCharge()).thenReturn(true);
            when(charge.getAmountPaid(AED)).thenReturn(penaltyAmount);
            when(charge.undoPaidOrPartiallyAmountBy(any(Money.class), eq(1), any(Money.class))).thenReturn(penaltyAmount);

            final LoanTransaction refund = LoanTransaction.repaymentType(LoanTransactionType.REFUND_FOR_ACTIVE_LOAN, mock(Office.class),
                    penaltyAmount, null, REFUND_DATE, ExternalId.empty(), CredXTargetedLoanChargeRefundProcessor.PENALTY);
            final LoanChargePaidBy refundPaidBy = new LoanChargePaidBy(refund, charge, PENALTY.negate(), 1);
            refund.getLoanChargesPaid().add(refundPaidBy);
            loan.addLoanTransaction(refund);

            processor.processLatestTransaction(refund, new TransactionCtx(AED, loan.getRepaymentScheduleInstallments(), Set.of(charge),
                    new MoneyHolder(Money.zero(AED)), new ChangedTransactionDetail()));

            assertThat(installment.getPenaltyChargesPaid(AED).getAmount()).isEqualByComparingTo("0.00");
            assertThat(installment.getPenaltyChargesCharged(AED).getAmount()).isEqualByComparingTo(PENALTY);
            assertThat(installment.getPenaltyChargesOutstanding(AED).getAmount()).isEqualByComparingTo(PENALTY);
            assertThat(installment.getPrincipal(AED).plus(installment.getInterestCharged(AED)).getAmount())
                    .isEqualByComparingTo(contractualEmiBefore);
            assertThat(installment.getPrincipalCompleted(AED).getAmount()).isEqualByComparingTo(PRINCIPAL_PAID);
            assertThat(installment.getPrincipalOutstanding(AED).getAmount()).isEqualByComparingTo(principalOutstandingBefore);
            assertThat(installment.getTotalPaidLate()).isEqualByComparingTo(latePaidBefore.subtract(PENALTY));
            assertThat(refund.getPrincipalPortion(AED).getAmount()).isEqualByComparingTo("0.00");
            assertThat(refund.getInterestPortion(AED).getAmount()).isEqualByComparingTo("0.00");
            assertThat(refund.getPenaltyChargesPortion(AED).getAmount()).isEqualByComparingTo(PENALTY);
            assertThat(loan.calculateTotalOverpayment().getAmount()).isEqualByComparingTo("0.00");
        }
    }
}
