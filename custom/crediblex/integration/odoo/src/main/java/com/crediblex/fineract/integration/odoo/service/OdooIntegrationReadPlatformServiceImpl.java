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

import com.crediblex.fineract.integration.odoo.client.OdooApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Implementation of Odoo integration read platform service
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "odoo.enabled", havingValue = "true", matchIfMissing = false)
public class OdooIntegrationReadPlatformServiceImpl implements OdooIntegrationReadPlatformService {

    private static final String SUCCESS_KEY = "success";
    private static final String MESSAGE_KEY = "message";
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String ERROR_KEY = "error";

    private final OdooApiClient odooApiClient;

    @Override
    public Map<String, Object> testConnection() {
        try {
            log.info("Testing Odoo connection...");
            boolean isConnected = odooApiClient.testConnection();
            
            if (isConnected) {
                log.info("Odoo connection test successful");
                return Map.of(
                    SUCCESS_KEY, true,
                    MESSAGE_KEY, "Successfully connected to Odoo server",
                    TIMESTAMP_KEY, System.currentTimeMillis()
                );
            } else {
                log.warn("Odoo connection test failed");
                return Map.of(
                    SUCCESS_KEY, false,
                    MESSAGE_KEY, "Failed to connect to Odoo server",
                    TIMESTAMP_KEY, System.currentTimeMillis()
                );
            }
        } catch (Exception e) {
            log.error("Odoo connection test failed with exception", e);
            return Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "Connection test failed: " + e.getMessage(),
                ERROR_KEY, e.getClass().getSimpleName(),
                TIMESTAMP_KEY, System.currentTimeMillis()
            );
        }
    }
}
