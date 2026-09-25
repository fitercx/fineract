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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanDpdStrategySwitch;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanDpdStrategySwitchRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DpdStrategySwitchServiceTest {

    private static final String SWITCHED_CODE = PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE;
    private static final String ORIGINAL_CODE = "mifos-standard-strategy";
    private static final String ORIGINAL_NAME = "Penalties, Fees, Interest, Principal order";
    private static final Long LOAN_ID = 55L;
    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 10);

    private DpdStrategySwitchConfigService configService;
    private DpdMaxDaysPastDueService maxDpdService;
    private LoanDpdStrategySwitchRepository repository;
    private DpdStrategySwitchService underTest;

    private Loan loan;
    private LoanProduct product;

    @BeforeEach
    void setUp() {
        configService = mock(DpdStrategySwitchConfigService.class);
        maxDpdService = mock(DpdMaxDaysPastDueService.class);
        repository = mock(LoanDpdStrategySwitchRepository.class);
        underTest = new DpdStrategySwitchService(configService, maxDpdService, repository);

        product = mock(LoanProduct.class);
        loan = mock(Loan.class);
        when(loan.getId()).thenReturn(LOAN_ID);
        when(loan.loanProduct()).thenReturn(product);
        when(loan.getTransactionProcessingStrategyCode()).thenReturn(ORIGINAL_CODE);
        when(loan.getTransactionProcessingStrategyName()).thenReturn(ORIGINAL_NAME);

        when(configService.isGloballyEnabled()).thenReturn(true);
        when(configService.getThresholdDays()).thenReturn(60);
        when(product.isEnableDpdStrategySwitch()).thenReturn(true);
        when(repository.findByLoanId(LOAN_ID)).thenReturn(Optional.empty());
    }

    @Test
    void switchesLoanOnceMaxDpdExceedsThreshold() {
        when(maxDpdService.calculateMaxDpd(LOAN_ID, AS_OF)).thenReturn(61);

        final String effective = underTest.resolveEffectiveStrategyCode(loan, AS_OF);

        assertThat(effective).isEqualTo(SWITCHED_CODE);
        verify(loan).updateTransactionProcessingStrategy(eq(SWITCHED_CODE), any());
        verify(repository).save(any(LoanDpdStrategySwitch.class));
    }

    @Test
    void doesNotSwitchWhenMaxDpdEqualsThreshold() {
        when(maxDpdService.calculateMaxDpd(LOAN_ID, AS_OF)).thenReturn(60);

        final String effective = underTest.resolveEffectiveStrategyCode(loan, AS_OF);

        assertThat(effective).isEqualTo(ORIGINAL_CODE);
        verify(loan, never()).updateTransactionProcessingStrategy(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void doesNotSwitchWhenProductHasNotOptedIn() {
        when(product.isEnableDpdStrategySwitch()).thenReturn(false);
        when(maxDpdService.calculateMaxDpd(LOAN_ID, AS_OF)).thenReturn(120);

        final String effective = underTest.resolveEffectiveStrategyCode(loan, AS_OF);

        assertThat(effective).isEqualTo(ORIGINAL_CODE);
        verify(loan, never()).updateTransactionProcessingStrategy(any(), any());
    }

    @Test
    void doesNotSwitchWhenGloballyDisabled() {
        when(configService.isGloballyEnabled()).thenReturn(false);
        when(maxDpdService.calculateMaxDpd(LOAN_ID, AS_OF)).thenReturn(120);

        final String effective = underTest.resolveEffectiveStrategyCode(loan, AS_OF);

        assertThat(effective).isEqualTo(ORIGINAL_CODE);
        verify(loan, never()).updateTransactionProcessingStrategy(any(), any());
    }

    @Test
    void revertsToOriginalStrategyOnceLoanRecovers() {
        final LoanDpdStrategySwitch existing = LoanDpdStrategySwitch.newSwitch(LOAN_ID, ORIGINAL_CODE, ORIGINAL_NAME, SWITCHED_CODE, 90,
                AS_OF.minusDays(30));
        when(repository.findByLoanId(LOAN_ID)).thenReturn(Optional.of(existing));
        when(loan.getTransactionProcessingStrategyCode()).thenReturn(SWITCHED_CODE);
        when(maxDpdService.calculateMaxDpd(LOAN_ID, AS_OF)).thenReturn(10);

        final String effective = underTest.resolveEffectiveStrategyCode(loan, AS_OF);

        assertThat(effective).isEqualTo(ORIGINAL_CODE);
        verify(loan).updateTransactionProcessingStrategy(ORIGINAL_CODE, ORIGINAL_NAME);
        assertThat(existing.isSwitched()).isFalse();
        assertThat(existing.getRevertedOnDate()).isEqualTo(AS_OF);
    }

    @Test
    void revertsWhenProductIsOptedOutWhileLoanIsSwitched() {
        final LoanDpdStrategySwitch existing = LoanDpdStrategySwitch.newSwitch(LOAN_ID, ORIGINAL_CODE, ORIGINAL_NAME, SWITCHED_CODE, 90,
                AS_OF.minusDays(30));
        when(repository.findByLoanId(LOAN_ID)).thenReturn(Optional.of(existing));
        when(product.isEnableDpdStrategySwitch()).thenReturn(false);

        final String effective = underTest.resolveEffectiveStrategyCode(loan, AS_OF);

        assertThat(effective).isEqualTo(ORIGINAL_CODE);
        verify(loan).updateTransactionProcessingStrategy(ORIGINAL_CODE, ORIGINAL_NAME);
        assertThat(existing.isSwitched()).isFalse();
    }

    @Test
    void reSwitchIsIdempotentAndKeepsTheRecordedOriginalStrategy() {
        final LoanDpdStrategySwitch existing = LoanDpdStrategySwitch.newSwitch(LOAN_ID, ORIGINAL_CODE, ORIGINAL_NAME, SWITCHED_CODE, 61,
                AS_OF.minusDays(5));
        when(repository.findByLoanId(LOAN_ID)).thenReturn(Optional.of(existing));
        when(loan.getTransactionProcessingStrategyCode()).thenReturn(SWITCHED_CODE);
        when(maxDpdService.calculateMaxDpd(LOAN_ID, AS_OF)).thenReturn(66);

        final String effective = underTest.resolveEffectiveStrategyCode(loan, AS_OF);

        assertThat(effective).isEqualTo(SWITCHED_CODE);
        assertThat(existing.getOriginalStrategyCode()).isEqualTo(ORIGINAL_CODE);
        verify(loan, never()).updateTransactionProcessingStrategy(any(), any());
    }

    @Test
    void reSwitchAfterARevertCapturesTheOriginalStrategyAgain() {
        final LoanDpdStrategySwitch existing = LoanDpdStrategySwitch.newSwitch(LOAN_ID, ORIGINAL_CODE, ORIGINAL_NAME, SWITCHED_CODE, 61,
                AS_OF.minusDays(40));
        existing.markReverted(AS_OF.minusDays(20));
        when(repository.findByLoanId(LOAN_ID)).thenReturn(Optional.of(existing));
        when(maxDpdService.calculateMaxDpd(LOAN_ID, AS_OF)).thenReturn(75);

        final String effective = underTest.resolveEffectiveStrategyCode(loan, AS_OF);

        assertThat(effective).isEqualTo(SWITCHED_CODE);
        assertThat(existing.isSwitched()).isTrue();
        assertThat(existing.getOriginalStrategyCode()).isEqualTo(ORIGINAL_CODE);
        assertThat(existing.getRevertedOnDate()).isNull();
        verify(loan).updateTransactionProcessingStrategy(eq(SWITCHED_CODE), any());
    }

    @Test
    void reevaluateRevertsOnceTheAllocationHasClearedTheArrears() {
        final LoanDpdStrategySwitch existing = LoanDpdStrategySwitch.newSwitch(LOAN_ID, ORIGINAL_CODE, ORIGINAL_NAME, SWITCHED_CODE, 105,
                AS_OF.minusDays(2));
        when(repository.findByLoanId(LOAN_ID)).thenReturn(Optional.of(existing));
        when(loan.getTransactionProcessingStrategyCode()).thenReturn(SWITCHED_CODE);
        // The repayment that just landed brought the loan current, so the in-memory schedule reports no arrears even
        // though the committed rows the SQL measure reads still show the loan deep in arrears.
        when(maxDpdService.calculateMaxDpd(loan, AS_OF)).thenReturn(0);
        when(maxDpdService.calculateMaxDpd(LOAN_ID, AS_OF)).thenReturn(105);

        final String effective = underTest.reevaluate(loan, AS_OF);

        assertThat(effective).isEqualTo(ORIGINAL_CODE);
        verify(loan).updateTransactionProcessingStrategy(ORIGINAL_CODE, ORIGINAL_NAME);
        assertThat(existing.isSwitched()).isFalse();
        assertThat(existing.getRevertedOnDate()).isEqualTo(AS_OF);
        verify(maxDpdService, never()).calculateMaxDpd(eq(LOAN_ID), any());
    }

    @Test
    void reevaluateSwitchesADelinquentLoanThatHasNotTransacted() {
        when(maxDpdService.calculateMaxDpd(loan, AS_OF)).thenReturn(105);

        final String effective = underTest.reevaluate(loan, AS_OF);

        assertThat(effective).isEqualTo(SWITCHED_CODE);
        verify(loan).updateTransactionProcessingStrategy(eq(SWITCHED_CODE), any());
        verify(repository).save(any(LoanDpdStrategySwitch.class));
    }

    @Test
    void reevaluateKeepsTheLoanSwitchedWhileItIsStillPastDue() {
        final LoanDpdStrategySwitch existing = LoanDpdStrategySwitch.newSwitch(LOAN_ID, ORIGINAL_CODE, ORIGINAL_NAME, SWITCHED_CODE, 105,
                AS_OF.minusDays(2));
        when(repository.findByLoanId(LOAN_ID)).thenReturn(Optional.of(existing));
        when(loan.getTransactionProcessingStrategyCode()).thenReturn(SWITCHED_CODE);
        when(maxDpdService.calculateMaxDpd(loan, AS_OF)).thenReturn(61);

        final String effective = underTest.reevaluate(loan, AS_OF);

        assertThat(effective).isEqualTo(SWITCHED_CODE);
        assertThat(existing.isSwitched()).isTrue();
        assertThat(existing.getOriginalStrategyCode()).isEqualTo(ORIGINAL_CODE);
        assertThat(existing.getRevertedOnDate()).isNull();
    }

    @Test
    void doesNothingWhenTheProductAlreadyRepaysInTheSwitchedOrder() {
        when(loan.getTransactionProcessingStrategyCode()).thenReturn(SWITCHED_CODE);
        when(maxDpdService.calculateMaxDpd(LOAN_ID, AS_OF)).thenReturn(120);

        final String effective = underTest.resolveEffectiveStrategyCode(loan, AS_OF);

        assertThat(effective).isEqualTo(SWITCHED_CODE);
        verify(repository, never()).save(any());
        verify(loan, never()).updateTransactionProcessingStrategy(any(), any());
    }
}
