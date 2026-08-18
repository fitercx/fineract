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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class LoanStatusWebhookTrailRecorderTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private PlatformTransactionManager platformTransactionManager;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private AppUser appUser;

    private LoanStatusWebhookTrailRecorder recorder;

    @BeforeEach
    void setUp() {
        when(platformTransactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        doAnswer(invocation -> null).when(platformTransactionManager).commit(transactionStatus);
        recorder = new LoanStatusWebhookTrailRecorder(jdbcTemplate, context, platformTransactionManager);
    }

    @Test
    void record_insertsInsideRequiresNewTransaction() {
        when(appUser.getId()).thenReturn(2L);
        when(context.authenticatedUser()).thenReturn(appUser);

        final WebhookTrailEntry entry = WebhookTrailEntry.builder().entityName("LOAN").actionName("STATUS_CHANGED").resourceId(13886L)
                .loanId(13886L).clientId(10285L).officeId(1L).isDrawdown(false).oldCoreStatus("SUBMITTED_AND_PENDING_APPROVAL")
                .oldCoreStatusCode(100).newCoreStatus("APPROVED").newCoreStatusCode(200).triggerSource("CORE_STATUS_CHANGE")
                .payload("{}").dispatched(true).build();

        recorder.record(entry);

        verify(platformTransactionManager).getTransaction(any(TransactionDefinition.class));
        verify(jdbcTemplate).update(any(String.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(platformTransactionManager).commit(transactionStatus);
    }
}
