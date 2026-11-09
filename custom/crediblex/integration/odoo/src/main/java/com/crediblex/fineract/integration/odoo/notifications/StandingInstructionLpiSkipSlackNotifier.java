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

import com.crediblex.fineract.integration.odoo.client.SlackClient;
import com.crediblex.fineract.integration.odoo.config.SlackProperties;
import com.crediblex.fineract.portfolio.account.data.SkippedStandingInstruction;
import com.crediblex.fineract.portfolio.account.service.StandingInstructionLpiSkipNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Posts the daily Ops digest of standing instructions skipped because the loan had outstanding LPI.
 * <p>
 * Webhook URL is read from {@code slack.ops-webhook-url} / {@code SLACK_OPS_WEBHOOK_URL}. Works without
 * {@code slack.enabled}, like the Settlements Report.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandingInstructionLpiSkipSlackNotifier implements StandingInstructionLpiSkipNotifier {

    private static final String USERNAME = "Standing Instruction Bot";
    private static final String ICON_EMOJI = ":no_entry:";
    // Slack allows 50 blocks per message; header, summary, divider and footer take 4.
    static final int LOANS_PER_MESSAGE = 40;

    private final SlackProperties slackProperties;
    private final ObjectProvider<SlackClient> slackClientProvider;

    @Override
    public void notifySkipped(List<SkippedStandingInstruction> skipped) {
        if (skipped == null || skipped.isEmpty()) {
            return;
        }
        final String webhookUrl = slackProperties.getOpsWebhookUrl();
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("Ops Slack webhook not configured (set SLACK_OPS_WEBHOOK_URL / slack.ops-webhook-url); {} LPI skip(s) not alerted",
                    skipped.size());
            return;
        }

        for (Map<String, Object> payload : buildPayloads(skipped)) {
            if (!post(webhookUrl, payload)) {
                log.warn("Failed to post LPI skip digest to Slack");
            }
        }
    }

    List<Map<String, Object>> buildPayloads(List<SkippedStandingInstruction> skipped) {
        final LocalDate runDate = skipped.get(0).runDate();
        final int parts = (skipped.size() + LOANS_PER_MESSAGE - 1) / LOANS_PER_MESSAGE;
        final List<Map<String, Object>> payloads = new ArrayList<>();

        for (int part = 0; part < parts; part++) {
            final int from = part * LOANS_PER_MESSAGE;
            final List<SkippedStandingInstruction> chunk = skipped.subList(from, Math.min(from + LOANS_PER_MESSAGE, skipped.size()));

            final List<Map<String, Object>> blocks = new ArrayList<>();
            String header = ":no_entry: Standing instructions not executed: outstanding LPI";
            if (parts > 1) {
                header += String.format(" (%d/%d)", part + 1, parts);
            }
            blocks.add(headerBlock(header));
            blocks.add(sectionBlock(String.join("\n", "*Run date:* " + runDate,
                    String.format("*Skipped:* %d loan(s) with available balance and outstanding LPI", skipped.size()))));
            blocks.add(dividerBlock());
            for (int i = 0; i < chunk.size(); i++) {
                blocks.add(sectionBlock(formatEntry(chunk.get(i), from + i + 1)));
            }
            blocks.add(contextBlock(":information_source: Recorded as `skipped` in standing instruction history. Clear or waive the LPI, "
                    + "then re-run *Execute Standing Instruction* to collect today."));

            final Map<String, Object> payload = new HashMap<>();
            if (StringUtils.hasText(slackProperties.getOpsChannel())) {
                payload.put("channel", slackProperties.getOpsChannel());
            }
            payload.put("username", USERNAME);
            payload.put("icon_emoji", ICON_EMOJI);
            payload.put("blocks", blocks);
            payload.put("text", String.format("%d standing instruction(s) not executed on %s: outstanding LPI", skipped.size(), runDate));
            payloads.add(payload);
        }
        return payloads;
    }

    private String formatEntry(SkippedStandingInstruction entry, int index) {
        // Slack mrkdwn needs a literal \n; %n would be platform-dependent.
        return String.join("\n",
                String.format("*%d. Loan `%s`* | %s", index, entry.loanAccountNo(), entry.clientName() != null ? entry.clientName() : "-"),
                String.format("Savings: `%s` | Available balance: `%s`", entry.savingsAccountNo(), amount(entry.availableBalance())),
                String.format("SI amount: `%s` | Outstanding LPI: `%s`", amount(entry.scheduledAmount()), amount(entry.outstandingLpi())),
                String.format("Scheduled: %s | _%s_", entry.runDate(), SkippedStandingInstruction.REASON));
    }

    private static String amount(BigDecimal value) {
        return value == null ? "-" : String.format("%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }

    private boolean post(String webhookUrl, Map<String, Object> payload) {
        final SlackClient slackClient = slackClientProvider.getIfAvailable();
        if (slackClient != null) {
            return slackClient.sendMessage(webhookUrl, payload);
        }
        try {
            final RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofMilliseconds(slackProperties.getConnectTimeout()))
                    .setResponseTimeout(Timeout.ofMilliseconds(slackProperties.getReadTimeout())).build();
            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(config).build()) {
                final HttpPost httpPost = new HttpPost(webhookUrl);
                httpPost.setEntity(new StringEntity(new ObjectMapper().writeValueAsString(payload), ContentType.APPLICATION_JSON));
                return httpClient.execute(httpPost, response -> {
                    final String body = EntityUtils.toString(response.getEntity());
                    if (response.getCode() == 200 && "ok".equals(body)) {
                        return true;
                    }
                    log.error("Ops Slack webhook failed. Status: {}, Response: {}", response.getCode(), body);
                    return false;
                });
            }
        } catch (Exception e) {
            log.error("Exception while posting Ops Slack webhook", e);
            return false;
        }
    }

    private static Map<String, Object> headerBlock(String text) {
        return Map.of("type", "header", "text", Map.of("type", "plain_text", "text", text, "emoji", true));
    }

    private static Map<String, Object> sectionBlock(String markdown) {
        return Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", markdown));
    }

    private static Map<String, Object> dividerBlock() {
        return Map.of("type", "divider");
    }

    private static Map<String, Object> contextBlock(String markdown) {
        return Map.of("type", "context", "elements", List.of(Map.of("type", "mrkdwn", "text", markdown)));
    }
}
