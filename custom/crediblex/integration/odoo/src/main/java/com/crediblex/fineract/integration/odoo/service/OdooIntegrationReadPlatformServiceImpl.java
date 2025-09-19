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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    
    // Cache for account mappings to avoid repeated Odoo calls
    private final Map<String, Integer> accountMappingCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> journalMappingCache = new ConcurrentHashMap<>();

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

    @Override
    public Integer getOdooAccountId(String fineractAccountCode) {
        if (fineractAccountCode == null) {
            return null;
        }

        // Check cache first
        if (accountMappingCache.containsKey(fineractAccountCode)) {
            return accountMappingCache.get(fineractAccountCode);
        }

        try {
            Integer uid = odooApiClient.authenticate();
            if (uid == null) {
                log.error("Failed to authenticate with Odoo for account mapping");
                return null;
            }

            // Search for account by code in Odoo
            List<Object> domain = Arrays.asList(
                Arrays.asList("code", "=", fineractAccountCode)
            );

            List<Map<String, Object>> accounts = odooApiClient.searchRead(
                uid, 
                "account.account", 
                domain, 
                Arrays.asList("id", "code", "name")
            );

            if (!accounts.isEmpty()) {
                Map<String, Object> account = accounts.get(0);
                Integer accountId = ((Number) account.get("id")).intValue();
                
                // Cache the mapping
                accountMappingCache.put(fineractAccountCode, accountId);
                
                log.debug("Mapped Fineract account '{}' to Odoo account ID: {} ({})", 
                    fineractAccountCode, accountId, account.get("name"));
                
                return accountId;
            } else {
                log.warn("No Odoo account found for Fineract account code: {}", fineractAccountCode);
                return null;
            }

        } catch (Exception e) {
            log.error("Error mapping account code '{}' to Odoo", fineractAccountCode, e);
            return null;
        }
    }

    @Override
    public Integer getDefaultJournalId() {
        String journalKey = "default";
        
        // Check cache first
        if (journalMappingCache.containsKey(journalKey)) {
            return journalMappingCache.get(journalKey);
        }

        try {
            Integer uid = odooApiClient.authenticate();
            if (uid == null) {
                log.error("Failed to authenticate with Odoo for journal mapping");
                return null;
            }

            // Search for a journal with type 'general' or 'miscellaneous'
            List<Object> domain = Arrays.asList(
                Arrays.asList("type", "in", Arrays.asList("general", "miscellaneous"))
            );

            List<Map<String, Object>> journals = odooApiClient.searchRead(
                uid, 
                "account.journal", 
                domain, 
                Arrays.asList("id", "name", "code", "type")
            );

            if (!journals.isEmpty()) {
                Map<String, Object> journal = journals.get(0);
                Integer journalId = ((Number) journal.get("id")).intValue();
                
                // Cache the mapping
                journalMappingCache.put(journalKey, journalId);
                
                log.debug("Using default journal ID: {} ({}) for journal entries", 
                    journalId, journal.get("name"));
                
                return journalId;
            } else {
                log.error("No suitable journal found in Odoo for journal entries");
                return null;
            }

        } catch (Exception e) {
            log.error("Error getting default journal from Odoo", e);
            return null;
        }
    }

    @Override
    public Integer getJournalIdForTransaction(String transactionType) {
        // You can implement specific logic here to map different transaction types
        // to different journals in Odoo
        
        // For now, return the default journal
        return getDefaultJournalId();
    }

    @Override
    public void clearAccountMappingCache() {
        accountMappingCache.clear();
        journalMappingCache.clear();
        log.info("Cleared Odoo account mapping cache");
    }

    @Override
    public void preloadAccountMappings(List<String> fineractAccountCodes) {
        log.info("Preloading account mappings for {} accounts", fineractAccountCodes.size());
        
        for (String accountCode : fineractAccountCodes) {
            getOdooAccountId(accountCode);
        }
        
        log.info("Completed preloading account mappings");
    }
}
