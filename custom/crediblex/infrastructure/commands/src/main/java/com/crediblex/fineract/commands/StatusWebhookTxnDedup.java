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

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Per-transaction de-duplication guard for LOAN {@code STATUS_CHANGED} webhooks.
 *
 * <p>
 * The central {@link LoanStatusChangedWebhookListener} fires a webhook whenever the <em>core</em> loan status changes.
 * The legacy per-method publishers ({@link LoanStatusWebhookPublisher#publish}) fire whenever the <em>custom
 * overlay</em> status changes. When a single operation changes both (e.g. disbursal moves the core status
 * {@code APPROVED -> ACTIVE} and the overlay {@code null -> INVALID}), both would otherwise emit a webhook for the same
 * loan in the same transaction. This guard lets the core-status webhook win and suppresses the redundant overlay
 * webhook.
 *
 * <p>
 * The listener runs synchronously while the command transaction is still open (the state machine raises the business
 * event mid-transaction), so it {@link #markCoreEmitted marks} the loan here <em>before</em> the overlay publishers run
 * inside their {@code afterCommit} callbacks. Overlay publishers therefore observe the mark and skip. State is held in
 * a {@link ThreadLocal} that is cleared on transaction completion to avoid leaking across pooled request threads.
 */
@Component
public class StatusWebhookTxnDedup {

    private static final ThreadLocal<Set<Long>> CORE_EMITTED = new ThreadLocal<>();

    /**
     * Record that a core-status webhook has been emitted for the given loan in the current transaction. Registers a
     * completion hook (when a transaction is active) to clear the thread-local afterwards.
     */
    public void markCoreEmitted(final Long loanId) {
        if (loanId == null) {
            return;
        }
        Set<Long> set = CORE_EMITTED.get();
        if (set == null) {
            set = new HashSet<>();
            CORE_EMITTED.set(set);
            registerCleanup();
        }
        set.add(loanId);
    }

    /**
     * @return true if a core-status webhook was already emitted for this loan in the current transaction.
     */
    public boolean wasCoreEmitted(final Long loanId) {
        final Set<Long> set = CORE_EMITTED.get();
        return loanId != null && set != null && set.contains(loanId);
    }

    /**
     * Best-effort manual clear (used by tests and by the non-transactional fallback path).
     */
    public void clear() {
        CORE_EMITTED.remove();
    }

    private void registerCleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCompletion(final int status) {
                    CORE_EMITTED.remove();
                }
            });
        }
    }
}
