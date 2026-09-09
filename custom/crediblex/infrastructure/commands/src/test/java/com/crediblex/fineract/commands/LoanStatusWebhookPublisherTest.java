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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.commands.repository.EzySqlLoanLocLookupRepository;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit coverage for {@link LoanStatusWebhookPublisher}: core-status webhooks fire with old -> new default status; the
 * overlay path is suppressed when a core webhook already fired this transaction, suppressed when the overlay did not
 * change, and fires for genuine delinquency-only changes; every attempt is trailed.
 */
@ExtendWith(MockitoExtension.class)
class LoanStatusWebhookPublisherTest {

    @Mock
    private CredXSynchronousCommandProcessingService credxSyncCommandService;
    @Mock
    private EzySqlLoanLocLookupRepository ezyLoanLocLookupRepository;
    @Mock
    private LoanStatusWebhookTrailRecorder trailRecorder;
    @Mock
    private StatusWebhookTxnDedup dedup;

    @InjectMocks
    private LoanStatusWebhookPublisher publisher;

    private Loan loan(final Long id, final LoanStatus status, final CustomLoanStatus custom) {
        final Loan loan = org.mockito.Mockito.mock(Loan.class);
        // getId() is not reached on the early-return (custom-unchanged) path, so keep it lenient.
        org.mockito.Mockito.lenient().when(loan.getId()).thenReturn(id);
        when(loan.getStatus()).thenReturn(status);
        lenientClientOffice(loan);
        when(loan.hasCustomStatus()).thenReturn(custom != null);
        if (custom != null) {
            when(loan.getCustomLoanStatus()).thenReturn(custom);
        }
        return loan;
    }

    private void lenientClientOffice(final Loan loan) {
        org.mockito.Mockito.lenient().when(loan.getClientId()).thenReturn(11L);
        org.mockito.Mockito.lenient().when(loan.getOfficeId()).thenReturn(22L);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePayload() {
        final ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(credxSyncCommandService).publishHookEventRaw(eq("LOAN"), eq("STATUS_CHANGED"), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> changesOf(final Map<String, Object> payload) {
        final Map<String, Object> response = (Map<String, Object>) payload.get("response");
        return (Map<String, Object>) response.get("changes");
    }

    @Test
    void coreStatusChange_fires_withOldAndNewDefaultStatus_andTrail() {
        // Active -> Closed (obligations met): the previously-undelivered post-active transition.
        final Loan loan = loan(1L, LoanStatus.CLOSED_OBLIGATIONS_MET, null);

        publisher.publishCoreStatusChange(loan, LoanStatus.ACTIVE, false, Optional.empty());

        final Map<String, Object> changes = changesOf(capturePayload());
        assertThat(changes.get("defaultStatus")).isEqualTo("CLOSED_OBLIGATIONS_MET");
        assertThat(changes.get("defaultStatusCode")).isEqualTo(600);
        assertThat(changes.get("oldDefaultStatus")).isEqualTo("ACTIVE");
        assertThat(changes.get("oldDefaultStatusCode")).isEqualTo(300);

        final ArgumentCaptor<WebhookTrailEntry> trail = ArgumentCaptor.forClass(WebhookTrailEntry.class);
        verify(trailRecorder).record(trail.capture());
        assertThat(trail.getValue().isDispatched()).isTrue();
        assertThat(trail.getValue().getOldCoreStatus()).isEqualTo("ACTIVE");
        assertThat(trail.getValue().getNewCoreStatus()).isEqualTo("CLOSED_OBLIGATIONS_MET");
        assertThat(trail.getValue().getTriggerSource()).isEqualTo("CORE_STATUS_CHANGE");
    }

    @Test
    void overlayChange_isSuppressed_whenCoreWebhookAlreadyFiredThisTxn() {
        final Loan loan = loan(1L, LoanStatus.ACTIVE, CustomLoanStatus.PAST_DUE);
        when(dedup.wasCoreEmitted(1L)).thenReturn(true);

        publisher.publish(loan, CustomLoanStatus.INVALID, false, Optional.empty());

        verify(credxSyncCommandService, never()).publishHookEventRaw(any(), any(), any());
        verify(trailRecorder, never()).record(any());
    }

    @Test
    void overlayChange_fires_forDelinquencyOnlyChange() {
        final Loan loan = loan(1L, LoanStatus.ACTIVE, CustomLoanStatus.PAST_DUE);
        when(dedup.wasCoreEmitted(1L)).thenReturn(false);

        publisher.publish(loan, CustomLoanStatus.INVALID, false, Optional.empty());

        final Map<String, Object> changes = changesOf(capturePayload());
        assertThat(changes.get("defaultStatus")).isEqualTo("ACTIVE");
        @SuppressWarnings("unchecked")
        final Map<String, Object> custom = (Map<String, Object>) changes.get("customStatus");
        assertThat(custom.get("oldStatus")).isEqualTo("INVALID");
        assertThat(custom.get("newStatus")).isEqualTo("PAST_DUE");

        final ArgumentCaptor<WebhookTrailEntry> trail = ArgumentCaptor.forClass(WebhookTrailEntry.class);
        verify(trailRecorder).record(trail.capture());
        assertThat(trail.getValue().getTriggerSource()).isEqualTo("CUSTOM_STATUS_CHANGE");
    }

    @Test
    void overlayChange_isSuppressed_whenCustomStatusUnchanged() {
        final Loan loan = loan(1L, LoanStatus.CLOSED_OBLIGATIONS_MET, CustomLoanStatus.INVALID);

        // old == new overlay (INVALID -> INVALID): the historical bug scenario. Overlay path must not fire;
        // the core listener is responsible for this transition instead.
        publisher.publish(loan, CustomLoanStatus.INVALID, false, Optional.empty());

        verify(credxSyncCommandService, never()).publishHookEventRaw(any(), any(), any());
    }
}
