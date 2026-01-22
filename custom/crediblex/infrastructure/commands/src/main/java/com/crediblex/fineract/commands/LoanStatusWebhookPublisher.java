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

import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.stereotype.Component;
import com.crediblex.fineract.commands.repository.LoanLocLookupRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LoanStatusWebhookPublisher {

    private static final String ENTITY = "LOAN";
    private static final String ACTION = "STATUS_CHANGED";

    private final CredXSynchronousCommandProcessingService credxSyncCommandService;
    private final LoanLocLookupRepository loanLocLookupRepository;

    // Publish with full loan context and both default/custom old statuses
    public void publish(Loan loan, LoanStatus oldDefaultStatus, CustomLoanStatus oldCustomStatus) {
        if (loan == null || loan.getStatus() == null) {
            return;
        }
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> changes = new HashMap<>();

        // Root-level enrichment: isDrawdown
        boolean isDrawdown = loanLocLookupRepository.existsByLoanId(loan.getId());
        response.put("isDrawdown", isDrawdown);

        // Basic identifiers
        changes.put("clientId", loan.getClientId());
        changes.put("officeId", loan.getOfficeId());
        changes.put("loanId", loan.getId());
        changes.put("statusChanged", true);

        // Status info
        changes.put("defaultStatus", loan.getStatus().toString());
        changes.put("oldDefaultStatus", oldDefaultStatus == null ? null : oldDefaultStatus.toString());
        changes.put("customStatus", loan.hasCustomStatus() ? loan.getCustomStatus().toString() : null);
        changes.put("oldCustomStatus", oldCustomStatus == null ? null : oldCustomStatus.toString());

        // Optional LOC id when drawdown
        if (isDrawdown) {
            loanLocLookupRepository.findLocIdByLoanId(loan.getId()).ifPresent(locId -> changes.put("locId", locId));
        }

        response.put("changes", changes);

        Map<String, Object> payload = new HashMap<>();
        payload.put("entityName", ENTITY);
        payload.put("actionName", ACTION);
        payload.put("resourceId", loan.getId());
        payload.put("resourceIdentifier", String.valueOf(loan.getId()));
        payload.put("response", response);

        credxSyncCommandService.publishHookEventRaw(ENTITY, ACTION, payload);
    }

    // Backward-compatible overloads
    public void publish(Loan loan, String oldDefaultStatus, String oldCustomStatus) {
        LoanStatus oldDefault = oldDefaultStatus == null ? null : LoanStatus.valueOf(oldDefaultStatus);
        CustomLoanStatus oldCustom = oldCustomStatus == null ? null : CustomLoanStatus.valueOf(oldCustomStatus);
        publish(loan, oldDefault, oldCustom);
    }

    // New overload to publish without repository access (use precomputed flags)
    public void publish(Loan loan, LoanStatus oldDefaultStatus, CustomLoanStatus oldCustomStatus, boolean isDrawdown, Optional<Long> locIdOpt) {
        if (loan == null || loan.getStatus() == null) {
            return;
        }
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> changes = new HashMap<>();

        response.put("isDrawdown", isDrawdown);

        changes.put("clientId", loan.getClientId());
        changes.put("officeId", loan.getOfficeId());
        changes.put("loanId", loan.getId());
        changes.put("statusChanged", true);
        changes.put("defaultStatus", loan.getStatus().toString());
        changes.put("oldDefaultStatus", oldDefaultStatus == null ? null : oldDefaultStatus.toString());
        changes.put("customStatus", loan.hasCustomStatus() ? loan.getCustomStatus().toString() : null);
        changes.put("oldCustomStatus", oldCustomStatus == null ? null : oldCustomStatus.toString());

        locIdOpt.ifPresent(locId -> changes.put("locId", locId));

        response.put("changes", changes);

        Map<String, Object> payload = new HashMap<>();
        payload.put("entityName", ENTITY);
        payload.put("actionName", ACTION);
        payload.put("resourceId", loan.getId());
        payload.put("resourceIdentifier", String.valueOf(loan.getId()));
        payload.put("response", response);

        credxSyncCommandService.publishHookEventRaw(ENTITY, ACTION, payload);
    }
}
