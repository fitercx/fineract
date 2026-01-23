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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanStatusWebhookPublisher {

    private static final String ENTITY = "LOAN";
    private static final String ACTION = "STATUS_CHANGED";

    private final CredXSynchronousCommandProcessingService credxSyncCommandService;
    private final EzySqlLoanLocLookupRepository ezyLoanLocLookupRepository;

    // Publish with full loan context and both default/custom old statuses
    public void publish(Loan loan, CustomLoanStatus oldCustomStatus) {
        if (loan == null || loan.getStatus() == null) {
            return;
        }

        Map<String, Object> customStatus = new HashMap<>();
        Map<String, Object> changes = new HashMap<>();
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> payload = new HashMap<>();

        customStatus.put("newStatus", loan.hasCustomStatus() ? loan.getCustomLoanStatus().toString() : null);
        customStatus.put("oldStatus", oldCustomStatus == null ? null : oldCustomStatus.toString());

        changes.put("customStatus", customStatus);
        changes.put("defaultStatus", loan.getStatus().toString());
        changes.put("loanId", loan.getId());
        changes.put("clientId", loan.getClientId());
        changes.put("officeId", loan.getOfficeId());
        changes.put("statusChanged", true);

        boolean isDrawdown = ezyLoanLocLookupRepository.existsByLoanId(loan.getId());

        // Optional LOC id when drawdown
        if (isDrawdown) {
            ezyLoanLocLookupRepository.findLocIdByLoanId(loan.getId()).ifPresent(locId -> changes.put("locId", locId));
        }

        response.put("changes", changes);
        response.put("isDrawdown", isDrawdown);

        payload.put("response", response);
        payload.put("entityName", ENTITY);
        payload.put("actionName", ACTION);
        payload.put("resourceId", loan.getId());
        payload.put("resourceIdentifier", String.valueOf(loan.getId()));

        credxSyncCommandService.publishHookEventRaw(ENTITY, ACTION, payload);
    }

    // New overload to publish without repository access (use precomputed flags)
    public void publish(Loan loan, CustomLoanStatus oldCustomStatus, boolean isDrawdown, Optional<Long> locIdOpt) {
        if (loan == null || loan.getStatus() == null) {
            return;
        }
        Map<String, Object> customStatus = new HashMap<>();
        Map<String, Object> changes = new HashMap<>();
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> payload = new HashMap<>();

        customStatus.put("newStatus", loan.hasCustomStatus() ? loan.getCustomLoanStatus().toString() : null);
        customStatus.put("oldStatus", oldCustomStatus == null ? null : oldCustomStatus.toString());

        changes.put("customStatus", customStatus);
        changes.put("defaultStatus", loan.getStatus().toString());
        changes.put("loanId", loan.getId());
        changes.put("clientId", loan.getClientId());
        changes.put("officeId", loan.getOfficeId());
        changes.put("statusChanged", true);

        locIdOpt.ifPresent(locId -> changes.put("locId", locId));

        response.put("changes", changes);
        response.put("isDrawdown", isDrawdown);

        payload.put("response", response);
        payload.put("entityName", ENTITY);
        payload.put("actionName", ACTION);
        payload.put("resourceId", loan.getId());
        payload.put("resourceIdentifier", String.valueOf(loan.getId()));

        credxSyncCommandService.publishHookEventRaw(ENTITY, ACTION, payload);
    }
}
