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
package com.crediblex.fineract.integration.odoo.settlements;

import com.crediblex.fineract.infrastructure.s3.service.SettlementsReportS3StorageService;
import com.crediblex.fineract.integration.odoo.client.SlackClient;
import com.crediblex.fineract.integration.odoo.config.SlackProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 * Posts Settlements Report notifications to Slack (default channel {@code #daily-banktransfers-update}).
 * <p>
 * When there is activity: Excel is uploaded to S3 and a download link is shared in Slack. When there is no activity:
 * plain text only (no S3 upload).
 * <p>
 * Webhook URL is read from {@code slack.settlements-webhook-url} / {@code SLACK_SETTLEMENTS_WEBHOOK_URL}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementsSlackNotifier {

    private static final String USERNAME = "Settlements Report Bot";
    private static final String ICON_EMOJI = ":page_facing_up:";
    private static final DateTimeFormatter SUBJECT_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    static final String NO_ACTIVITY_MESSAGE = "No settlement activity recorded since the last report run.";

    private final SlackProperties slackProperties;
    private final ObjectProvider<SlackClient> slackClientProvider;
    private final ObjectProvider<SettlementsReportS3StorageService> settlementsS3StorageProvider;

    /**
     * @param rows
     *            settlement rows for this run
     * @param excelBytes
     *            Excel bytes (may be null/empty when there is no activity)
     * @param reportTimeUae
     *            report timestamp in UAE timezone
     * @return true if Slack notification was sent successfully
     */
    public boolean sendReport(final List<SettlementsReportRow> rows, final byte[] excelBytes, final ZonedDateTime reportTimeUae) {
        final String subject = "Settlements Report — " + reportTimeUae.format(SUBJECT_FORMATTER);
        final boolean empty = rows == null || rows.isEmpty();

        if (empty) {
            return postToSlack(subject + "\n" + NO_ACTIVITY_MESSAGE);
        }

        long disbursements = rows.stream().filter(r -> "Disbursement".equals(r.getEvent())).count();
        long refunds = rows.stream().filter(r -> "Refund".equals(r.getEvent())).count();
        final String summary = String.format("Settlements since last run: *%d* total (%d disbursements, %d refunds).", rows.size(),
                disbursements, refunds);

        final SettlementsReportS3StorageService s3Storage = settlementsS3StorageProvider.getIfAvailable();
        if (s3Storage == null) {
            log.error("Settlements S3 storage unavailable (crediblex.s3.bucket-name not set); sending Slack summary without download link");
            return postToSlack(subject + "\n" + summary + "\n_Excel could not be uploaded to S3; check AWS_S3_BUCKET_NAME._");
        }

        final Optional<String> downloadUrl = s3Storage.uploadAndGetDownloadUrl(excelBytes, reportTimeUae);
        if (downloadUrl.isPresent()) {
            final String text = subject + "\n" + summary + "\n<" + downloadUrl.get() + "|Download Excel report>";
            return postToSlack(text);
        }

        log.error("S3 upload failed for settlements report; sending Slack summary without download link");
        return postToSlack(subject + "\n" + summary + "\n_Excel could not be uploaded to S3; check AWS_S3_BUCKET_NAME / credentials._");
    }

    private boolean postToSlack(final String text) {
        final String webhookUrl = slackProperties.getSettlementsWebhookUrl();
        if (!StringUtils.hasText(webhookUrl)) {
            log.error("Settlements Slack webhook not configured (set SLACK_SETTLEMENTS_WEBHOOK_URL / slack.settlements-webhook-url)");
            return false;
        }

        final String channel = StringUtils.hasText(slackProperties.getSettlementsChannel()) ? slackProperties.getSettlementsChannel()
                : "#daily-banktransfers-update";

        final Map<String, Object> payload = new HashMap<>();
        payload.put("channel", channel);
        payload.put("username", USERNAME);
        payload.put("icon_emoji", ICON_EMOJI);
        payload.put("text", text);

        final SlackClient slackClient = slackClientProvider.getIfAvailable();
        if (slackClient != null) {
            return slackClient.sendMessage(webhookUrl, payload);
        }
        return postWebhookDirect(webhookUrl, payload);
    }

    private boolean postWebhookDirect(final String webhookUrl, final Map<String, Object> payload) {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            final RequestConfig config = RequestConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(5000))
                    .setResponseTimeout(Timeout.ofMilliseconds(10000)).build();
            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(config).build()) {
                final HttpPost httpPost = new HttpPost(webhookUrl);
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(payload), ContentType.APPLICATION_JSON));
                return httpClient.execute(httpPost, response -> {
                    final int statusCode = response.getCode();
                    final String responseBody = EntityUtils.toString(response.getEntity());
                    if (statusCode == 200 && "ok".equals(responseBody)) {
                        log.info("Settlements Slack webhook message sent successfully");
                        return true;
                    }
                    log.error("Settlements Slack webhook failed. Status: {}, Response: {}", statusCode, responseBody);
                    return false;
                });
            }
        } catch (Exception e) {
            log.error("Exception while posting Settlements Slack webhook", e);
            return false;
        }
    }
}
