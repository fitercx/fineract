/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanInstallmentInterestSnapshot;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanInstallmentInterestSnapshotRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class EarlyRepaymentInterestHookImplTest {

    private static MockedStatic<MoneyHelper> moneyHelperMock;

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, null);
    private final Map<Long, LoanInstallmentInterestSnapshot> snapshots = new HashMap<>();
    private LoanInstallmentInterestSnapshotRepository snapshotRepository;
    private EarlyRepaymentInterestHookImpl hook;

    @BeforeAll
    static void mockMoneyHelper() {
        moneyHelperMock = mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_UP);
        moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new MathContext(19, RoundingMode.HALF_UP));
    }

    @AfterAll
    static void closeMoneyHelperMock() {
        moneyHelperMock.close();
    }

    @BeforeEach
    void setUp() {
        snapshots.clear();
        snapshotRepository = Mockito.mock(LoanInstallmentInterestSnapshotRepository.class);
        Mockito.when(snapshotRepository.findById(Mockito.any())).thenAnswer(inv -> Optional.ofNullable(snapshots.get(inv.getArgument(0))));
        Mockito.when(snapshotRepository.save(Mockito.any())).thenAnswer(inv -> {
            final LoanInstallmentInterestSnapshot snapshot = inv.getArgument(0);
            snapshots.put(snapshot.getLoanRepaymentScheduleId(), snapshot);
            return snapshot;
        });
        Mockito.doAnswer(inv -> {
            final LoanInstallmentInterestSnapshot snapshot = inv.getArgument(0);
            snapshots.remove(snapshot.getLoanRepaymentScheduleId());
            return null;
        }).when(snapshotRepository).delete(Mockito.any());
        hook = new EarlyRepaymentInterestHookImpl(snapshotRepository);
    }

    private LoanRepaymentScheduleInstallment newInstallment(final Long id) {
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(null, 1, LocalDate.of(2026, 6, 23),
                LocalDate.of(2026, 7, 23), new BigDecimal("17353.34"), new BigDecimal("270.28"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false, null);
        installment.setId(id);
        return installment;
    }

    @Test
    void reduceForEarlyPayment_storesSnapshotAndReducesCharged() {
        final LoanRepaymentScheduleInstallment installment = newInstallment(1L);

        hook.reduceForEarlyPayment(installment, LocalDate.of(2026, 7, 21), currency);

        assertTrue(snapshots.containsKey(1L));
        assertEquals(0, new BigDecimal("270.28").compareTo(snapshots.get(1L).getInterestChargedOriginal()));
        final BigDecimal expected = new BigDecimal("270.28").multiply(BigDecimal.valueOf(28)).divide(BigDecimal.valueOf(30), 2,
                RoundingMode.HALF_UP);
        assertEquals(0, expected.compareTo(installment.getInterestCharged(currency).getAmount()));
    }

    @Test
    void restoreBeforeReprocessing_restoresChargedAndDeletesSnapshot() {
        final LoanRepaymentScheduleInstallment installment = newInstallment(1L);

        hook.reduceForEarlyPayment(installment, LocalDate.of(2026, 7, 21), currency);
        hook.restoreBeforeReprocessing(installment);

        assertTrue(snapshots.isEmpty());
        assertEquals(0, new BigDecimal("270.28").compareTo(installment.getInterestCharged(currency).getAmount()));
    }

    @Test
    void restoreBeforeReprocessing_flushesImmediatelyAfterDelete_soSameIdCanBeSavedAgainInTheSameReprocessingPass() {
        // Regression test for BUG_REPORT.md Finding #0: AbstractLoanRepaymentScheduleTransactionProcessor calls
        // restoreBeforeReprocessing() for every installment, then later - within the SAME reprocessing pass/
        // transaction - calls reduceForEarlyPayment() again for any installment that still has an early-payment
        // transaction. That re-save must not collide with the just-deleted row of the same assigned id, which is
        // exactly what a delete()-without-flush leaves a real JPA provider vulnerable to.
        final LoanRepaymentScheduleInstallment installment = newInstallment(1L);

        hook.reduceForEarlyPayment(installment, LocalDate.of(2026, 7, 21), currency);
        hook.restoreBeforeReprocessing(installment);
        Mockito.verify(snapshotRepository).flush();

        // Replaying the same early payment within the same pass must succeed cleanly (no leftover snapshot state
        // from before the restore/delete should cause a collision or a stale/duplicate row).
        hook.reduceForEarlyPayment(installment, LocalDate.of(2026, 7, 21), currency);
        assertEquals(1, snapshots.size());
        assertEquals(0, new BigDecimal("270.28").compareTo(snapshots.get(1L).getInterestChargedOriginal()));
    }

    @Test
    void reduceForEarlyPayment_withNullInstallmentId_isNoOp() {
        final LoanRepaymentScheduleInstallment installment = newInstallment(null);

        hook.reduceForEarlyPayment(installment, LocalDate.of(2026, 7, 21), currency);

        assertTrue(snapshots.isEmpty());
        assertEquals(0, new BigDecimal("270.28").compareTo(installment.getInterestCharged(currency).getAmount()));
    }

    @Test
    void reduceForEarlyPayment_onOrAfterDueDate_isNoOp() {
        final LoanRepaymentScheduleInstallment installment = newInstallment(1L);

        hook.reduceForEarlyPayment(installment, LocalDate.of(2026, 7, 23), currency);

        assertTrue(snapshots.isEmpty());
        assertEquals(0, new BigDecimal("270.28").compareTo(installment.getInterestCharged(currency).getAmount()));
    }
}
