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

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable value object describing a single loan/LOC status-change webhook attempt, persisted by
 * {@link LoanStatusWebhookTrailRecorder} into {@code crediblex_loan_status_webhook_trail}.
 */
@Getter
@Builder
public class WebhookTrailEntry {

    /** LOAN or LINE_OF_CREDIT. */
    private final String entityName;
    /** Always STATUS_CHANGED today. */
    private final String actionName;
    /** The resource the webhook is about (loan id for LOAN, loc id for LINE_OF_CREDIT). */
    private final Long resourceId;
    private final Long loanId;
    private final Long clientId;
    private final Long officeId;
    private final boolean isDrawdown;
    private final Long locId;

    /** Previous / new core Fineract loan status (name + numeric code); null for custom-overlay-only changes. */
    private final String oldCoreStatus;
    private final Integer oldCoreStatusCode;
    private final String newCoreStatus;
    private final Integer newCoreStatusCode;

    /** Previous / new custom overlay status (name); null for core-only changes. */
    private final String oldCustomStatus;
    private final String newCustomStatus;

    /** What drove the webhook: CORE_STATUS_CHANGE, CUSTOM_STATUS_CHANGE or LOC_STATUS_CHANGE. */
    private final String triggerSource;

    /** The exact JSON payload handed to the hook infrastructure. */
    private final String payload;

    /** True if {@code publishHookEventRaw} accepted the event for dispatch (did not throw). */
    private final boolean dispatched;

    /** Error message when dispatch failed; null otherwise. */
    private final String errorMessage;
}
