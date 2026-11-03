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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.commands.repository.EzySqlLoanLocLookupRepository;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A cleared line overlay (INVALID) must be published as the core line status (ACTIVE) so LOS applies active after the
 * last delinquent drawdown is settled.
 */
@ExtendWith(MockitoExtension.class)
class LineOfCreditStatusWebhookPublisherTest {

    @Mock
    private CredXSynchronousCommandProcessingService credxSyncCommandService;
    @Mock
    private EzySqlLoanLocLookupRepository ezyLoanLocLookupRepository;
    @Mock
    private LoanStatusWebhookTrailRecorder trailRecorder;

    @InjectMocks
    private LineOfCreditStatusWebhookPublisher publisher;

    @Test
    void clearedOverlay_isPublishedAsCoreActiveStatus() {
        final Loan loan = org.mockito.Mockito.mock(Loan.class);
        when(loan.getStatus()).thenReturn(LoanStatus.CLOSED_OBLIGATIONS_MET);
        when(loan.getId()).thenReturn(1579L);
        when(loan.getClientId()).thenReturn(1355L);
        when(loan.getOfficeId()).thenReturn(1L);

        publisher.publish(loan, "ACTIVE", "PAST_MATURITY", "INVALID", true, Optional.of(133L));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(credxSyncCommandService).publishHookEventRaw(eq("LINE_OF_CREDIT"), eq("STATUS_CHANGED"), captor.capture());

        @SuppressWarnings("unchecked")
        final Map<String, Object> response = (Map<String, Object>) captor.getValue().get("response");
        @SuppressWarnings("unchecked")
        final Map<String, Object> changes = (Map<String, Object>) response.get("changes");
        @SuppressWarnings("unchecked")
        final Map<String, Object> custom = (Map<String, Object>) changes.get("customStatus");

        assertThat(changes.get("defaultStatus")).isEqualTo("ACTIVE");
        assertThat(custom.get("oldStatus")).isEqualTo("PAST_MATURITY");
        assertThat(custom.get("newStatus")).isEqualTo("ACTIVE");
        assertThat(response.get("locId")).isEqualTo(133L);
    }

    @Test
    void delinquencyOverlay_isPublishedUnchanged() {
        final Loan loan = org.mockito.Mockito.mock(Loan.class);
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getId()).thenReturn(1579L);
        when(loan.getClientId()).thenReturn(1355L);
        when(loan.getOfficeId()).thenReturn(1L);

        publisher.publish(loan, "ACTIVE", "INVALID", "PAST_MATURITY", true, Optional.of(133L));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(credxSyncCommandService).publishHookEventRaw(eq("LINE_OF_CREDIT"), eq("STATUS_CHANGED"), captor.capture());
        verify(trailRecorder).record(any());

        @SuppressWarnings("unchecked")
        final Map<String, Object> response = (Map<String, Object>) captor.getValue().get("response");
        @SuppressWarnings("unchecked")
        final Map<String, Object> changes = (Map<String, Object>) response.get("changes");
        @SuppressWarnings("unchecked")
        final Map<String, Object> custom = (Map<String, Object>) changes.get("customStatus");

        assertThat(custom.get("newStatus")).isEqualTo("PAST_MATURITY");
    }
}
