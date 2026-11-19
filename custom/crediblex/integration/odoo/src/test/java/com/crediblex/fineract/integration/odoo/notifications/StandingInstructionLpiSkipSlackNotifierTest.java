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
package com.crediblex.fineract.integration.odoo.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.integration.odoo.client.SlackClient;
import com.crediblex.fineract.integration.odoo.config.SlackProperties;
import com.crediblex.fineract.portfolio.account.data.SkippedStandingInstruction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class StandingInstructionLpiSkipSlackNotifierTest {

    private static final String WEBHOOK = "https://hooks.slack.com/services/ops";

    private SlackProperties slackProperties;
    private SlackClient slackClient;
    private StandingInstructionLpiSkipSlackNotifier notifier;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        slackProperties = new SlackProperties();
        slackProperties.setOpsWebhookUrl(WEBHOOK);
        slackProperties.setOpsChannel("#ops-alerts");
        slackClient = mock(SlackClient.class);
        when(slackClient.sendMessage(anyString(), anyMap())).thenReturn(true);
        ObjectProvider<SlackClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(slackClient);
        notifier = new StandingInstructionLpiSkipSlackNotifier(slackProperties, provider);
    }

    @Test
    void buildsOneDigestWithEveryRequiredField() {
        List<Map<String, Object>> payloads = notifier.buildPayloads(List.of(skip(1)));

        assertThat(payloads).hasSize(1);
        Map<String, Object> payload = payloads.get(0);
        assertThat(payload).containsEntry("channel", "#ops-alerts");
        assertThat(payload.get("text").toString()).contains("1 standing instruction(s) not executed on 2026-11-05");

        String body = payload.get("blocks").toString();
        assertThat(body).contains("Loan `L00001`", "Acme Trading LLC", "Savings: `S00001`", "Available balance: `1,500.00`",
                "SI amount: `200.00`", "Outstanding LPI: `12.35`", "Scheduled: 2026-11-05", "Not executed: outstanding LPI");
    }

    @Test
    void splitsLargeDigestsToStayWithinSlackBlockLimit() {
        int count = StandingInstructionLpiSkipSlackNotifier.LOANS_PER_MESSAGE + 5;
        List<SkippedStandingInstruction> skipped = IntStream.rangeClosed(1, count).mapToObj(this::skip).toList();

        List<Map<String, Object>> payloads = notifier.buildPayloads(skipped);

        assertThat(payloads).hasSize(2);
        payloads.forEach(p -> assertThat((List<?>) p.get("blocks")).hasSizeLessThanOrEqualTo(50));
        assertThat(payloads.get(1).get("blocks").toString()).contains("(2/2)", "*" + count + ". Loan");
    }

    @Test
    void sendsEachPartToTheOpsWebhook() {
        int count = StandingInstructionLpiSkipSlackNotifier.LOANS_PER_MESSAGE + 1;

        notifier.notifySkipped(IntStream.rangeClosed(1, count).mapToObj(this::skip).toList());

        verify(slackClient, times(2)).sendMessage(eq(WEBHOOK), anyMap());
    }

    @Test
    void sendsNothingForEmptyList() {
        notifier.notifySkipped(List.of());

        verify(slackClient, never()).sendMessage(anyString(), any());
    }

    @Test
    void sendsNothingWhenWebhookNotConfigured() {
        slackProperties.setOpsWebhookUrl("");

        notifier.notifySkipped(List.of(skip(1)));

        verify(slackClient, never()).sendMessage(anyString(), any());
    }

    private SkippedStandingInstruction skip(int i) {
        return new SkippedStandingInstruction((long) i, (long) i, String.format("L%05d", i), "Acme Trading LLC", String.format("S%05d", i),
                new BigDecimal("1500"), new BigDecimal("200"), new BigDecimal("12.345"), LocalDate.of(2026, 11, 5));
    }
}
