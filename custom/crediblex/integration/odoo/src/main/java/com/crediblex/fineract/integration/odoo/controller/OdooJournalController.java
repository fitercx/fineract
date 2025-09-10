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
package com.crediblex.fineract.integration.odoo.controller;

import com.crediblex.fineract.integration.odoo.service.OdooJournalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simplified REST API controller for Odoo journal entries
 */
@RestController
@RequestMapping("/api/v1/odoo")
@RequiredArgsConstructor
@Slf4j
public class OdooJournalController {

    private final OdooJournalService odooJournalService;

    /**
     * Test Odoo connection
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection() {
        try {
            boolean connected = odooJournalService.testConnection();
            return ResponseEntity.ok(Map.of("connected", connected, "message", connected ? "Connection successful" : "Connection failed"));
        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("connected", false, "message", "Connection failed: " + e.getMessage()));
        }
    }

    /**
     * Post journal entry to Odoo
     */
    @PostMapping("/journal-entry")
    public ResponseEntity<Map<String, Object>> postJournalEntry(@RequestBody JournalEntryRequest request) {
        try {
            Map<String, Object> result = odooJournalService.postJournalEntry(request.getReference(), request.getDate(),
                    request.getDescription(), request.getAmount(), request.getDebitAccount(), request.getCreditAccount());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Journal entry posting failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Journal entry request DTO
     */
    public static class JournalEntryRequest {

        private String reference;
        private LocalDate date;
        private String description;
        private BigDecimal amount;
        private String debitAccount;
        private String creditAccount;

        // Getters and setters
        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getDebitAccount() {
            return debitAccount;
        }

        public void setDebitAccount(String debitAccount) {
            this.debitAccount = debitAccount;
        }

        public String getCreditAccount() {
            return creditAccount;
        }

        public void setCreditAccount(String creditAccount) {
            this.creditAccount = creditAccount;
        }
    }
}
