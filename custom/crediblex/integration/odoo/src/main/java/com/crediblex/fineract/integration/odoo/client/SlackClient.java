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
package com.crediblex.fineract.integration.odoo.client;

import com.crediblex.fineract.integration.odoo.config.SlackProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * HTTP client for sending messages to Slack via Incoming Webhooks.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackClient {

    private static final String SLACK_API_BASE = "https://slack.com/api/";
    private static final String BOT_TOKEN_ENV = "SLACK_BOT_TOKEN";

    private final SlackProperties slackProperties;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public SlackClient(SlackProperties slackProperties, ObjectMapper objectMapper) {
        this.slackProperties = slackProperties;
        this.objectMapper = objectMapper;

        // Configure HTTP client with timeouts
        RequestConfig config = RequestConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(slackProperties.getConnectTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(slackProperties.getReadTimeout())).build();

        this.httpClient = HttpClients.custom().setDefaultRequestConfig(config).build();

        log.info("SlackClient initialized with webhook URL configured: {}",
                slackProperties.getWebhookUrl() != null && !slackProperties.getWebhookUrl().isEmpty());
    }

    @PreDestroy
    public void destroy() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.warn("Error closing SlackClient HTTP client", e);
            }
        }
    }

    /**
     * Send a message to Slack via the configured incoming webhook ({@code slack.webhook-url}).
     *
     * @param payload
     *            The Slack message payload (can include blocks, attachments, etc.)
     * @return true if message was sent successfully, false otherwise
     */
    public boolean sendMessage(Map<String, Object> payload) {
        return sendMessage(slackProperties.getWebhookUrl(), payload);
    }

    /**
     * Send a message to Slack via a specific incoming webhook URL.
     *
     * @param webhookUrl
     *            Incoming webhook URL
     * @param payload
     *            The Slack message payload
     * @return true if message was sent successfully, false otherwise
     */
    public boolean sendMessage(String webhookUrl, Map<String, Object> payload) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("Slack webhook URL is not configured");
            return false;
        }

        try {
            HttpPost httpPost = new HttpPost(webhookUrl);
            httpPost.setHeader("Content-Type", "application/json");

            String jsonPayload = objectMapper.writeValueAsString(payload);
            httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            log.debug("Sending Slack message to webhook");

            return httpClient.execute(httpPost, response -> {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode == 200 && "ok".equals(responseBody)) {
                    log.info("Slack message sent successfully");
                    return true;
                } else {
                    log.error("Failed to send Slack message. Status: {}, Response: {}", statusCode, responseBody);
                    return false;
                }
            });

        } catch (Exception e) {
            log.error("Exception while sending Slack message", e);
            return false;
        }
    }

    /**
     * Upload a file to a Slack channel using files.getUploadURLExternal + files.completeUploadExternal. Bot token is
     * read from env {@code SLACK_BOT_TOKEN} (not application.properties).
     *
     * @param channelName
     *            channel name without leading '#'
     * @param filename
     *            file name shown in Slack
     * @param fileBytes
     *            file content
     * @param initialComment
     *            message posted with the file
     * @return true if upload and share succeeded
     */
    public boolean uploadFileToChannel(final String channelName, final String filename, final byte[] fileBytes,
            final String initialComment) {
        final String botToken = resolveBotToken();
        if (botToken == null || botToken.isBlank()) {
            log.error("Slack bot token is not configured; cannot upload Excel to Slack");
            return false;
        }
        if (fileBytes == null || fileBytes.length == 0) {
            log.error("Cannot upload empty file to Slack");
            return false;
        }

        try {
            final String channelId = resolveChannelId(botToken, channelName);
            if (channelId == null) {
                log.error("Could not resolve Slack channel id for #{}", channelName);
                return false;
            }

            final JsonNode uploadUrlResponse = getUploadUrl(botToken, filename, fileBytes.length);
            if (uploadUrlResponse == null || !uploadUrlResponse.path("ok").asBoolean(false)) {
                log.error("files.getUploadURLExternal failed: {}", uploadUrlResponse);
                return false;
            }

            final String uploadUrl = uploadUrlResponse.path("upload_url").asText(null);
            final String fileId = uploadUrlResponse.path("file_id").asText(null);
            if (uploadUrl == null || fileId == null) {
                log.error("Upload URL response missing upload_url/file_id: {}", uploadUrlResponse);
                return false;
            }

            if (!postFileBytes(uploadUrl, fileBytes)) {
                return false;
            }

            return completeUpload(botToken, fileId, filename, channelId, initialComment);
        } catch (Exception e) {
            log.error("Exception while uploading file to Slack", e);
            return false;
        }
    }

    private String resolveBotToken() {
        final String fromEnv = System.getenv(BOT_TOKEN_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return null;
    }

    private String resolveChannelId(final String botToken, final String channelName) throws IOException {
        String cursor = null;
        do {
            final StringBuilder url = new StringBuilder(SLACK_API_BASE)
                    .append("conversations.list?types=public_channel,private_channel&limit=200");
            if (cursor != null) {
                url.append("&cursor=").append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
            }
            final HttpGet get = new HttpGet(url.toString());
            get.setHeader("Authorization", "Bearer " + botToken);

            final JsonNode response = httpClient.execute(get, httpResponse -> {
                final String body = EntityUtils.toString(httpResponse.getEntity());
                return objectMapper.readTree(body);
            });

            if (response == null || !response.path("ok").asBoolean(false)) {
                log.error("conversations.list failed: {}", response);
                return null;
            }

            for (final JsonNode channel : response.path("channels")) {
                if (channelName.equals(channel.path("name").asText())) {
                    return channel.path("id").asText(null);
                }
            }
            cursor = response.path("response_metadata").path("next_cursor").asText(null);
            if (cursor != null && cursor.isBlank()) {
                cursor = null;
            }
        } while (cursor != null);

        return null;
    }

    private JsonNode getUploadUrl(final String botToken, final String filename, final int length) throws IOException {
        final HttpPost post = new HttpPost(SLACK_API_BASE + "files.getUploadURLExternal");
        post.setHeader("Authorization", "Bearer " + botToken);
        post.setEntity(
                MultipartEntityBuilder.create().addTextBody("filename", filename).addTextBody("length", String.valueOf(length)).build());

        return httpClient.execute(post, response -> {
            final String body = EntityUtils.toString(response.getEntity());
            return objectMapper.readTree(body);
        });
    }

    private boolean postFileBytes(final String uploadUrl, final byte[] fileBytes) throws IOException {
        final HttpPost post = new HttpPost(uploadUrl);
        post.setEntity(new ByteArrayEntity(fileBytes, ContentType.APPLICATION_OCTET_STREAM));

        return httpClient.execute(post, response -> {
            final int status = response.getCode();
            if (status >= 200 && status < 300) {
                return true;
            }
            final String body = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
            log.error("Slack file byte upload failed. Status: {}, Response: {}", status, body);
            return false;
        });
    }

    private boolean completeUpload(final String botToken, final String fileId, final String filename, final String channelId,
            final String initialComment) throws IOException {
        final Map<String, Object> fileEntry = new HashMap<>();
        fileEntry.put("id", fileId);
        fileEntry.put("title", filename);

        final Map<String, Object> payload = new HashMap<>();
        payload.put("files", List.of(fileEntry));
        payload.put("channel_id", channelId);
        if (initialComment != null && !initialComment.isBlank()) {
            payload.put("initial_comment", initialComment);
        }

        final HttpPost post = new HttpPost(SLACK_API_BASE + "files.completeUploadExternal");
        post.setHeader("Authorization", "Bearer " + botToken);
        post.setHeader("Content-Type", "application/json; charset=utf-8");
        post.setEntity(new StringEntity(objectMapper.writeValueAsString(payload), ContentType.APPLICATION_JSON));

        return httpClient.execute(post, response -> {
            final String body = EntityUtils.toString(response.getEntity());
            final JsonNode json = objectMapper.readTree(body);
            if (json.path("ok").asBoolean(false)) {
                log.info("Slack file upload completed for {}", filename);
                return true;
            }
            log.error("files.completeUploadExternal failed: {}", body);
            return false;
        });
    }
}
