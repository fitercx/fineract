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
import org.springframework.stereotype.Component;

/**
 * Publishes the {@code LINE_OF_CREDIT / STATUS_CHANGED} webhook (the drawdown's umbrella line-of-credit status).
 *
 * <p>
 * LOC status is an aggregated CrediblEx concept (not a Fineract state-machine status), so unlike {@code LOAN} it has no
 * business event to hook; it continues to be published from the aggregation path. Every attempt is now written to the
 * shared {@link LoanStatusWebhookTrailRecorder} audit trail. No de-duplication is needed because this is a distinct
 * entity from {@code LOAN}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineOfCreditStatusWebhookPublisher {

    private static final String ENTITY = "LINE_OF_CREDIT";
    private static final String ACTION = "STATUS_CHANGED";
    private static final String TRIGGER_LOC = "LOC_STATUS_CHANGE";

    private final CredXSynchronousCommandProcessingService credXSyncCommandService;
    private final EzySqlLoanLocLookupRepository ezyLoanLocLookupRepository;
    private final LoanStatusWebhookTrailRecorder trailRecorder;

    // Publish with full loan context and both default/custom old statuses
    public void publish(final Loan loan, final CustomLoanStatus oldCustomStatus) {
        if (loan == null || loan.getStatus() == null) {
            return;
        }

        final Map<String, Object> customStatus = new HashMap<>();
        final Map<String, Object> changes = new HashMap<>();
        final Map<String, Object> response = new HashMap<>();
        final Map<String, Object> payload = new HashMap<>();

        final String newCustom = loan.hasCustomStatus() ? loan.getCustomLoanStatus().toString() : null;
        final String oldCustom = oldCustomStatus == null ? null : oldCustomStatus.toString();
        customStatus.put("newStatus", newCustom);
        customStatus.put("oldStatus", oldCustom);

        // Skip if custom status did not change
        if (Objects.equals(oldCustom, newCustom)) {
            return;
        }

        changes.put("customStatus", customStatus);
        changes.put("defaultStatus", loan.getStatus().toString());
        changes.put("clientId", loan.getClientId());
        changes.put("officeId", loan.getOfficeId());
        changes.put("statusChanged", true);

        final boolean isDrawdown = ezyLoanLocLookupRepository.existsByLoanId(loan.getId());

        // Optional LOC id when drawdown
        final Optional<Long> locIdOpt = isDrawdown ? ezyLoanLocLookupRepository.findLocIdByLoanId(loan.getId()) : Optional.empty();
        locIdOpt.ifPresent(locId -> changes.put("locId", locId));

        response.put("changes", changes);

        payload.put("response", response);
        payload.put("entityName", ENTITY);
        payload.put("actionName", ACTION);
        payload.put("resourceId", loan.getId());
        payload.put("resourceIdentifier", String.valueOf(loan.getId()));

        final WebhookTrailEntry.WebhookTrailEntryBuilder trail = WebhookTrailEntry.builder().entityName(ENTITY).actionName(ACTION)
                .resourceId(locIdOpt.orElse(loan.getId())).loanId(loan.getId()).clientId(loan.getClientId()).officeId(loan.getOfficeId())
                .isDrawdown(isDrawdown).locId(locIdOpt.orElse(null)).newCoreStatus(loan.getStatus().toString())
                .newCoreStatusCode(loan.getStatus().getValue()).oldCustomStatus(oldCustom).newCustomStatus(newCustom)
                .triggerSource(TRIGGER_LOC);
        dispatchAndRecord(payload, trail);
    }

    // New overload to publish without repository access (use precomputed flags)
    public void publish(final Loan loan, final String defaultLocStatus, final String oldCustomStatus, final String newCustomStatus,
            final boolean isDrawdown, final Optional<Long> locIdOpt) {
        if (loan == null || loan.getStatus() == null) {
            return;
        }
        // Skip if custom status did not change
        if (Objects.equals(oldCustomStatus, newCustomStatus)) {
            return;
        }
        final Map<String, Object> customStatus = new HashMap<>();
        final Map<String, Object> changes = new HashMap<>();
        final Map<String, Object> response = new HashMap<>();
        final Map<String, Object> payload = new HashMap<>();

        customStatus.put("newStatus", newCustomStatus);
        customStatus.put("oldStatus", oldCustomStatus);

        changes.put("customStatus", customStatus);
        changes.put("defaultStatus", defaultLocStatus);

        response.put("changes", changes);
        response.put("clientId", loan.getClientId());
        response.put("officeId", loan.getOfficeId());
        response.put("isDrawdown", isDrawdown);
        response.put("resourceId", locIdOpt.orElse(null));
        locIdOpt.ifPresent(locId -> response.put("locId", locId));

        payload.put("response", response);
        payload.put("entityName", ENTITY);
        payload.put("actionName", ACTION);
        payload.put("resourceIdentifier", locIdOpt.map(String::valueOf).orElse(null));

        final WebhookTrailEntry.WebhookTrailEntryBuilder trail = WebhookTrailEntry.builder().entityName(ENTITY).actionName(ACTION)
                .resourceId(locIdOpt.orElse(null)).loanId(loan.getId()).clientId(loan.getClientId()).officeId(loan.getOfficeId())
                .isDrawdown(isDrawdown).locId(locIdOpt.orElse(null)).newCoreStatus(defaultLocStatus).oldCustomStatus(oldCustomStatus)
                .newCustomStatus(newCustomStatus).triggerSource(TRIGGER_LOC);
        dispatchAndRecord(payload, trail);
    }

    private void dispatchAndRecord(final Map<String, Object> payload, final WebhookTrailEntry.WebhookTrailEntryBuilder trail) {
        boolean dispatched = false;
        String error = null;
        try {
            credXSyncCommandService.publishHookEventRaw(ENTITY, ACTION, payload);
            dispatched = true;
        } catch (final RuntimeException ex) {
            error = ex.getMessage();
            log.error("Failed to dispatch LINE_OF_CREDIT STATUS_CHANGED webhook: {}", ex.getMessage(), ex);
        } finally {
            try {
                trailRecorder.record(trail.payload(new Gson().toJson(payload)).dispatched(dispatched).errorMessage(error).build());
            } catch (final RuntimeException e) {
                log.error("Failed to record LINE_OF_CREDIT status webhook trail: {}", e.getMessage());
            }
        }
    }
}
