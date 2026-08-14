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

import com.crediblex.fineract.commands.repository.EzySqlLoanLocLookupRepository;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.event.business.BusinessEventListener;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanStatusChangedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Central emitter of the {@code LOAN / STATUS_CHANGED} webhook for <b>core</b> Fineract status transitions.
 *
 * <p>
 * Fineract's loan state machine raises a {@link LoanStatusChangedBusinessEvent} on every core status transition
 * (approve, reject, withdraw, disburse, obligations-met, overpaid, written-off, rescheduled, foreclosure, ... —
 * everything except initial loan creation). Subscribing here means a single component covers <em>all</em> of those
 * paths, including the many that were previously never wired to a publisher (write-off, manual close, reschedule,
 * charge payment, refunds). This replaces per-method wiring and permanently closes the "post-active statuses not
 * delivered" gap.
 *
 * <p>
 * The event is raised while the command transaction is still open, so we:
 * <ol>
 * <li>mark the loan in {@link StatusWebhookTxnDedup} synchronously, so the legacy overlay publishers (which run in
 * their own {@code afterCommit} callbacks later) skip and we don't double-send; and</li>
 * <li>defer the actual publish to {@code afterCommit}, so we never emit a webhook for a transaction that rolls
 * back.</li>
 * </ol>
 *
 * <p>
 * Custom-overlay-only changes (e.g. an active loan going {@code INVALID -> PAST_DUE}) do not raise this event because
 * the core status is unchanged; those continue to be emitted by {@link LoanStatusWebhookPublisher#publish} as before,
 * giving the "fire on core OR custom change" behaviour.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanStatusChangedWebhookListener implements BusinessEventListener<LoanStatusChangedBusinessEvent> {

    private final BusinessEventNotifierService businessEventNotifierService;
    private final LoanStatusWebhookPublisher loanStatusWebhookPublisher;
    private final EzySqlLoanLocLookupRepository ezyLoanLocLookupRepository;
    private final StatusWebhookTxnDedup dedup;

    @PostConstruct
    public void addListener() {
        businessEventNotifierService.addPostBusinessEventListener(LoanStatusChangedBusinessEvent.class, this);
    }

    @Override
    public void onBusinessEvent(final LoanStatusChangedBusinessEvent event) {
        final Loan loan = event.get();
        if (loan == null || loan.getId() == null || loan.getStatus() == null) {
            return;
        }
        final LoanStatus oldStatus = event.getOldStatus();
        final LoanStatus newStatus = loan.getStatus();
        if (oldStatus != null && oldStatus == newStatus) {
            // Defensive: not a real core change, nothing to notify.
            return;
        }

        // Claim this loan for the current transaction so the overlay publishers (later, in afterCommit) don't
        // double-send.
        dedup.markCoreEmitted(loan.getId());

        final boolean isDrawdown = ezyLoanLocLookupRepository.existsByLoanId(loan.getId());
        final Optional<Long> locId = isDrawdown ? ezyLoanLocLookupRepository.findLocIdByLoanId(loan.getId()) : Optional.empty();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    safePublish(loan, oldStatus, isDrawdown, locId);
                }
            });
        } else {
            // No active transaction (e.g. tests) — publish immediately.
            safePublish(loan, oldStatus, isDrawdown, locId);
        }
    }

    private void safePublish(final Loan loan, final LoanStatus oldStatus, final boolean isDrawdown, final Optional<Long> locId) {
        try {
            loanStatusWebhookPublisher.publishCoreStatusChange(loan, oldStatus, isDrawdown, locId);
        } catch (final RuntimeException e) {
            log.error("Failed to publish core LOAN STATUS_CHANGED webhook for loan {}: {}", loan.getId(), e.getMessage(), e);
        }
    }
}
