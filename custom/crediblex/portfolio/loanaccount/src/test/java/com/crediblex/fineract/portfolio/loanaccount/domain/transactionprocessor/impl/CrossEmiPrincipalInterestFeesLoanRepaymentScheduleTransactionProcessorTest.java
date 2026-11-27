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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.dpdrepayment.domain.transactionprocessor.DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor;
import com.crediblex.fineract.portfolio.loanaccount.util.CrossEmiOverdueRepayment;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.ChangedTransactionDetail;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.MoneyHolder;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.TransactionCtx;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessorTest {

    private static final MonetaryCurrency CURRENCY = new MonetaryCurrency("AED", 2, 1);
    private static final MockedStatic<MoneyHelper> MONEY_HELPER = Mockito.mockStatic(MoneyHelper.class);
    private static final LocalDate DISBURSEMENT = LocalDate.of(2026, 1, 1);
    private static final LocalDate EMI1_DUE = LocalDate.of(2026, 2, 1);
    private static final LocalDate EMI2_DUE = LocalDate.of(2026, 3, 1);
    private static final LocalDate EMI3_DUE = LocalDate.of(2026, 4, 1);
    private static final LocalDate ON_TIME = EMI2_DUE;
    /** Day after EMI 2 is due, so EMI 1 and EMI 2 are overdue and EMI 3 is not. */
    private static final LocalDate BOTH_OVERDUE = EMI2_DUE.plusDays(1);

    private CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor processor;
    private Loan loan;
    private Office office;

    @BeforeAll
    static void initMoneyHelper() {
        MONEY_HELPER.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        MONEY_HELPER.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
    }

    @AfterAll
    static void closeMoneyHelper() {
        MONEY_HELPER.close();
    }

    @BeforeEach
    void setUp() {
        this.processor = new CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor(mock(ExternalIdFactory.class));
        this.office = mock(Office.class);
        this.loan = mock(Loan.class);
        when(this.loan.isReceivableLocLoan()).thenReturn(false);
        when(this.loan.isForeclosure()).thenReturn(false);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Dubai", null));
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, ON_TIME)));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void paymentOf15000FillsEmi1PrincipalThenEmi2Principal() {
        final List<LoanRepaymentScheduleInstallment> installments = ticketInstallments();

        final LoanTransaction repayment = pay(this.processor, installments, "15000", BOTH_OVERDUE);

        assertPortion(repayment, "15000", "0", "0", "0");
        assertMapping(repayment, 1, "10000", "0", "0", "0");
        assertMapping(repayment, 2, "5000", "0", "0", "0");
        assertOutstanding(installments.get(0), "0", "1000", "100", "0");
        assertOutstanding(installments.get(1), "5000", "800", "50", "20");
    }

    @Test
    void paymentOf21000ClearsPrincipalThenStartsInterestOnTheEarliestEmi() {
        final List<LoanRepaymentScheduleInstallment> installments = ticketInstallments();

        final LoanTransaction repayment = pay(this.processor, installments, "21000", BOTH_OVERDUE);

        assertPortion(repayment, "20000", "1000", "0", "0");
        assertMapping(repayment, 1, "10000", "1000", "0", "0");
        assertMapping(repayment, 2, "10000", "0", "0", "0");
        assertOutstanding(installments.get(1), "0", "800", "50", "20");
    }

    @Test
    void paymentOf21950ClearsPrincipalInterestAndFeesAndLeavesPenalties() {
        final List<LoanRepaymentScheduleInstallment> installments = ticketInstallments();

        final LoanTransaction repayment = pay(this.processor, installments, "21950", BOTH_OVERDUE);

        assertPortion(repayment, "20000", "1800", "150", "0");
        assertMapping(repayment, 1, "10000", "1000", "100", "0");
        assertMapping(repayment, 2, "10000", "800", "50", "0");
        assertOutstanding(installments.get(0), "0", "0", "0", "0");
        assertOutstanding(installments.get(1), "0", "0", "0", "20");
    }

    @Test
    void penaltiesStartOnTheEarliestEmiOnlyAfterFeesAreGone() {
        final List<LoanRepaymentScheduleInstallment> installments = ticketInstallments();

        final LoanTransaction repayment = pay(this.processor, installments, "21970", BOTH_OVERDUE);

        assertPortion(repayment, "20000", "1800", "150", "20");
        assertMapping(repayment, 2, "10000", "800", "50", "20");
    }

    @Test
    void paymentStaysOnOverdueInstallmentsAndDoesNotTouchOneThatIsNotYetDue() {
        final List<LoanRepaymentScheduleInstallment> installments = ticketInstallments();

        final LoanTransaction onlyFirstOverdue = pay(this.processor, installments, "15000", EMI1_DUE.plusDays(1));

        assertPortion(onlyFirstOverdue, "10000", "1000", "100", "0");
        assertOutstanding(installments.get(1), "10000", "800", "50", "20");

        final List<LoanRepaymentScheduleInstallment> bothOverdue = ticketInstallments();
        final LoanTransaction acrossOverdue = pay(this.processor, bothOverdue, "15000", BOTH_OVERDUE);
        assertPortion(acrossOverdue, "15000", "0", "0", "0");
        assertMapping(acrossOverdue, 1, "10000", "0", "0", "0");
        assertMapping(acrossOverdue, 2, "5000", "0", "0", "0");
    }

    @Test
    void anEmiWithNoPrincipalStillWaitsUntilLaterPrincipalIsPaid() {
        final LoanRepaymentScheduleInstallment emi1 = installment(1, DISBURSEMENT, EMI1_DUE, "0", "1000", "100", "50");
        final LoanRepaymentScheduleInstallment emi2 = installment(2, EMI1_DUE, EMI2_DUE, "10000", "800", "50", "40");
        final LoanRepaymentScheduleInstallment emi3 = installment(3, EMI2_DUE, EMI3_DUE, "10000", "700", "40", "0");
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(List.of(emi1, emi2, emi3));

        final LoanTransaction repayment = pay(this.processor, installments, "5000", BOTH_OVERDUE);

        assertPortion(repayment, "5000", "0", "0", "0");
        assertMapping(repayment, 2, "5000", "0", "0", "0");
        assertOutstanding(emi1, "0", "1000", "100", "50");
        assertOutstanding(emi3, "10000", "700", "40", "0");
    }

    @Test
    void pipfStillPaysEmi1InterestBeforeEmi2Principal() {
        final PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor pipf = new PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(
                mock(ExternalIdFactory.class));
        final List<LoanRepaymentScheduleInstallment> installments = ticketInstallments();

        final LoanTransaction repayment = pay(pipf, installments, "15000", ON_TIME);

        assertPortion(repayment, "13900", "1000", "100", "0");
        assertMapping(repayment, 1, "10000", "1000", "100", "0");
        assertMapping(repayment, 2, "3900", "0", "0", "0");
    }

    @Test
    void afterOverduePrincipalIsPaidTheNextPaymentPaysOverdueInterestNotAFutureEmi() {
        final List<LoanRepaymentScheduleInstallment> installments = threeEmiSchedule();
        final LoanTransaction first = pay(this.processor, installments, "20000", BOTH_OVERDUE);
        assertPortion(first, "20000", "0", "0", "0");

        final LoanTransaction second = pay(this.processor, installments, "1000", BOTH_OVERDUE);

        assertPortion(first, "20000", "0", "0", "0");
        assertPortion(second, "0", "1000", "0", "0");
        assertMapping(second, 1, "0", "1000", "0", "0");
        assertOutstanding(installments.get(2), "10000", "700", "40", "0");
    }

    @Test
    void afterPrincipalOnlyPaymentsSwitchingToInterestFirstPaysEmi1InterestThenPenaltyThenFee() {
        final DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor principalOnly = new DpdPrincipalOnlyLoanRepaymentScheduleTransactionProcessor(
                mock(ExternalIdFactory.class));
        final InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor interestFirst = new InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(
                mock(ExternalIdFactory.class));
        final List<LoanRepaymentScheduleInstallment> installments = threeEmiSchedule();
        final LoanTransaction principalPayment = pay(principalOnly, installments, "20000", ON_TIME);
        assertPortion(principalPayment, "20000", "0", "0", "0");

        final LoanTransaction interestPayment = pay(interestFirst, installments, "1150", ON_TIME.plusDays(1));

        assertPortion(principalPayment, "20000", "0", "0", "0");
        assertPortion(interestPayment, "0", "1000", "100", "50");
        assertMapping(interestPayment, 1, "0", "1000", "100", "50");
        assertOutstanding(installments.get(2), "10000", "700", "40", "0");
        assertOutstanding(installments.get(1), "0", "800", "50", "40");
    }

    @Test
    void repaymentAboveTheOverdueTotalIsRejectedAndACurrentLoanCannotBeRepaid() {
        final Loan overdueLoan = mock(Loan.class);
        when(overdueLoan.getCurrency()).thenReturn(CURRENCY);
        when(overdueLoan.getTransactionProcessingStrategyCode())
                .thenReturn(CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE);
        when(overdueLoan.getRepaymentScheduleInstallments()).thenReturn(ticketInstallments());

        final PlatformApiDataValidationException exceeds = assertThrows(PlatformApiDataValidationException.class,
                () -> CrossEmiOverdueRepayment.validateRepayment(overdueLoan, EMI2_DUE, new BigDecimal("11100.01")));
        assertEquals(CrossEmiOverdueRepayment.AMOUNT_EXCEEDS_OVERDUE, exceeds.getErrors().get(0).getUserMessageGlobalisationCode());
        CrossEmiOverdueRepayment.validateRepayment(overdueLoan, EMI2_DUE, new BigDecimal("11100.00"));

        final PlatformApiDataValidationException cleared = assertThrows(PlatformApiDataValidationException.class,
                () -> CrossEmiOverdueRepayment.validateRepayment(overdueLoan, EMI1_DUE, new BigDecimal("1")));
        assertEquals(CrossEmiOverdueRepayment.OVERDUE_CLEARED, cleared.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    @Test
    void reprocessAfterAStrategyChangeKeepsThePostedPrincipalAllocation() throws Exception {
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>(
                List.of(installment(1, DISBURSEMENT, EMI1_DUE, "10000", "1000", "100", "50"),
                        installment(2, EMI1_DUE, EMI2_DUE, "10000", "800", "50", "40")));
        final LoanTransaction repayment = pay(this.processor, installments, "20000", BOTH_OVERDUE);
        setEntityId(repayment, 100L);
        repayment.setManuallyAdjustedOrReversed();

        final InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor interestFirst = new InterestPrincipalPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor(
                mock(ExternalIdFactory.class));
        final ChangedTransactionDetail changes = interestFirst.reprocessLoanTransactions(DISBURSEMENT, List.of(repayment), CURRENCY,
                installments, new HashSet<LoanCharge>());

        assertTrue(repayment.isNotReversed());
        assertTrue(changes.getTransactionChanges().isEmpty());
        assertPortion(repayment, "20000", "0", "0", "0");
        assertEquals(0, new BigDecimal("10000").compareTo(installments.get(0).getPrincipalCompleted(CURRENCY).getAmount()));
        assertEquals(0, new BigDecimal("10000").compareTo(installments.get(1).getPrincipalCompleted(CURRENCY).getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(installments.get(0).getInterestPaid(CURRENCY).getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(installments.get(0).getFeeChargesPaid(CURRENCY).getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(installments.get(0).getPenaltyChargesPaid(CURRENCY).getAmount()));
    }

    private List<LoanRepaymentScheduleInstallment> ticketInstallments() {
        return new ArrayList<>(List.of(installment(1, DISBURSEMENT, EMI1_DUE, "10000", "1000", "100", "0"),
                installment(2, EMI1_DUE, EMI2_DUE, "10000", "800", "50", "20")));
    }

    private List<LoanRepaymentScheduleInstallment> threeEmiSchedule() {
        return new ArrayList<>(List.of(installment(1, DISBURSEMENT, EMI1_DUE, "10000", "1000", "100", "50"),
                installment(2, EMI1_DUE, EMI2_DUE, "10000", "800", "50", "40"),
                installment(3, EMI2_DUE, EMI3_DUE, "10000", "700", "40", "0")));
    }

    private LoanRepaymentScheduleInstallment installment(final int number, final LocalDate from, final LocalDate due,
            final String principal, final String interest, final String fee, final String penalty) {
        return new LoanRepaymentScheduleInstallment(this.loan, number, from, due, new BigDecimal(principal), new BigDecimal(interest),
                new BigDecimal(fee), BigDecimal.ZERO, new BigDecimal(penalty), false, null, BigDecimal.ZERO);
    }

    private LoanTransaction pay(
            final org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.AbstractLoanRepaymentScheduleTransactionProcessor processor,
            final List<LoanRepaymentScheduleInstallment> installments, final String amount, final LocalDate date) {
        final LoanTransaction repayment = LoanTransaction.repayment(this.office, Money.of(CURRENCY, new BigDecimal(amount)), null, date,
                ExternalId.empty());
        repayment.updateLoan(this.loan);
        processor.processLatestTransaction(repayment,
                new TransactionCtx(CURRENCY, installments, new HashSet<>(), new MoneyHolder(Money.zero(CURRENCY)), null));
        return repayment;
    }

    private static void assertPortion(final LoanTransaction repayment, final String principal, final String interest, final String fee,
            final String penalty) {
        assertMoney(repayment.getPrincipalPortion(), principal);
        assertMoney(repayment.getInterestPortion(), interest);
        assertMoney(repayment.getFeeChargesPortion(), fee);
        assertMoney(repayment.getPenaltyChargesPortion(), penalty);
    }

    private static void assertMapping(final LoanTransaction repayment, final int installmentNumber, final String principal,
            final String interest, final String fee, final String penalty) {
        final LoanTransactionToRepaymentScheduleMapping mapping = repayment.getLoanTransactionToRepaymentScheduleMappings().stream()
                .filter(row -> row.getLoanRepaymentScheduleInstallment().getInstallmentNumber() == installmentNumber).findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal(principal).compareTo(mapping.getPrincipalPortion(CURRENCY).getAmount()));
        assertEquals(0, new BigDecimal(interest).compareTo(mapping.getInterestPortion(CURRENCY).getAmount()));
        assertEquals(0, new BigDecimal(fee).compareTo(mapping.getFeeChargesPortion(CURRENCY).getAmount()));
        assertEquals(0, new BigDecimal(penalty).compareTo(mapping.getPenaltyChargesPortion(CURRENCY).getAmount()));
    }

    private static void assertOutstanding(final LoanRepaymentScheduleInstallment installment, final String principal, final String interest,
            final String fee, final String penalty) {
        assertEquals(0, new BigDecimal(principal).compareTo(installment.getPrincipalOutstanding(CURRENCY).getAmount()));
        assertEquals(0, new BigDecimal(interest).compareTo(installment.getInterestOutstanding(CURRENCY).getAmount()));
        assertEquals(0, new BigDecimal(fee).compareTo(installment.getFeeChargesOutstanding(CURRENCY).getAmount()));
        assertEquals(0, new BigDecimal(penalty).compareTo(installment.getPenaltyChargesOutstanding(CURRENCY).getAmount()));
    }

    private static void assertMoney(final BigDecimal actual, final String expected) {
        final BigDecimal value = actual == null ? BigDecimal.ZERO : actual;
        assertEquals(0, new BigDecimal(expected).compareTo(value), "expected " + expected + " but was " + value);
    }

    private static void setEntityId(final Object entity, final Long id) throws Exception {
        final java.lang.reflect.Field idField = org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom.class
                .getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
