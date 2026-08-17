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
package com.crediblex.fineract.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.commands.repository.EzySqlLoanLocLookupRepository;
import java.util.Optional;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanStatusChangedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit coverage for {@link LoanStatusChangedWebhookListener}: registers itself, fires a core-status webhook (and claims
 * the loan in the dedup guard) on a genuine core transition, and does nothing when the status did not actually change.
 */
@ExtendWith(MockitoExtension.class)
class LoanStatusChangedWebhookListenerTest {

    @Mock
    private BusinessEventNotifierService businessEventNotifierService;
    @Mock
    private LoanStatusWebhookPublisher loanStatusWebhookPublisher;
    @Mock
    private EzySqlLoanLocLookupRepository ezyLoanLocLookupRepository;
    @Mock
    private StatusWebhookTxnDedup dedup;

    @InjectMocks
    private LoanStatusChangedWebhookListener listener;

    @Test
    void registersItselfAsPostListener() {
        listener.addListener();
        verify(businessEventNotifierService).addPostBusinessEventListener(LoanStatusChangedBusinessEvent.class, listener);
    }

    @Test
    void coreTransition_marksDedup_andPublishes() {
        final Loan loan = Mockito.mock(Loan.class);
        when(loan.getId()).thenReturn(7L);
        when(loan.getStatus()).thenReturn(LoanStatus.CLOSED_WRITTEN_OFF);
        when(ezyLoanLocLookupRepository.existsByLoanId(7L)).thenReturn(false);

        // No active transaction in a plain unit test -> the listener publishes immediately.
        listener.onBusinessEvent(new LoanStatusChangedBusinessEvent(loan, LoanStatus.ACTIVE));

        verify(dedup).markCoreEmitted(7L);
        verify(loanStatusWebhookPublisher).publishCoreStatusChange(eq(loan), eq(LoanStatus.ACTIVE), eq(false), eq(Optional.empty()));
    }

    @Test
    void noCoreChange_doesNothing() {
        final Loan loan = Mockito.mock(Loan.class);
        when(loan.getId()).thenReturn(7L);
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);

        listener.onBusinessEvent(new LoanStatusChangedBusinessEvent(loan, LoanStatus.ACTIVE));

        verify(dedup, never()).markCoreEmitted(any());
        verify(loanStatusWebhookPublisher, never()).publishCoreStatusChange(any(), any(), anyBoolean(), any());
    }
}
