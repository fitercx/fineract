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

import java.util.List;
import java.util.Map;

/**
 * Service interface for Odoo integration read operations
 */
public interface OdooIntegrationReadPlatformService {

    /**
     * Test connection to Odoo server
     *
     * @return Map containing connection test results
     */
    Map<String, Object> testConnection();

    /**
     * Get Odoo account ID for a Fineract GL account code
     *
     * @param fineractAccountCode
     *            Fineract GL account code
     * @return Odoo account ID or null if not found
     */
    Integer getOdooAccountId(String fineractAccountCode);

    /**
     * Get Odoo account ID for a Fineract GL account code with business event context
     *
     * @param fineractAccountCode
     *            Fineract GL account code
     * @param businessEventType
     *            Business event type for context-aware remapping (can be null)
     * @return Odoo account ID or null if not found
     */
    Integer getOdooAccountId(String fineractAccountCode, String businessEventType);

    /**
     * Get default journal ID for journal entries
     *
     * @return Default journal ID or null if not found
     */
    Integer getDefaultJournalId();

    /**
     * Get journal ID based on transaction type
     *
     * @param transactionType
     *            Transaction type
     * @return Journal ID or null if not found
     */
    Integer getJournalIdForTransaction(String transactionType);

    /**
     * Get journal ID based on GL account code
     *
     * @param glCode
     *            GL account code
     * @return Journal ID or null if not found
     */
    Integer getJournalIdForGlCode(String glCode, String businessEventType, boolean isDebit);

    Integer getJournalIdByOdooCode(String odooCode);

    /**
     * Clear account mapping cache
     */
    void clearAccountMappingCache();

    /**
     * Preload account mappings for commonly used accounts
     *
     * @param fineractAccountCodes
     *            List of Fineract account codes to preload
     */
    void preloadAccountMappings(List<String> fineractAccountCodes);
}
