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

package com.crediblex.fineract.portfolio.loc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditSummary;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransaction;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guards the fix for the "drawdown does not reduce the LOC limit" bug.
 *
 * <p>
 * Root cause: on a RECEIVABLE drawdown the service used to re-anchor {@code consumed_amount} to
 * {@code SUM(total_outstanding_derived)} immediately. A drawdown is recorded at loan submission, when the new loan has
 * not been disbursed yet and its outstanding is still 0 — so the reservation we had just made was wiped back to 0 and
 * the UI showed no utilisation change. These tests pin the corrected behaviour: DISBURSEMENT reserves the amount and
 * does NOT reconcile; the reconcile only fires on repayment (increment) events, once outstanding is populated.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class LineOfCreditBalanceUpdateServiceReservationTest {

    private static final Long LOC_ID = 4075L;
    private static final Long LOAN_ID = 14301L;

    @Mock
    private LineOfCreditTransactionRepository transactionRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private LineOfCredit lineOfCredit;

    private LineOfCreditBalanceUpdateService service;

    private LineOfCreditSummary summaryWith(BigDecimal consumed, BigDecimal available) {
        final LineOfCreditSummary summary = new LineOfCreditSummary();
        summary.setConsumedAmount(consumed);
        summary.setAvailableBalance(available);
        summary.setBlockedAmount(BigDecimal.ZERO);
        summary.setTotalDrawDownCountDerived(BigDecimal.ZERO);
        return summary;
    }

    private void commonStubs(LineOfCreditSummary summary, LocProductType productType) {
        service = new LineOfCreditBalanceUpdateService(transactionRepository, jdbcTemplate);
        when(lineOfCredit.getId()).thenReturn(LOC_ID);
        when(lineOfCredit.getSummary()).thenReturn(summary);
        when(lineOfCredit.getProductType()).thenReturn(productType);
        when(transactionRepository.findLatestTransaction(anyLong(), any(Pageable.class))).thenReturn(Collections.emptyList());
        when(transactionRepository.saveAndFlush(any(LineOfCreditTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("RECEIVABLE drawdown reserves the amount and does NOT reconcile to outstanding")
    void receivableDrawdown_reservesAmount_withoutReconcile() {
        // Given a receivable LOC of 5,000,000 fully available
        final LineOfCreditSummary summary = summaryWith(BigDecimal.ZERO, new BigDecimal("5000000"));
        commonStubs(summary, LocProductType.RECEIVABLE);

        // When a 4,000 drawdown is created (loan not disbursed yet -> outstanding would be 0)
        service.computeLocBalance(LOAN_ID, LOAN_ID, new BigDecimal("4000"), lineOfCredit, LocalDate.of(2026, 8, 4),
                LineOfCreditTransactionType.DISBURSEMENT);

        // Then the reservation sticks: utilisation reflects the drawdown, available drops by the same amount
        assertEquals(0, new BigDecimal("4000").compareTo(summary.getConsumedAmount()), "consumed should reflect the reservation");
        assertEquals(0, new BigDecimal("4996000").compareTo(summary.getAvailableBalance()), "available should drop by the drawdown");
        // And the outstanding-reconcile query must NOT run on the drawdown path (this is what used to wipe it to 0)
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(BigDecimal.class), any());
    }

    @Test
    @DisplayName("RECEIVABLE repayment reconciles utilisation to live loan outstanding")
    void receivableRepayment_reconcilesToOutstanding() {
        // Given a receivable LOC with 4,000 already consumed
        final LineOfCreditSummary summary = summaryWith(new BigDecimal("4000"), new BigDecimal("4996000"));
        commonStubs(summary, LocProductType.RECEIVABLE);
        when(lineOfCredit.getMaximumAmount()).thenReturn(new BigDecimal("5000000"));
        when(lineOfCredit.getEffectiveDrawableLimit()).thenReturn(new BigDecimal("5000000"));
        // Live outstanding across the LOC's loans is 2,500 -> reconcile should re-anchor consumed to this
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any())).thenReturn(new BigDecimal("2500"));

        // When a 1,000 repayment is applied
        service.computeLocBalance(LOAN_ID, LOAN_ID, new BigDecimal("1000"), lineOfCredit, LocalDate.of(2026, 8, 10),
                LineOfCreditTransactionType.REPAYMENT);

        // Then utilisation is re-anchored to the live outstanding (2,500), not just the incremental 3,000
        verify(jdbcTemplate).queryForObject(anyString(), eq(BigDecimal.class), any());
        assertEquals(0, new BigDecimal("2500").compareTo(summary.getConsumedAmount()), "consumed should re-anchor to outstanding");
        assertEquals(0, new BigDecimal("4997500").compareTo(summary.getAvailableBalance()), "available = limit - reconciled consumed");
    }

    @Test
    @DisplayName("PAYABLE drawdown reserves principal and does not reconcile in this path")
    void payableDrawdown_reservesPrincipal_withoutReconcile() {
        // Given a payable LOC of 750,000 fully available
        final LineOfCreditSummary summary = summaryWith(BigDecimal.ZERO, new BigDecimal("750000"));
        commonStubs(summary, LocProductType.PAYABLE);

        // When an 85,000 drawdown is created
        service.computeLocBalance(LOAN_ID, LOAN_ID, new BigDecimal("85000"), lineOfCredit, LocalDate.of(2026, 6, 12),
                LineOfCreditTransactionType.DISBURSEMENT);

        // Then consumed climbs by the disbursed principal and available drops by the same amount
        assertEquals(0, new BigDecimal("85000").compareTo(summary.getConsumedAmount()), "consumed should climb by disbursed principal");
        assertEquals(0, new BigDecimal("665000").compareTo(summary.getAvailableBalance()), "available should drop by disbursed principal");
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(BigDecimal.class), any());
    }
}
