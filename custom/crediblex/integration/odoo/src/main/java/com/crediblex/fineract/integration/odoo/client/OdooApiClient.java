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
import com.crediblex.fineract.integration.odoo.exception.OdooApiException;
import com.crediblex.fineract.integration.odoo.exception.OdooAuthenticationException;
import com.crediblex.fineract.integration.odoo.exception.OdooConnectionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Simplified Odoo API client for journal entries only
 */
@Component
public class OdooApiClient {

    private static final Logger log = LoggerFactory.getLogger(OdooApiClient.class);

    private final OdooProperties odooProperties;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    private Integer uid;
    private boolean authenticated = false;

    @Autowired
    public OdooApiClient(OdooProperties odooProperties) {
        this.odooProperties = odooProperties;
        this.objectMapper = new ObjectMapper();

        // Configure HTTP client with timeouts
        RequestConfig config = RequestConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(odooProperties.getConnectionTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(odooProperties.getReadTimeout())).build();

        this.httpClient = HttpClients.custom().setDefaultRequestConfig(config).build();
    }

    /**
     * Authenticate with Odoo server
     */
    public Integer authenticate() throws OdooAuthenticationException {
        if (!odooProperties.getEnabled()) {
            throw new OdooAuthenticationException("Odoo integration is disabled");
        }

        try {
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

            Map<String, Object> response = makeRequest("/jsonrpc", request);

            if (response.containsKey("error")) {
                throw new OdooAuthenticationException("Authentication failed: " + response.get("error"));
            }

            Object result = response.get("result");
            if (!(result instanceof Number)) {
                throw new OdooAuthenticationException("Invalid authentication response");
            }

            this.uid = ((Number) result).intValue();
            this.authenticated = true;

            log.info("Successfully authenticated with Odoo as UID: {}", uid);
            return uid;

        } catch (Exception e) {
            log.error("Failed to authenticate with Odoo", e);
            throw new OdooAuthenticationException("Authentication failed", e);
        }
    }

    /**
     * Create record in Odoo
     */
    public Integer create(String model, Map<String, Object> values) throws OdooApiException {
        ensureAuthenticated();

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("method", "call");

            Map<String, Object> params = new HashMap<>();
            params.put("service", "object");
            params.put("method", "execute");

            List<Object> args = new ArrayList<>();
            args.add(odooProperties.getDatabase());
            args.add(uid);
            args.add(odooProperties.getPassword());
            args.add(model);
            args.add("create");
            args.add(values);

            params.put("args", args);
            request.put("params", params);
            request.put("id", generateRequestId());

            Map<String, Object> response = makeRequest("/jsonrpc", request);

            if (response.containsKey("error")) {
                throw new OdooApiException("Create failed: " + response.get("error"));
            }

            Object result = response.get("result");
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }

            throw new OdooApiException("Failed to create record - invalid response");

        } catch (Exception e) {
            log.error("Failed to create record in Odoo model: {}", model, e);
            throw new OdooApiException("Create operation failed", e);
        }
    }

    /**
     * Test connection to Odoo
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

    private void ensureAuthenticated() throws OdooAuthenticationException {
        if (!authenticated || uid == null) {
            authenticate();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> makeRequest(String endpoint, Map<String, Object> requestData) throws OdooConnectionException {
        try {
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

                throw new OdooConnectionException("Empty response from Odoo server");
            }

        } catch (IOException | org.apache.hc.core5.http.ParseException e) {
            log.error("Failed to make request to Odoo", e);
            throw new OdooConnectionException("Failed to communicate with Odoo server", e);
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
