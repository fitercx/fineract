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

import com.crediblex.fineract.integration.odoo.config.OdooProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Odoo API client for connecting to Odoo ERP system
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "odoo.enabled", havingValue = "true", matchIfMissing = false)
public class OdooApiClient {

    private final OdooProperties odooProperties;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private boolean authenticated = false;

    public OdooApiClient(OdooProperties odooProperties) {
        this.odooProperties = odooProperties;
        this.objectMapper = new ObjectMapper();
        
        // Configure HTTP client with timeouts
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(odooProperties.getConnectionTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(odooProperties.getReadTimeout()))
                .build();
                
        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build();
    }

    /**
     * Test connection to Odoo server
     */
    public boolean testConnection() {
        try {
            authenticate();
            return authenticated;
        } catch (Exception e) {
            log.error("Connection test failed", e);
            return false;
        }
    }

    /**
     * Authenticate with Odoo server
     */
    public Integer authenticate() {
        if (!odooProperties.getEnabled()) {
            log.warn("Odoo integration is disabled");
            return null;
        }

        try {
            log.info("Authenticating with Odoo server: {}", odooProperties.getUrl());
            
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("method", "call");
            
            Map<String, Object> params = new HashMap<>();
            params.put("service", "common");
            params.put("method", "authenticate");
            params.put("args", Arrays.asList(
                odooProperties.getDatabase(),
                odooProperties.getUsername(),
                odooProperties.getPassword(),
                new HashMap<>()
            ));
            
            request.put("params", params);
            request.put("id", generateRequestId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = makeRequest("/jsonrpc", request);
            
            if (response.containsKey("error")) {
                log.error("Authentication failed: {}", response.get("error"));
                return null;
            }

            Object result = response.get("result");
            if (!(result instanceof Number)) {
                log.error("Invalid authentication response: {}", result);
                return null;
            }

            Integer uid = ((Number) result).intValue();
            this.authenticated = true;
            log.info("Successfully authenticated with Odoo as UID: {}", uid);
            return uid;
            
        } catch (Exception e) {
            log.error("Failed to authenticate with Odoo", e);
            this.authenticated = false;
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> makeRequest(String endpoint, Map<String, Object> requestData) throws IOException {
        String url = odooProperties.getUrl() + endpoint;
        HttpPost httpPost = new HttpPost(url);
        
        // Set headers
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Accept", "application/json");
        
        // Set request body
        String jsonRequest = objectMapper.writeValueAsString(requestData);
        httpPost.setEntity(new StringEntity(jsonRequest, ContentType.APPLICATION_JSON));
        
        log.debug("Making request to Odoo: {}", url);
        
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                String responseBody = EntityUtils.toString(entity);
                log.debug("Odoo response: {}", responseBody);
                return objectMapper.readValue(responseBody, Map.class);
            }
            throw new IOException("Empty response from Odoo server");
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response from Odoo server", e);
        }
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            log.warn("Failed to close HTTP client", e);
        }
    }
}