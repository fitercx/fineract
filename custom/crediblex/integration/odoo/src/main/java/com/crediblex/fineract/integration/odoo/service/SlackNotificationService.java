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
package com.crediblex.fineract.integration.odoo.service;

import com.crediblex.fineract.integration.odoo.client.SlackClient;
import com.crediblex.fineract.integration.odoo.config.SlackProperties;
import com.crediblex.fineract.integration.odoo.domain.FailedEntryDetail;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service for sending Slack notifications related to Odoo sync operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackNotificationService {

    private final SlackClient slackClient;
    private final SlackProperties slackProperties;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Send a notification to Slack about Odoo sync failures.
     *
     * @param failureCount
     *            Total number of failed entries
     * @param successCount
     *            Total number of successful entries
     * @param failedEntries
     *            List of failed entry details
     */
    public void sendOdooSyncFailureNotification(int failureCount, int successCount, List<FailedEntryDetail> failedEntries) {
        if (failureCount == 0) {
            log.debug("No failures to report, skipping Slack notification");
            return;
        }

        try {
            Map<String, Object> payload = buildSlackPayload(failureCount, successCount, failedEntries);
            boolean sent = slackClient.sendMessage(payload);

            if (sent) {
                log.info("Odoo sync failure notification sent to Slack: {} failures, {} successes", failureCount, successCount);
            } else {
                log.warn("Failed to send Odoo sync failure notification to Slack");
            }
        } catch (Exception e) {
            // Log but don't throw - Slack notification should not break the job
            log.error("Error sending Slack notification for Odoo sync failures", e);
        }
    }

    /**
     * Build the Slack message payload using Block Kit for rich formatting.
     */
    private Map<String, Object> buildSlackPayload(int failureCount, int successCount, List<FailedEntryDetail> failedEntries) {
        Map<String, Object> payload = new HashMap<>();

        // Optional: Override channel
        if (slackProperties.getChannel() != null && !slackProperties.getChannel().isEmpty()) {
            payload.put("channel", slackProperties.getChannel());
        }

        payload.put("username", slackProperties.getUsername());
        payload.put("icon_emoji", slackProperties.getIconEmoji());

        // Build blocks for rich formatting
        List<Map<String, Object>> blocks = new ArrayList<>();

        // Header block
        blocks.add(createHeaderBlock(":x: Odoo Journal Entry Sync Failed"));

        // Summary section
        String summaryText = "*Summary:*\n:red_circle: *" + failureCount + " entries failed* to post to Odoo\n:white_check_mark: "
                + successCount + " entries posted successfully\n:clock1: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        blocks.add(createSectionBlock(summaryText));

        // Divider
        blocks.add(createDividerBlock());

        // Failed entries details (limited to maxFailedEntriesToShow)
        if (failedEntries != null && !failedEntries.isEmpty()) {
            blocks.add(createSectionBlock("*Failed Entry Details:*"));

            int entriesToShow = Math.min(failedEntries.size(), slackProperties.getMaxFailedEntriesToShow());

            for (int i = 0; i < entriesToShow; i++) {
                FailedEntryDetail entry = failedEntries.get(i);
                String entryText = formatFailedEntryText(entry, i + 1);
                blocks.add(createSectionBlock(entryText));
            }

            // Show "and X more..." if there are more failures
            if (failedEntries.size() > entriesToShow) {
                int remaining = failedEntries.size() - entriesToShow;
                blocks.add(createContextBlock(String.format("_...and %d more failed entries_", remaining)));
            }
        }

        // Divider
        blocks.add(createDividerBlock());

        // Action guidance
        blocks.add(createContextBlock(
                ":information_source: Please check the application logs and `journal_entry_odoo_sync` table for more details."));

        payload.put("blocks", blocks);

        // Fallback text for notifications
        payload.put("text", String.format("Odoo Sync Alert: %d journal entries failed to post to Odoo", failureCount));

        return payload;
    }

    /**
     * Format a single failed entry for display.
     */
    private String formatFailedEntryText(FailedEntryDetail entry, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("*%d.* ", index));

        if (entry.getLoanId() != null) {
            sb.append(String.format("Loan ID: `%d` | ", entry.getLoanId()));
        }

        if (entry.getBusinessEventType() != null) {
            sb.append(String.format("Type: `%s` | ", entry.getBusinessEventType()));
        }

        if (entry.getGlAccountCode() != null) {
            sb.append(String.format("GL: `%s`", entry.getGlAccountCode()));
            if (entry.getGlAccountName() != null) {
                sb.append(String.format(" (%s)", entry.getGlAccountName()));
            }
            sb.append(" | ");
        }

        if (entry.getAmount() != null) {
            sb.append(String.format("Amount: `%s`", entry.getAmount().toPlainString()));
        }

        sb.append("\n:exclamation: _Error: ").append(entry.getErrorMessage() != null ? entry.getErrorMessage() : "Unknown error")
                .append("_");

        return sb.toString();
    }

    private Map<String, Object> createHeaderBlock(String text) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "header");

        Map<String, Object> textObj = new HashMap<>();
        textObj.put("type", "plain_text");
        textObj.put("text", text);
        textObj.put("emoji", true);

        block.put("text", textObj);
        return block;
    }

    private Map<String, Object> createSectionBlock(String markdownText) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "section");

        Map<String, Object> textObj = new HashMap<>();
        textObj.put("type", "mrkdwn");
        textObj.put("text", markdownText);

        block.put("text", textObj);
        return block;
    }

    private Map<String, Object> createDividerBlock() {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "divider");
        return block;
    }

    private Map<String, Object> createContextBlock(String markdownText) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "context");

        List<Map<String, Object>> elements = new ArrayList<>();
        Map<String, Object> textObj = new HashMap<>();
        textObj.put("type", "mrkdwn");
        textObj.put("text", markdownText);
        elements.add(textObj);

        block.put("elements", elements);
        return block;
    }
}
