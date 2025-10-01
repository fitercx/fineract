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
import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSync;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service for posting Fineract journal entries to Odoo
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "odoo.enabled", havingValue = "true")
public class OdooJournalEntryService {

    private final OdooApiClient odooApiClient;
    private final OdooIntegrationReadPlatformService odooIntegrationService;

    /**
     * Post a Fineract journal entry to Odoo
     */
    public Long postJournalEntryToOdoo(JournalEntry fineractEntry) {
        try {
            // Authenticate with Odoo
            Integer uid = odooApiClient.authenticate();
            if (uid == null) {
                throw new RuntimeException("Odoo authentication failed - check credentials and connection settings");
            }

            // Get journal ID based on GL account code
            String fineractAccountCode = fineractEntry.getGlAccount().getGlCode();
            Integer journalId = odooIntegrationService.getJournalIdForGlCode(fineractAccountCode);
            if (journalId == null) {
                throw new RuntimeException(String.format("No suitable journal found in Odoo for GL code '%s' - check journal mapping configuration", fineractAccountCode));
            }

            // Get account mapping
            Integer odooAccountId = odooIntegrationService.getOdooAccountId(fineractAccountCode);
            if (odooAccountId == null) {
                throw new RuntimeException(String.format("Could not map Fineract GL account '%s' to Odoo account - account may not exist in Odoo chart of accounts", fineractAccountCode));
            }

            // Create the account move
            Map<String, Object> moveValues = buildAccountMoveValues(fineractEntry, journalId, odooAccountId);

            Long odooMoveId = odooApiClient.create(uid, "account.move", moveValues);
            if (odooMoveId == null) {
                throw new RuntimeException(String.format("Failed to create account move in Odoo for journal entry %d - check move data and Odoo permissions", fineractEntry.getId()));
            }

            // Post the move (from draft to posted state)
            Boolean posted = odooApiClient.postAccountMove(uid, odooMoveId);
            if (!posted) {
                log.warn("Created move {} in Odoo but failed to post it - move remains in draft state", odooMoveId);
                // Still return the ID as the move was created successfully
            }

            log.info("Successfully posted Fineract journal entry {} to Odoo as move {}", fineractEntry.getId(), odooMoveId);

            return odooMoveId;

        } catch (RuntimeException e) {
            // Re-throw our specific exceptions
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while posting journal entry {} to Odoo", fineractEntry.getId(), e);
            throw new RuntimeException(String.format("Unexpected error posting journal entry %d to Odoo: %s", fineractEntry.getId(), e.getMessage()), e);
        }
    }

    /**
     * Build the account move values for Odoo
     */
    private Map<String, Object> buildAccountMoveValues(JournalEntry fineractEntry, Integer journalId, Integer accountId) {
        Map<String, Object> moveValues = new HashMap<>();

        // Basic move information
        moveValues.put("journal_id", journalId);
        moveValues.put("date", fineractEntry.getTransactionDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        moveValues.put("ref", "Haider JE " + fineractEntry.getId());
        moveValues.put("narration", buildNarration(fineractEntry));

        // Build line items
        List<Object> lines = buildMoveLines(fineractEntry, accountId);
        moveValues.put("line_ids", lines);

        return moveValues;
    }

    /**
     * Build move lines for the journal entry This creates balanced debit and credit entries
     */
    private List<Object> buildMoveLines(JournalEntry fineractEntry, Integer accountId) {
        List<Object> lines = new ArrayList<>();
        BigDecimal amount = fineractEntry.getAmount();

        // Determine if this is a debit or credit entry
        boolean isDebit = fineractEntry.isDebitEntry();

        if (isDebit) {
            // Debit line
            Map<String, Object> debitLine = new HashMap<>();
            debitLine.put("account_id", accountId);
            debitLine.put("debit", amount);
            debitLine.put("credit", BigDecimal.ZERO);
            debitLine.put("name", fineractEntry.getDescription() != null ? fineractEntry.getDescription() : "Haider Journal Entry");

            // Credit line (balancing entry) - you might need to determine this based on business logic
            Map<String, Object> creditLine = new HashMap<>();
            creditLine.put("account_id", getBalancingAccountId(fineractEntry));
            creditLine.put("debit", BigDecimal.ZERO);
            creditLine.put("credit", amount);
            creditLine.put("name",
                    "Balancing entry for " + (fineractEntry.getDescription() != null ? fineractEntry.getDescription() : "Haider JE"));

            // Add lines using Odoo's line creation format: (0, 0, values)
            lines.add(Arrays.asList(0, 0, debitLine));
            lines.add(Arrays.asList(0, 0, creditLine));

        } else {
            // Credit line
            Map<String, Object> creditLine = new HashMap<>();
            creditLine.put("account_id", accountId);
            creditLine.put("debit", BigDecimal.ZERO);
            creditLine.put("credit", amount);
            creditLine.put("name", fineractEntry.getDescription() != null ? fineractEntry.getDescription() : "Haider Journal Entry");

            // Debit line (balancing entry)
            Map<String, Object> debitLine = new HashMap<>();
            debitLine.put("account_id", getBalancingAccountId(fineractEntry));
            debitLine.put("debit", amount);
            debitLine.put("credit", BigDecimal.ZERO);
            debitLine.put("name",
                    "Balancing entry for " + (fineractEntry.getDescription() != null ? fineractEntry.getDescription() : "Haider JE"));

            // Add lines using Odoo's line creation format: (0, 0, values)
            lines.add(Arrays.asList(0, 0, creditLine));
            lines.add(Arrays.asList(0, 0, debitLine));
        }

        return lines;
    }

    /**
     * Get balancing account ID This is a simplified implementation - you should implement proper logic based on your
     * chart of accounts and business rules
     */
    private Integer getBalancingAccountId(JournalEntry fineractEntry) {
        // TODO: Implement proper balancing account logic
        // For now, return a default suspense/clearing account ID
        // You should map this to an actual account in your Odoo instance

        // This could be based on:
        // - Transaction type
        // - Office/branch
        // - Product type
        // - etc.

        return 999999; // Replace with actual account ID from your Odoo instance
    }

    /**
     * Build narration/memo for the move
     */
    private String buildNarration(JournalEntry fineractEntry) {
        StringBuilder narration = new StringBuilder();
        narration.append("Fineract Journal Entry ID: ").append(fineractEntry.getId());

        if (fineractEntry.getDescription() != null && !fineractEntry.getDescription().trim().isEmpty()) {
            narration.append("\nDescription: ").append(fineractEntry.getDescription());
        }

        if (fineractEntry.getReferenceNumber() != null && !fineractEntry.getReferenceNumber().trim().isEmpty()) {
            narration.append("\nReference: ").append(fineractEntry.getReferenceNumber());
        }

        // Add transaction context if available
        if (fineractEntry.getLoanTransactionId() != null) {
            narration.append("\nLoan Transaction ID: ").append(fineractEntry.getLoanTransactionId());
        }

        if (fineractEntry.getSavingsTransactionId() != null) {
            narration.append("\nSavings Transaction ID: ").append(fineractEntry.getSavingsTransactionId());
        }

        return narration.toString();
    }

    /**
     * Validate if a journal entry can be posted to Odoo
     */
    public boolean canPostToOdoo(JournalEntry fineractEntry) {
        if (fineractEntry == null) {
            return false;
        }

        if (fineractEntry.getGlAccount() == null || fineractEntry.getGlAccount().getGlCode() == null) {
            log.warn("Journal entry {} has no GL account or GL code", fineractEntry.getId());
            return false;
        }

        if (fineractEntry.getAmount() == null || fineractEntry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Journal entry {} has invalid amount: {}", fineractEntry.getId(), fineractEntry.getAmount());
            return false;
        }

        if (fineractEntry.getTransactionDate() == null) {
            log.warn("Journal entry {} has no transaction date", fineractEntry.getId());
            return false;
        }

        return true;
    }

    /**
     * Post multiple journal entries for a loan as grouped account moves in Odoo
     * Groups entries by their target journal based on GL codes
     */
    public Map<Integer, Long> postJournalEntriesForLoan(Long loanId, List<JournalEntryOdooSync> journalEntryOdooSyncs) {
        Map<Integer, Long> journalToMoveMap = new HashMap<>();
        
        try {
            // Authenticate with Odoo
            Integer uid = odooApiClient.authenticate();
            if (uid == null) {
                throw new RuntimeException("Odoo authentication failed for loan " + loanId + " - check credentials and connection settings");
            }

            // Group journal entries by target journal
            List<JournalEntry> journalEntries = journalEntryOdooSyncs.stream().map(JournalEntryOdooSync::getJournalEntry).toList();

            // Validate all entries
            for (JournalEntry entry : journalEntries) {
                if (!canPostToOdoo(entry)) {
                    throw new RuntimeException(String.format("Journal entry %d for loan %d failed validation - check GL account, amount, or transaction date", 
                                             entry.getId(), loanId));
                }
            }

            // Group entries by journal
            Map<Integer, List<JournalEntry>> entriesByJournal = groupEntriesByJournal(journalEntries);
            
            if (entriesByJournal.isEmpty()) {
                throw new RuntimeException(String.format("No valid journal mappings found for loan %d entries - check GL code to journal mapping configuration", loanId));
            }
            
            // Create separate account moves for each journal
            for (Map.Entry<Integer, List<JournalEntry>> journalGroup : entriesByJournal.entrySet()) {
                Integer journalId = journalGroup.getKey();
                List<JournalEntry> entries = journalGroup.getValue();
                
                // Create the account move with multiple lines for this journal
                Map<String, Object> moveValues = buildAccountMoveValuesForLoan(loanId, entries, journalId);

                Long odooMoveId = odooApiClient.create(uid, "account.move", moveValues);
                if (odooMoveId == null) {
                    throw new RuntimeException(String.format("Failed to create account move in Odoo for loan %d and journal %d - check move data and Odoo permissions", 
                                             loanId, journalId));
                }

                // Post the move (from draft to posted state)
                Boolean posted = odooApiClient.postAccountMove(uid, odooMoveId);
                if (!posted) {
                    log.warn("Created move {} in Odoo but failed to post it - move remains in draft state", odooMoveId);
                    // Continue anyway as the move was created
                }

                journalToMoveMap.put(journalId, odooMoveId);
                log.info("Successfully posted {} journal entries for loan {} to Odoo journal {} as move {}", 
                    entries.size(), loanId, journalId, odooMoveId);
            }

            return journalToMoveMap;

        } catch (RuntimeException e) {
            // Re-throw our specific exceptions
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while posting journal entries for loan {} to Odoo", loanId, e);
            throw new RuntimeException(String.format("Unexpected error posting journal entries for loan %d to Odoo: %s", loanId, e.getMessage()), e);
        }
    }
    
    /**
     * Group journal entries by their target journal based on GL codes
     */
    private Map<Integer, List<JournalEntry>> groupEntriesByJournal(List<JournalEntry> journalEntries) {
        Map<Integer, List<JournalEntry>> groupedEntries = new HashMap<>();
        
        for (JournalEntry entry : journalEntries) {
            String glCode = entry.getGlAccount().getGlCode();
            Integer journalId = odooIntegrationService.getJournalIdForGlCode(glCode);
            
            if (journalId != null) {
                groupedEntries.computeIfAbsent(journalId, k -> new ArrayList<>()).add(entry);
                log.debug("Assigned journal entry {} (GL: {}) to journal {}", entry.getId(), glCode, journalId);
            } else {
                log.warn("Could not determine journal for GL code {}, skipping entry {}", glCode, entry.getId());
            }
        }
        
        return groupedEntries;
    }

    /**
     * Build account move values for multiple journal entries of a loan
     */
    private Map<String, Object> buildAccountMoveValuesForLoan(Long loanId, List<JournalEntry> journalEntries, Integer journalId) {
        Map<String, Object> moveValues = new HashMap<>();

        // Use the first entry's date as move date
        JournalEntry firstEntry = journalEntries.get(0);
        moveValues.put("journal_id", journalId);
        moveValues.put("date", firstEntry.getTransactionDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        moveValues.put("ref", "Haider Loan " + loanId + " - JEs: "
                + journalEntries.stream().map(je -> je.getId().toString()).collect(Collectors.joining(", ")));
        moveValues.put("narration", buildLoanNarration(loanId, journalEntries));

        // Build consolidated line items
        List<Object> lines = buildConsolidatedMoveLines(journalEntries);
        moveValues.put("line_ids", lines);

        return moveValues;
    }

    /**
     * Build consolidated move lines from multiple journal entries
     */
    private List<Object> buildConsolidatedMoveLines(List<JournalEntry> journalEntries) {
        List<Object> lines = new ArrayList<>();

        // Group by account and type to consolidate amounts
        Map<String, Map<String, BigDecimal>> accountAmounts = new HashMap<>();

        for (JournalEntry entry : journalEntries) {
            String accountCode = entry.getGlAccount().getGlCode();
            Integer accountId = odooIntegrationService.getOdooAccountId(accountCode);

            if (accountId == null) {
                log.warn("Could not map account {} to Odoo, skipping entry {}", accountCode, entry.getId());
                continue;
            }

            String accountKey = accountId.toString();
            accountAmounts.putIfAbsent(accountKey, new HashMap<>());
            Map<String, BigDecimal> amounts = accountAmounts.get(accountKey);

            if (entry.isDebitEntry()) {
                amounts.merge("debit", entry.getAmount(), BigDecimal::add);
            } else {
                amounts.merge("credit", entry.getAmount(), BigDecimal::add);
            }
        }

        // Create move lines from consolidated amounts
        for (Map.Entry<String, Map<String, BigDecimal>> accountEntry : accountAmounts.entrySet()) {
            Integer accountId = Integer.valueOf(accountEntry.getKey());
            Map<String, BigDecimal> amounts = accountEntry.getValue();

            BigDecimal debitAmount = amounts.getOrDefault("debit", BigDecimal.ZERO);
            BigDecimal creditAmount = amounts.getOrDefault("credit", BigDecimal.ZERO);

            // Net the amounts
            BigDecimal netAmount = debitAmount.subtract(creditAmount);

            if (netAmount.compareTo(BigDecimal.ZERO) != 0) {
                Map<String, Object> line = new HashMap<>();
                line.put("account_id", accountId);

                if (netAmount.compareTo(BigDecimal.ZERO) > 0) {
                    line.put("debit", netAmount);
                    line.put("credit", BigDecimal.ZERO);
                } else {
                    line.put("debit", BigDecimal.ZERO);
                    line.put("credit", netAmount.abs());
                }

                line.put("name", "Haider consolidated entry for account " + accountId);

                // Add line using Odoo's line creation format: (0, 0, values)
                lines.add(Arrays.asList(0, 0, line));
            }
        }

        return lines;
    }

    /**
     * Build narration for loan entries
     */
    private String buildLoanNarration(Long loanId, List<JournalEntry> journalEntries) {
        StringBuilder narration = new StringBuilder();
        narration.append("Haider Loan ID: ").append(loanId);
        narration.append("\nJournal Entries: ");

        journalEntries.forEach(entry -> {
            narration.append("\n- JE ").append(entry.getId());
            if (entry.getDescription() != null && !entry.getDescription().trim().isEmpty()) {
                narration.append(": ").append(entry.getDescription());
            }
            String entryType = entry.isDebitEntry() ? "DEBIT" : "CREDIT";
            narration.append(" (").append(entryType).append(" ").append(entry.getAmount()).append(")");
        });

        return narration.toString();
    }
}
