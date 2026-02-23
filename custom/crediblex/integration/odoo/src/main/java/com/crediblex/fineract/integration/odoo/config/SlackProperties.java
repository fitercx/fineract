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
package com.crediblex.fineract.integration.odoo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Slack integration.
 * Used to send notifications when Odoo sync failures occur.
 */
@Data
@Component
@ConfigurationProperties(prefix = "slack")
public class SlackProperties {

    /**
     * Enable/disable Slack notifications
     */
    private Boolean enabled = false;

    /**
     * Slack Incoming Webhook URL
     * Format: https://hooks.slack.com/services/XXXXX/YYYYY/ZZZZZ
     */
    private String webhookUrl;

    /**
     * Optional: Override the default channel configured in webhook
     */
    private String channel;

    /**
     * Bot username displayed in Slack
     */
    private String username = "Fineract Odoo Sync Bot";

    /**
     * Emoji icon for the bot
     */
    private String iconEmoji = ":warning:";

    /**
     * HTTP connection timeout in milliseconds
     */
    private Integer connectTimeout = 5000;

    /**
     * HTTP read timeout in milliseconds
     */
    private Integer readTimeout = 10000;

    /**
     * Maximum number of failed entries to show in detail
     */
    private Integer maxFailedEntriesToShow = 10;
}
