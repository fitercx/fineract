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

import java.sql.Timestamp;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists an audit trail row for every loan/LOC status-change webhook attempt into
 * {@code crediblex_loan_status_webhook_trail}.
 *
 * <p>
 * "Dispatched" here means the event was accepted by the (synchronous) hook dispatch — i.e. {@code publishHookEventRaw}
 * did not throw. The actual HTTP delivery to the subscriber is performed asynchronously and fire-and-forget by the
 * shared {@code WebHookProcessor}; capturing the final HTTP response would require enhancing that shared infrastructure
 * and is intentionally out of scope here (the {@code error_message}/{@code dispatched} columns already make failed
 * dispatch and building visible, and the row gives Ops a reconciliation anchor).
 *
 * <p>
 * The publisher runs this from {@code afterCommit}, when the originating command's JDBC connection is already closed.
 * Inserts therefore use a {@code REQUIRES_NEW} transaction so they never reuse that closed connection (the failure mode
 * that left this table empty in UAT/STG/local).
 *
 * <p>
 * Recording is strictly best-effort: a failure to write the trail must never break the originating business operation
 * nor the webhook itself, so all exceptions are swallowed and logged.
 */
@Slf4j
@Component
public class LoanStatusWebhookTrailRecorder {

    private static final String INSERT_SQL = """
            INSERT INTO crediblex_loan_status_webhook_trail
                (entity_name, action_name, resource_id, loan_id, client_id, office_id, is_drawdown, loc_id,
                 old_core_status, old_core_status_code, new_core_status, new_core_status_code,
                 old_custom_status, new_custom_status, trigger_source, payload, dispatched, error_message,
                 fired_at, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final TransactionTemplate requiresNewTx;

    public LoanStatusWebhookTrailRecorder(final JdbcTemplate jdbcTemplate, final PlatformSecurityContext context,
            final PlatformTransactionManager platformTransactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.context = context;
        this.requiresNewTx = new TransactionTemplate(platformTransactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(final WebhookTrailEntry entry) {
        try {
            requiresNewTx.executeWithoutResult(status -> insert(entry));
        } catch (final RuntimeException e) {
            // Never let an audit-trail failure affect the business operation or the webhook dispatch.
            log.error("Failed to record loan status webhook trail for {} {}: {}", entry.getEntityName(), entry.getResourceId(),
                    e.getMessage());
        }
    }

    private void insert(final WebhookTrailEntry entry) {
        final String truncatedError = entry.getErrorMessage() == null ? null
                : entry.getErrorMessage().substring(0, Math.min(entry.getErrorMessage().length(), 1000));
        jdbcTemplate.update(INSERT_SQL, entry.getEntityName(), entry.getActionName(), entry.getResourceId(), entry.getLoanId(),
                entry.getClientId(), entry.getOfficeId(), entry.isDrawdown(), entry.getLocId(), entry.getOldCoreStatus(),
                entry.getOldCoreStatusCode(), entry.getNewCoreStatus(), entry.getNewCoreStatusCode(), entry.getOldCustomStatus(),
                entry.getNewCustomStatus(), entry.getTriggerSource(), entry.getPayload(), entry.isDispatched(), truncatedError,
                new Timestamp(System.currentTimeMillis()), resolveCreatedBy());
    }

    private Long resolveCreatedBy() {
        try {
            return context.authenticatedUser().getId();
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
