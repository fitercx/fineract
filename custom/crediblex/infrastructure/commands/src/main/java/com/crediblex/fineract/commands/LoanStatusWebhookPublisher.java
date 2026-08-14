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
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.stereotype.Component;

/**
 * Publishes the {@code LOAN / STATUS_CHANGED} webhook.
 *
 * <p>
 * Two kinds of change are emitted:
 * <ul>
 * <li><b>Core status changes</b> ({@link #publishCoreStatusChange}) — driven centrally by
 * {@link LoanStatusChangedWebhookListener} for every Fineract lifecycle transition (approve, disburse, obligations-met,
 * overpaid, written-off, rescheduled, ...). This is the path that fixes the previously-undelivered post-active
 * statuses.</li>
 * <li><b>Custom overlay changes</b> ({@link #publish}) — the legacy path, kept for delinquency-only transitions (e.g.
 * {@code INVALID -> PAST_DUE}) where the core status does not change. It now defers to the core path via
 * {@link StatusWebhookTxnDedup} so a single operation that changes both never double-sends.</li>
 * </ul>
 *
 * <p>
 * Every attempt (success or failure) is written to the audit trail by {@link LoanStatusWebhookTrailRecorder}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanStatusWebhookPublisher {

    private static final String ENTITY = "LOAN";
    private static final String ACTION = "STATUS_CHANGED";
    private static final String TRIGGER_CORE = "CORE_STATUS_CHANGE";
    private static final String TRIGGER_CUSTOM = "CUSTOM_STATUS_CHANGE";

    private final CredXSynchronousCommandProcessingService credxSyncCommandService;
    private final EzySqlLoanLocLookupRepository ezyLoanLocLookupRepository;
    private final LoanStatusWebhookTrailRecorder trailRecorder;
    private final StatusWebhookTxnDedup dedup;

    /**
     * Emit a webhook for a <b>core</b> Fineract loan-status transition (old -> new). Not gated on the custom overlay;
     * the caller ({@link LoanStatusChangedWebhookListener}) has already confirmed the core status changed.
     */
    public void publishCoreStatusChange(final Loan loan, final LoanStatus oldStatus, final boolean isDrawdown,
            final Optional<Long> locIdOpt) {
        if (loan == null || loan.getStatus() == null) {
            return;
        }
        final LoanStatus newStatus = loan.getStatus();
        final CustomLoanStatus currentCustom = loan.hasCustomStatus() ? loan.getCustomLoanStatus() : null;

        final Map<String, Object> changes = new HashMap<>();
        final Map<String, Object> customStatus = new HashMap<>();
        customStatus.put("newStatus", currentCustom == null ? null : currentCustom.toString());
        customStatus.put("oldStatus", null); // core-driven change: the overlay is not the trigger
        changes.put("customStatus", customStatus);
        // Backward compatible: existing consumers already read `defaultStatus` as the (new) status name.
        changes.put("defaultStatus", newStatus.toString());
        changes.put("defaultStatusCode", newStatus.getValue());
        // New fields carrying the previous core status so LOS gets the full old -> new transition.
        changes.put("oldDefaultStatus", oldStatus == null ? null : oldStatus.toString());
        changes.put("oldDefaultStatusCode", oldStatus == null ? null : oldStatus.getValue());

        final Map<String, Object> payload = buildPayload(loan, changes, isDrawdown, locIdOpt);
        final WebhookTrailEntry.WebhookTrailEntryBuilder trail = baseTrail(loan, isDrawdown, locIdOpt.orElse(null))
                .oldCoreStatus(oldStatus == null ? null : oldStatus.toString())
                .oldCoreStatusCode(oldStatus == null ? null : oldStatus.getValue()).newCoreStatus(newStatus.toString())
                .newCoreStatusCode(newStatus.getValue()).newCustomStatus(currentCustom == null ? null : currentCustom.toString())
                .triggerSource(TRIGGER_CORE);
        dispatchAndRecord(payload, trail);
    }

    // Publish with full loan context and both default/custom old statuses (legacy overlay path; looks up drawdown
    // itself)
    public void publish(final Loan loan, final CustomLoanStatus oldCustomStatus) {
        if (loan == null || loan.getStatus() == null) {
            return;
        }
        final CustomLoanStatus newCustom = loan.hasCustomStatus() ? loan.getCustomLoanStatus() : null;
        if (Objects.equals(oldCustomStatus, newCustom)) {
            return; // overlay unchanged
        }
        final boolean isDrawdown = ezyLoanLocLookupRepository.existsByLoanId(loan.getId());
        final Optional<Long> locIdOpt = isDrawdown ? ezyLoanLocLookupRepository.findLocIdByLoanId(loan.getId()) : Optional.empty();
        publishOverlayChange(loan, oldCustomStatus, newCustom, isDrawdown, locIdOpt);
    }

    // Overload used by the transaction-synchronised call sites (precomputed drawdown flags)
    public void publish(final Loan loan, final CustomLoanStatus oldCustomStatus, final boolean isDrawdown, final Optional<Long> locIdOpt) {
        if (loan == null || loan.getStatus() == null) {
            return;
        }
        final CustomLoanStatus newCustom = loan.hasCustomStatus() ? loan.getCustomLoanStatus() : null;
        if (Objects.equals(oldCustomStatus, newCustom)) {
            return; // overlay unchanged
        }
        publishOverlayChange(loan, oldCustomStatus, newCustom, isDrawdown, locIdOpt);
    }

    private void publishOverlayChange(final Loan loan, final CustomLoanStatus oldCustomStatus, final CustomLoanStatus newCustom,
            final boolean isDrawdown, final Optional<Long> locIdOpt) {
        // A core-status webhook already went out for this loan in this transaction — it carries the current overlay,
        // so the overlay-only webhook would be redundant. Skip it.
        if (dedup.wasCoreEmitted(loan.getId())) {
            return;
        }

        final Map<String, Object> changes = new HashMap<>();
        final Map<String, Object> customStatus = new HashMap<>();
        customStatus.put("newStatus", newCustom == null ? null : newCustom.toString());
        customStatus.put("oldStatus", oldCustomStatus == null ? null : oldCustomStatus.toString());
        changes.put("customStatus", customStatus);
        changes.put("defaultStatus", loan.getStatus().toString());
        changes.put("defaultStatusCode", loan.getStatus().getValue());

        final Map<String, Object> payload = buildPayload(loan, changes, isDrawdown, locIdOpt);
        final WebhookTrailEntry.WebhookTrailEntryBuilder trail = baseTrail(loan, isDrawdown, locIdOpt.orElse(null))
                .newCoreStatus(loan.getStatus().toString()).newCoreStatusCode(loan.getStatus().getValue())
                .oldCustomStatus(oldCustomStatus == null ? null : oldCustomStatus.toString())
                .newCustomStatus(newCustom == null ? null : newCustom.toString()).triggerSource(TRIGGER_CUSTOM);
        dispatchAndRecord(payload, trail);
    }

    private Map<String, Object> buildPayload(final Loan loan, final Map<String, Object> changes, final boolean isDrawdown,
            final Optional<Long> locIdOpt) {
        final Map<String, Object> response = new HashMap<>();
        response.put("changes", changes);
        response.put("loanId", loan.getId());
        response.put("clientId", loan.getClientId());
        response.put("officeId", loan.getOfficeId());
        response.put("resourceId", loan.getId());
        response.put("isDrawdown", isDrawdown);
        locIdOpt.ifPresent(locId -> response.put("locId", locId));

        final Map<String, Object> payload = new HashMap<>();
        payload.put("response", response);
        payload.put("entityName", ENTITY);
        payload.put("actionName", ACTION);
        payload.put("resourceIdentifier", String.valueOf(loan.getId()));
        return payload;
    }

    private WebhookTrailEntry.WebhookTrailEntryBuilder baseTrail(final Loan loan, final boolean isDrawdown, final Long locId) {
        return WebhookTrailEntry.builder().entityName(ENTITY).actionName(ACTION).resourceId(loan.getId()).loanId(loan.getId())
                .clientId(loan.getClientId()).officeId(loan.getOfficeId()).isDrawdown(isDrawdown).locId(locId);
    }

    private void dispatchAndRecord(final Map<String, Object> payload, final WebhookTrailEntry.WebhookTrailEntryBuilder trail) {
        boolean dispatched = false;
        String error = null;
        try {
            credxSyncCommandService.publishHookEventRaw(ENTITY, ACTION, payload);
            dispatched = true;
        } catch (final RuntimeException ex) {
            error = ex.getMessage();
            log.error("Failed to dispatch LOAN STATUS_CHANGED webhook", ex);
        } finally {
            try {
                trailRecorder.record(trail.payload(new Gson().toJson(payload)).dispatched(dispatched).errorMessage(error).build());
            } catch (final RuntimeException e) {
                log.error("Failed to record LOAN status webhook trail: {}", e.getMessage());
            }
        }
    }
}
