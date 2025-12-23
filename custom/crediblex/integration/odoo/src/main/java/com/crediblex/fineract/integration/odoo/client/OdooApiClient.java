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
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
    private Integer cachedUid = null;

    public OdooApiClient(OdooProperties odooProperties) {
        this.odooProperties = odooProperties;
        this.objectMapper = new ObjectMapper();

        // Configure HTTP client with timeouts
        RequestConfig config = RequestConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(odooProperties.getConnectionTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(odooProperties.getReadTimeout())).build();

        this.httpClient = HttpClients.custom().setDefaultRequestConfig(config).build();
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

        // Return cached UID if available and still authenticated
        if (cachedUid != null && authenticated) {
            log.debug("Using cached UID: {}", cachedUid);
            return cachedUid;
        }

        try {
            log.info("Authenticating with Odoo server: {}", odooProperties.getUrl());

            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("method", "call");

            Map<String, Object> params = new HashMap<>();
            params.put("service", "common");
            params.put("method", "authenticate");
            params.put("args", Arrays.asList(odooProperties.getDatabase(), odooProperties.getUsername(), odooProperties.getPassword(),
                    new HashMap<>()));

            request.put("params", params);
            request.put("id", generateRequestId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = makeRequest("/jsonrpc", request);

            if (response.containsKey("error")) {
                log.error("Authentication failed: {}", response.get("error"));
                this.authenticated = false;
                this.cachedUid = null;
                return null;
            }

            Object result = response.get("result");
            if (!(result instanceof Number)) {
                log.error("Invalid authentication response: {}", result);
                this.authenticated = false;
                this.cachedUid = null;
                return null;
            }

            Integer uid = ((Number) result).intValue();
            this.authenticated = true;
            this.cachedUid = uid; // Cache the UID for future use
            log.info("Successfully authenticated with Odoo as UID: {}", uid);
            return uid;

        } catch (Exception e) {
            log.error("Failed to authenticate with Odoo", e);
            this.authenticated = false;
            this.cachedUid = null;
            return null;
        }
    }

    /**
     * Execute a method on an Odoo model
     */
    @SuppressWarnings("unchecked")
    public Object executeKw(Integer uid, String model, String method, List<Object> args, Map<String, Object> kwargs) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("method", "call");

            Map<String, Object> params = new HashMap<>();
            params.put("service", "object");
            params.put("method", "execute_kw");

            List<Object> executeArgs = new ArrayList<>();
            executeArgs.add(odooProperties.getDatabase());
            executeArgs.add(uid);
            executeArgs.add(odooProperties.getPassword());
            executeArgs.add(model);
            executeArgs.add(method);
            executeArgs.add(args);
            if (kwargs != null && !kwargs.isEmpty()) {
                executeArgs.add(kwargs);
            }

            params.put("args", executeArgs);
            request.put("params", params);
            request.put("id", generateRequestId());

            Map<String, Object> response = makeRequest("/jsonrpc", request);

            if (response.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) response.get("error");
                String errorMessage = extractErrorMessage(error);
                log.error("Failed to execute method {}::{}: {}", model, method, errorMessage);
                throw new RuntimeException("Odoo API error for " + model + "::" + method + ": " + errorMessage);
            }

            return response.get("result");

        } catch (RuntimeException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (Exception e) {
            log.error("Exception while executing method {}::{}", model, method, e);
            throw new RuntimeException("Failed to execute " + model + "::" + method + " in Odoo: " + e.getMessage(), e);
        }
    }

    /**
     * Extract readable error message from Odoo error response
     */
    @SuppressWarnings("unchecked")
    private String extractErrorMessage(Map<String, Object> error) {
        try {
            if (error.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) error.get("data");
                if (data.containsKey("message")) {
                    return data.get("message").toString();
                }
                if (data.containsKey("name")) {
                    return data.get("name").toString();
                }
            }
            if (error.containsKey("message")) {
                return error.get("message").toString();
            }
            return error.toString();
        } catch (Exception e) {
            return "Unable to parse error details: " + error.toString();
        }
    }

    /**
     * Search for records in Odoo
     */
    @SuppressWarnings("unchecked")
    public List<Integer> search(Integer uid, String model, List<Object> domain) {
        Object result = executeKw(uid, model, "search", Arrays.asList(domain), null);
        if (result instanceof List) {
            List<Object> resultList = (List<Object>) result;
            return resultList.stream().map(obj -> obj instanceof Number ? ((Number) obj).intValue() : null).filter(Objects::nonNull)
                    .toList();
        }
        return Collections.emptyList();
    }

    /**
     * Read records from Odoo
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> read(Integer uid, String model, List<Integer> ids, List<String> fields) {
        Object result = executeKw(uid, model, "read", Arrays.asList(ids), fields != null ? Map.of("fields", fields) : null);
        if (result instanceof List) {
            return (List<Map<String, Object>>) result;
        }
        return Collections.emptyList();
    }

    /**
     * Search and read records in one call
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchRead(Integer uid, String model, List<Object> domain, List<String> fields) {
        Map<String, Object> kwargs = new HashMap<>();
        if (fields != null) {
            kwargs.put("fields", fields);
        }

        Object result = executeKw(uid, model, "search_read", Arrays.asList(domain), kwargs);
        if (result instanceof List) {
            return (List<Map<String, Object>>) result;
        }
        return Collections.emptyList();
    }

    /**
     * Create a record in Odoo
     */
    public Long create(Integer uid, String model, Map<String, Object> values) {
        try {
            Object result = executeKw(uid, model, "create", Arrays.asList(values), null);
            if (result instanceof Number) {
                return ((Number) result).longValue();
            }
            log.error("Failed to create {} record in Odoo - unexpected result type: {}", model, result);
            return null;
        } catch (Exception e) {
            log.error("Exception while creating {} record in Odoo", model, e);
            throw new RuntimeException("Failed to create " + model + " in Odoo: " + e.getMessage(), e);
        }
    }

    /**
     * Call a specific method on records
     */
    public Object callMethod(Integer uid, String model, String method, List<Object> recordIds) {
        return executeKw(uid, model, method, Arrays.asList(recordIds), null);
    }

    /**
     * Post an account move (journal entry)
     */
    public Boolean postAccountMove(Integer uid, Long moveId) {
        Object result = callMethod(uid, "account.move", "action_post", Arrays.asList(moveId));
        return result != null;
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

    /**
     * Clear cached authentication (useful when authentication expires or credentials change)
     */
    public void clearAuthenticationCache() {
        this.cachedUid = null;
        this.authenticated = false;
        log.info("Cleared cached authentication");
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (Exception e) {
            log.warn("Error closing HTTP client", e);
        }
    }
}
