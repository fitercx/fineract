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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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

    // GL Code to Journal Code mapping - this can be externalized to configuration
    private final Map<String, Set<String>> journalGlCodeMapping = initializeJournalGlCodeMapping();

    /**
     * Initialize the mapping of journal codes to GL codes This can be moved to configuration properties or database
     * table for better maintainability
     */
    private Map<String, Set<String>> initializeJournalGlCodeMapping() {
        Map<String, Set<String>> mapping = new HashMap<>();

        // BNK5 journal for specific GL codes
        mapping.put("BNK5", Set.of("100031", "300004", "200065", "200040"));

        // BNK1 (Spend Money from bank) journal for specific GL codes
        mapping.put("BNK1", Set.of("200040", "100003"));

        // BNK7 (Cash margin) journal for specific GL codes
        mapping.put("BNK7", Set.of("100006", "23101001"));

        // BNK8 (Repayments) journal for specific GL codes
        // mapping.put("BNK8", Set.of("100031", "100034", "100001"));

        // Add more journal mappings as needed
        // mapping.put("MISC", Set.of("400001", "400002", "400003"));
        // mapping.put("CASH", Set.of("100001", "100002"));

        return mapping;
    }

    @Override
    public Map<String, Object> testConnection() {
        try {
            log.info("Testing Odoo connection...");
            boolean isConnected = odooApiClient.testConnection();

            if (isConnected) {
                log.info("Odoo connection test successful");
                return Map.of(SUCCESS_KEY, true, MESSAGE_KEY, "Successfully connected to Odoo server", TIMESTAMP_KEY,
                        System.currentTimeMillis());
            } else {
                log.warn("Odoo connection test failed");
                return Map.of(SUCCESS_KEY, false, MESSAGE_KEY, "Failed to connect to Odoo server", TIMESTAMP_KEY,
                        System.currentTimeMillis());
            }
        } catch (Exception e) {
            log.error("Odoo connection test failed with exception", e);
            return Map.of(SUCCESS_KEY, false, MESSAGE_KEY, "Connection test failed: " + e.getMessage(), ERROR_KEY,
                    e.getClass().getSimpleName(), TIMESTAMP_KEY, System.currentTimeMillis());
        }
    }

    @Override
    public Integer getOdooAccountId(String fineractAccountCode) {
        return getOdooAccountId(fineractAccountCode, null);
    }

    /**
     * Get Odoo account ID for a Fineract GL code with optional business event context
     * 
     * @param fineractAccountCode The Fineract GL code
     * @param businessEventType Optional business event type for context-aware remapping
     * @return The Odoo account ID
     */
    public Integer getOdooAccountId(String fineractAccountCode, String businessEventType) {
        if (fineractAccountCode == null) {
            return null;
        }

        // Remap GL code if needed (for special cases like 210003 → 100003)
        // Only remap for specific business event types to avoid affecting other transactions
        String odooAccountCode = remapGLCodeForOdoo(fineractAccountCode, businessEventType);

        // Check cache first (use remapped code for cache lookup)
        if (accountMappingCache.containsKey(odooAccountCode)) {
            return accountMappingCache.get(odooAccountCode);
        }

        try {
            Integer uid = odooApiClient.authenticate();
            if (uid == null) {
                log.error("Failed to authenticate with Odoo for account mapping");
                return null;
            }

            // Search for account by code in Odoo (use remapped code)
            List<Object> domain = Arrays.asList(Arrays.asList("code", "=", odooAccountCode));

            List<Map<String, Object>> accounts = odooApiClient.searchRead(uid, "account.account", domain,
                    Arrays.asList("id", "code", "name"));

            if (!accounts.isEmpty()) {
                Map<String, Object> account = accounts.get(0);
                Integer accountId = ((Number) account.get("id")).intValue();

                // Cache the mapping (using remapped code)
                accountMappingCache.put(odooAccountCode, accountId);

                if (!odooAccountCode.equals(fineractAccountCode)) {
                    log.debug("Mapped Fineract account '{}' (remapped to '{}' for business event '{}') to Odoo account ID: {} ({})", 
                        fineractAccountCode, odooAccountCode, businessEventType, accountId, account.get("name"));
                } else {
                    log.debug("Mapped Fineract account '{}' to Odoo account ID: {} ({})", 
                        fineractAccountCode, accountId, account.get("name"));
                }

                return accountId;
            } else {
                log.warn("No Odoo account found for Fineract account code: {} (remapped to: {})", 
                    fineractAccountCode, odooAccountCode);
                return null;
            }

        } catch (Exception e) {
            log.error("Error mapping account code '{}' (remapped to '{}') to Odoo", fineractAccountCode, odooAccountCode, e);
            return null;
        }
    }

    /**
     * Remap Fineract GL codes to Odoo GL codes for special cases
     * This handles scenarios where Fineract uses one GL code but Odoo expects a different one
     * 
     * @param fineractGlCode The GL code from Fineract
     * @param businessEventType The business event type for context-aware remapping
     * @return The GL code to use for Odoo lookup
     */
    private String remapGLCodeForOdoo(String fineractGlCode, String businessEventType) {
        if (fineractGlCode == null) {
            return null;
        }

        // Remap 210003 (Working Capital Loan) → 100003 (Bank Account)
        // ONLY for SAVINGS_WITHDRAWAL (loan disbursement to savings)
        // Do NOT remap for SAVINGS_DEPOSIT (repayments) or REPAYMENT events
        if ("210003".equals(fineractGlCode)) {
            // Only remap for disbursement-related events
            if ("SAVINGS_WITHDRAWAL".equals(businessEventType) || "DISBURSEMENT".equals(businessEventType)) {
                log.debug("Remapping GL code 210003 → 100003 for Odoo (business event: {})", businessEventType);
                return "100003";
            } else {
                // For SAVINGS_DEPOSIT, REPAYMENT, or other events, keep 210003 as-is
                log.debug("NOT remapping GL code 210003 for business event: {} (keeping original)", businessEventType);
                return fineractGlCode;
            }
        }

        // Add more remappings here as needed
        // Example: if ("XXXXX".equals(fineractGlCode)) { return "YYYYY"; }

        // No remapping needed, return original code
        return fineractGlCode;
    }

    @Override
    public Integer getDefaultJournalId() {
        // Default fallback - use BNK5 if no specific mapping found
        return getJournalIdByOdooCode("BNK5");
    }

    /**
     * Get journal ID based on GL account code, business event type and debit flag
     */
    public Integer getJournalIdForGlCode(String glCode, String businessEventType, boolean isDebit) {
        if (glCode == null) {
            log.warn("GL code is null, no journal available");
            return null;
        }

        // Find which journal this GL code belongs to based on business event type and debit flag
        String journalCode = findJournalCodeForGlCode(glCode, businessEventType, isDebit);
        if (journalCode != null) {
            log.debug("GL code {} with business event type {} and isDebit {} mapped to journal {}", glCode, businessEventType, isDebit,
                    journalCode);
            return getJournalIdByOdooCode(journalCode);
        } else {
            log.debug("No specific journal mapping found for GL code {} with business event type {} and isDebit {}, skipping journal entry",
                    glCode, businessEventType, isDebit);
            return null;
        }
    }

    /**
     * Find journal code for a given GL code, business event type and debit flag
     */
    private String findJournalCodeForGlCode(String glCode, String businessEventType, boolean isDebit) {
        if (glCode == null) {
            return null;
        }

        // BNK5 journal for DISBURSEMENT business events with specific GL codes
        if ("DISBURSEMENT".equals(businessEventType) && Set.of("100031", "300004", "200065", "200040").contains(glCode)) {
            return "BNK5";
        }

        // BNK6 journal for SAVINGS_WITHDRAWAL business events i.e. Spend Money from bank
        if ("SAVINGS_WITHDRAWAL".equals(businessEventType) && Set.of("200040", "100003").contains(glCode)) {
            return "BNK6";
        }

        // BNK5 journal for SAVINGS_WITHDRAWAL i.e. refund from savings account
        if ("SAVINGS_WITHDRAWAL".equals(businessEventType)) {
            // When GL code is 100062
            if ("100062".equals(glCode)) {
                return "BNK10";
            }
            // When GL code is 210003 and it's a debit transaction
            if ("210003".equals(glCode) && isDebit) {
                return "BNK10";
            }
        }

        // BNK4 journal for SAVINGS_DEPOSIT i.e. deposit money to savings account
        if ("SAVINGS_DEPOSIT".equals(businessEventType)) {
            // When GL code is 210003
            if ("210003".equals(glCode)) {
                return "BNK9";
            }
            // When GL code is 100062 and it's a debit transaction
            if ("100062".equals(glCode) && isDebit) {
                return "BNK9";
            }
        }

        // For GL code 200040, only map to BNK1 if it's a debit transaction
        if ("200040".equals(glCode) && isDebit) {
            return "BNK1";
        }

        // For GL code 100003, map regardless of debit/credit
        if ("100003".equals(glCode)) {
            return "BNK1";
        }

        // BNK7 (Cash margin) journal for specific GL codes
        if (Set.of("100006", "210002").contains(glCode)) {
            return "BNK7";
        }

        // BNK8 journal for DISBURSEMENT business events with specific GL codes
        if ("REPAYMENT".equals(businessEventType) && Set.of("210003", "100031", "100034", "100030", "100001").contains(glCode)) {
            return "BNK8";
        }

        return null; // No mapping found
    }

    /**
     * Get journal ID by journal code from Odoo
     */
    public Integer getJournalIdByOdooCode(String journalCode) {
        String journalKey = "journal_" + journalCode;

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

            // Search for journal with specific code
            List<Object> domain = Arrays.asList(Arrays.asList("code", "=", journalCode));

            List<Map<String, Object>> journals = odooApiClient.searchRead(uid, "account.journal", domain,
                    Arrays.asList("id", "name", "code", "type"));

            if (!journals.isEmpty()) {
                Map<String, Object> journal = journals.get(0);
                Integer journalId = ((Number) journal.get("id")).intValue();

                // Cache the mapping
                journalMappingCache.put(journalKey, journalId);

                log.debug("Using journal ID: {} ({}) with code '{}' for journal entries", journalId, journal.get("name"), journalCode);

                return journalId;
            } else {
                log.error("No journal found in Odoo with code '{}'", journalCode);
                return null;
            }

        } catch (Exception e) {
            log.error("Error getting journal from Odoo for code '{}'", journalCode, e);
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

    /**
     * Add GL codes to a journal mapping This allows for dynamic configuration of journal mappings
     */
    public void addGlCodesToJournal(String journalCode, Set<String> glCodes) {
        journalGlCodeMapping.computeIfAbsent(journalCode, k -> new java.util.HashSet<>()).addAll(glCodes);
        log.info("Added {} GL codes to journal {}: {}", glCodes.size(), journalCode, glCodes);
    }

    /**
     * Remove GL codes from journal mapping
     */
    public void removeGlCodesFromJournal(String journalCode, Set<String> glCodes) {
        Set<String> existingCodes = journalGlCodeMapping.get(journalCode);
        if (existingCodes != null) {
            existingCodes.removeAll(glCodes);
            log.info("Removed {} GL codes from journal {}: {}", glCodes.size(), journalCode, glCodes);
        }
    }

    /**
     * Get current journal GL code mappings
     */
    public Map<String, Set<String>> getJournalGlCodeMappings() {
        return new HashMap<>(journalGlCodeMapping);
    }
}
