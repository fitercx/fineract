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
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Fix 2 pure-logic helpers: detecting a repayment made exactly on an installment due date, and summing the outstanding
 * LPI (overdue-installment) charges that will be auto-waived for such a settlement. The sum MUST match the set
 * {@code CredXLoanChargeWritePlatformServiceImpl#waiveOverdueChargesInWindow} waives, so the repayment preview equals
 * what is actually settled.
 */
class LocDueDateRepaymentUtilsTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);

    @BeforeEach
    void setUp() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.now());
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "default", "UTC", null));
        final ConfigurationDomainService cfg = mock(ConfigurationDomainService.class);
        when(cfg.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", cfg);
        moneyHelper.initialize();
    }

    private LoanRepaymentScheduleInstallment installment(final LocalDate dueDate) {
        final LoanRepaymentScheduleInstallment i = mock(LoanRepaymentScheduleInstallment.class);
        when(i.getDueDate()).thenReturn(dueDate);
        when(i.isDownPayment()).thenReturn(false);
        when(i.isAdditional()).thenReturn(false);
        when(i.isRecalculatedInterestComponent()).thenReturn(false);
        return i;
    }

    @Test
    void dummyGraceInstallmentDueDateIsIgnoredForOnTimeSettlement() {
        final LoanRepaymentScheduleInstallment emi = installment(LocalDate.of(2026, 8, 2));
        final LoanRepaymentScheduleInstallment dummyGrace = installment(LocalDate.of(2026, 8, 18));
        when(dummyGrace.isRecalculatedInterestComponent()).thenReturn(true);

        final Loan loan = mock(Loan.class);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(emi, dummyGrace));

        assertThat(LocDueDateRepaymentUtils.isOnInstallmentDueDate(loan, LocalDate.of(2026, 8, 2))).isTrue();
        assertThat(LocDueDateRepaymentUtils.isOnInstallmentDueDate(loan, LocalDate.of(2026, 8, 18))).isFalse();
    }

    @Test
    void isOnInstallmentDueDateTrueOnlyForExactMatch() {
        // Build installment mocks first: stubbing a mock inside another when(...).thenReturn(...) confuses Mockito.
        final LoanRepaymentScheduleInstallment i1 = installment(LocalDate.of(2026, 6, 25));
        final LoanRepaymentScheduleInstallment i2 = installment(LocalDate.of(2026, 7, 25));
        final Loan loan = mock(Loan.class);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(i1, i2));
        assertThat(LocDueDateRepaymentUtils.isOnInstallmentDueDate(loan, LocalDate.of(2026, 7, 25))).isTrue();
        assertThat(LocDueDateRepaymentUtils.isOnInstallmentDueDate(loan, LocalDate.of(2026, 7, 24))).isFalse();
        assertThat(LocDueDateRepaymentUtils.isOnInstallmentDueDate(loan, null)).isFalse();
    }

    @Test
    void overdueChargeWaiverFromDateIsInclusiveOnInstallmentDueDate() {
        final LoanRepaymentScheduleInstallment i1 = installment(LocalDate.of(2026, 8, 3));
        final Loan loan = mock(Loan.class);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(i1));

        assertThat(LocDueDateRepaymentUtils.overdueChargeWaiverFromDate(loan, LocalDate.of(2026, 8, 3)))
                .isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(LocDueDateRepaymentUtils.overdueChargeWaiverFromDate(loan, LocalDate.of(2026, 8, 4)))
                .isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(LocDueDateRepaymentUtils.overdueChargeWaiverFromDate(loan, null)).isNull();
    }

    @Test
    void backdatingToDueDateWaivesOvernightLpiPostedTheNextMorning() {
        final LocalDate dueDate = LocalDate.of(2026, 8, 14);
        final LocalDate overnightLpiDate = LocalDate.of(2026, 8, 15);
        final LoanRepaymentScheduleInstallment emi = installment(dueDate);
        final LoanCharge overnightLpi = overdueLpi(overnightLpiDate, "93.20", false, false);
        final Loan loan = mock(Loan.class);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(emi));
        when(loan.getActiveCharges()).thenReturn(Set.of(overnightLpi));

        final LocalDate waiveFrom = LocDueDateRepaymentUtils.overdueChargeWaiverFromDate(loan, dueDate);
        assertThat(waiveFrom).isEqualTo(dueDate);
        final Money waived = LocDueDateRepaymentUtils.sumWaivableOverdueLpi(loan, waiveFrom, overnightLpiDate, currency);
        assertThat(waived.getAmount()).isEqualByComparingTo("93.20");
    }

    private LoanCharge overdueLpi(final LocalDate ownDueDate, final String outstanding, final boolean waived, final boolean paid) {
        final Money outMoney = Money.of(currency, new BigDecimal(outstanding));
        final LoanCharge c = mock(LoanCharge.class);
        when(c.isOverdueInstallmentCharge()).thenReturn(true);
        when(c.getDueLocalDate()).thenReturn(ownDueDate);
        when(c.isWaived()).thenReturn(waived);
        when(c.isPaid()).thenReturn(paid);
        when(c.getAmountOutstanding(currency)).thenReturn(outMoney);
        return c;
    }

    @Test
    void sumsOnlyUnwaivedUnpaidOverdueLpiInWindow() {
        final LocalDate from = LocalDate.of(2026, 7, 25);
        final LocalDate to = LocalDate.of(2026, 7, 31);
        final LoanCharge inWindow = overdueLpi(LocalDate.of(2026, 7, 26), "50.00", false, false); // counted
        final LoanCharge waived = overdueLpi(LocalDate.of(2026, 7, 27), "30.00", true, false); // waived -> excluded
        final LoanCharge paid = overdueLpi(LocalDate.of(2026, 7, 28), "20.00", false, true); // paid -> excluded
        final LoanCharge beforeWindow = overdueLpi(LocalDate.of(2026, 7, 20), "40.00", false, false); // before ->
                                                                                                      // excluded
        final LoanCharge nonLpi = mock(LoanCharge.class); // not overdue-installment -> excluded
        when(nonLpi.isOverdueInstallmentCharge()).thenReturn(false);

        final Loan loan = mock(Loan.class);
        when(loan.getActiveCharges()).thenReturn(Set.of(inWindow, waived, paid, beforeWindow, nonLpi));

        final Money sum = LocDueDateRepaymentUtils.sumWaivableOverdueLpi(loan, from, to, currency);
        assertThat(sum.getAmount()).isEqualByComparingTo("50.00");
    }
}
