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

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import com.crediblex.fineract.portfolio.loc.domain.CustomLocStatus;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.junit.jupiter.api.Test;

class LocStatusAggregationUtilsTest {

    private final LoanLineOfCreditParamsRepository repository = mock(LoanLineOfCreditParamsRepository.class);
    private final LocStatusAggregationUtils utils = new LocStatusAggregationUtils(repository);

    @Test
    void closedDrawdownsWithStaleDelinquencyOverlay_rollLineBackToActive() {
        final LineOfCredit loc = loc(133L, LocStatus.ACTIVE, CustomLocStatus.PAST_MATURITY);
        final Loan settled = loan(1579L, LoanStatus.CLOSED_OBLIGATIONS_MET, CustomLoanStatus.PAST_DUE);
        final Loan siblingClosed = loan(1591L, LoanStatus.CLOSED_OBLIGATIONS_MET, CustomLoanStatus.PAST_MATURITY);
        final Loan stillOpen = loan(5581L, LoanStatus.ACTIVE, CustomLoanStatus.INVALID);
        when(repository.findAllByLineOfCredit_Id(133L)).thenReturn(List.of(params(settled), params(siblingClosed), params(stillOpen)));

        final var result = utils.computeLocStatusAggregationData(loc, settled);

        assertThat(result.getOldLocCustomStatus()).isEqualTo(CustomLocStatus.PAST_MATURITY);
        assertThat(result.getNewLocCustomStatus()).isEqualTo(CustomLocStatus.INVALID);
        assertThat(result.getDefaultLocStatus()).isEqualTo(LocStatus.ACTIVE);
        assertThat(loc.getCustomLocStatus()).isEqualTo(CustomLocStatus.INVALID);
    }

    @Test
    void openPastDueDrawdown_keepsLinePastDue() {
        final LineOfCredit loc = loc(133L, LocStatus.ACTIVE, CustomLocStatus.PAST_MATURITY);
        final Loan settled = loan(1579L, LoanStatus.CLOSED_OBLIGATIONS_MET, CustomLoanStatus.PAST_DUE);
        final Loan stillPastDue = loan(5502L, LoanStatus.ACTIVE, CustomLoanStatus.PAST_DUE);
        when(repository.findAllByLineOfCredit_Id(133L)).thenReturn(List.of(params(settled), params(stillPastDue)));

        final var result = utils.computeLocStatusAggregationData(loc, settled);

        assertThat(result.getNewLocCustomStatus()).isEqualTo(CustomLocStatus.PAST_DUE);
    }

    @Test
    void openPastMaturityDrawdown_takesPrecedence() {
        final LineOfCredit loc = loc(133L, LocStatus.ACTIVE, CustomLocStatus.INVALID);
        final Loan pastDue = loan(1L, LoanStatus.ACTIVE, CustomLoanStatus.PAST_DUE);
        final Loan pastMaturity = loan(2L, LoanStatus.ACTIVE, CustomLoanStatus.PAST_MATURITY);
        when(repository.findAllByLineOfCredit_Id(133L)).thenReturn(List.of(params(pastDue), params(pastMaturity)));

        final var result = utils.computeLocStatusAggregationData(loc, pastDue);

        assertThat(result.getNewLocCustomStatus()).isEqualTo(CustomLocStatus.PAST_MATURITY);
    }

    private static LineOfCredit loc(final Long id, final LocStatus status, final CustomLocStatus custom) {
        final LineOfCredit loc = mock(LineOfCredit.class);
        when(loc.getId()).thenReturn(id);
        when(loc.getStatus()).thenReturn(status);
        when(loc.getCustomLocStatus()).thenReturn(custom);
        org.mockito.Mockito.doAnswer(invocation -> {
            when(loc.getCustomLocStatus()).thenReturn(invocation.getArgument(0));
            return null;
        }).when(loc).setCustomLocStatus(org.mockito.ArgumentMatchers.any());
        return loc;
    }

    private static Loan loan(final Long id, final LoanStatus status, final CustomLoanStatus custom) {
        final Loan loan = mock(Loan.class);
        when(loan.getId()).thenReturn(id);
        when(loan.getStatus()).thenReturn(status);
        // Closed drawdowns are skipped before the overlay is read.
        org.mockito.Mockito.lenient().when(loan.getCustomLoanStatus()).thenReturn(custom);
        return loan;
    }

    private static LoanLineOfCreditParams params(final Loan loan) {
        final LoanLineOfCreditParams params = new LoanLineOfCreditParams();
        params.setLoan(loan);
        return params;
    }
}
