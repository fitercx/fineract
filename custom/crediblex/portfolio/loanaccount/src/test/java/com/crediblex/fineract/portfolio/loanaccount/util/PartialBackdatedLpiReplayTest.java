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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.LastEmiSpillPenaltyHookImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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

class PartialBackdatedLpiReplayTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);
    private final LocalDate valueDate = LocalDate.of(2026, 8, 10);

    @BeforeEach
    void setUp() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 9, 28));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "default", "UTC", null));
        final ConfigurationDomainService cfg = mock(ConfigurationDomainService.class);
        when(cfg.getRoundingMode()).thenReturn(4);
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", cfg);
        moneyHelper.initialize();
    }

    @Test
    void realignsOnlyRepaymentsOnOrAfterThePartialValueDateAndClearsTheWindow() {
        final Loan loan = loanWithSchedulePenaltyAbovePayableCharges();
        final LastEmiSpillPenaltyHookImpl hook = new LastEmiSpillPenaltyHookImpl();
        final LoanRepaymentScheduleInstallment installment = loan.getRepaymentScheduleInstallments().get(0);

        PartialBackdatedLpiReplay.aligningFrom(valueDate, () -> {
            hook.foldOntoLastEmi(loan, valueDate.minusDays(1));
            verify(installment, never()).addToChargePortion(any(), any(), any(), any(), any(), any(), any(), any(), any());
            hook.foldOntoLastEmi(loan, valueDate);
            return null;
        });

        verify(installment, times(1)).addToChargePortion(any(), any(), any(), any(), any(), any(), any(), any(), any());
        hook.foldOntoLastEmi(loan, valueDate);
        verify(installment, times(1)).addToChargePortion(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private Loan loanWithSchedulePenaltyAbovePayableCharges() {
        final Loan loan = mock(Loan.class);
        final LoanCharge charge = mock(LoanCharge.class);
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(List.of(charge));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(installment));
        when(charge.isActive()).thenReturn(true);
        when(charge.isOverdueInstallmentCharge()).thenReturn(true);
        when(charge.isWaived()).thenReturn(false);
        when(charge.getDueLocalDate()).thenReturn(valueDate.minusDays(1));
        when(charge.getAmountOutstanding(currency)).thenReturn(Money.of(currency, new BigDecimal("40.00")));
        when(installment.isDownPayment()).thenReturn(false);
        when(installment.isAdditional()).thenReturn(false);
        when(installment.isRecalculatedInterestComponent()).thenReturn(false);
        when(installment.getPrincipal(currency)).thenReturn(Money.zero(currency));
        when(installment.getInterestCharged(currency)).thenReturn(Money.zero(currency));
        when(installment.getPenaltyChargesOutstanding(currency)).thenReturn(Money.of(currency, new BigDecimal("100.00")));
        return loan;
    }
}
