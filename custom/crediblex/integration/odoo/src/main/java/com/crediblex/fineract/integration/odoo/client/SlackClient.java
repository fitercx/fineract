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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
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
     * Send a message to Slack via incoming webhook.
     *
     * @param payload
     *            The Slack message payload (can include blocks, attachments, etc.)
     * @return true if message was sent successfully, false otherwise
     */
    public boolean sendMessage(Map<String, Object> payload) {
        if (slackProperties.getWebhookUrl() == null || slackProperties.getWebhookUrl().isEmpty()) {
            log.error("Slack webhook URL is not configured");
            return false;
        }

        try {
            HttpPost httpPost = new HttpPost(slackProperties.getWebhookUrl());
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
}
