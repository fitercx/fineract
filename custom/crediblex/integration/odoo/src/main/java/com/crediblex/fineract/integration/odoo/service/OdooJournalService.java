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
import com.crediblex.fineract.integration.odoo.exception.OdooApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Simplified service for posting journal entries to Odoo
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OdooJournalService {

    private final OdooApiClient odooApiClient;

    private static final String JOURNAL_ENTRY_MODEL = "account.move";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    /**
     * Post a simple journal entry to Odoo
     */
    public Map<String, Object> postJournalEntry(String reference, LocalDate date, String description, BigDecimal amount,
            String debitAccount, String creditAccount) {
        try {
            log.info("Posting journal entry to Odoo: {} for amount {}", reference, amount);

            // Create journal entry data
            Map<String, Object> entryData = new HashMap<>();
            entryData.put("move_type", "entry");
            entryData.put("ref", reference);
            entryData.put("date", date.format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
            entryData.put("journal_id", 1); // Use default journal (you may want to make this configurable)

            // Create journal entry
            Integer journalEntryId = odooApiClient.create(JOURNAL_ENTRY_MODEL, entryData);

            // Create journal entry lines
            createJournalLine(journalEntryId, debitAccount, description + " - Debit", amount, BigDecimal.ZERO);
            createJournalLine(journalEntryId, creditAccount, description + " - Credit", BigDecimal.ZERO, amount);

            log.info("Successfully posted journal entry to Odoo with ID: {}", journalEntryId);

            return Map.of("success", true, "odoo_journal_entry_id", journalEntryId, "reference", reference, "amount", amount);

        } catch (Exception e) {
            log.error("Failed to post journal entry to Odoo: {}", e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage(), "reference", reference);
        }
    }

    /**
     * Test connection to Odoo
     */
    public boolean testConnection() {
        try {
            return odooApiClient.testConnection();
        } catch (Exception e) {
            log.error("Odoo connection test failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Create a journal line
     */
    private void createJournalLine(Integer moveId, String account, String name, BigDecimal debit, BigDecimal credit)
            throws OdooApiException {
        Map<String, Object> lineData = new HashMap<>();
        lineData.put("move_id", moveId);
        lineData.put("account_id", account); // This should be the account ID or code
        lineData.put("name", name);
        lineData.put("debit", debit);
        lineData.put("credit", credit);

        odooApiClient.create("account.move.line", lineData);
    }
}
