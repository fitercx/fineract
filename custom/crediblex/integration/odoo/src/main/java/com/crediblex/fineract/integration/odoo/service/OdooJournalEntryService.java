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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    /**
     * Post a Fineract journal entry to Odoo which are not tied to a loan i.e. cash margin journal entries
     */
    public Long postJournalEntryToOdoo(JournalEntry fineractEntry) {
        try {
            // Authenticate with Odoo
            Integer uid = odooApiClient.authenticate();
            if (uid == null) {
                throw new RuntimeException("Odoo authentication failed - check credentials and connection settings");
            }

            // Get journal ID based on GL account code, transaction type and debit flag
            String fineractAccountCode = fineractEntry.getGlAccount().getGlCode();

            boolean isDebit = fineractEntry.isDebitEntry();

            Integer journalId = odooIntegrationService.getJournalIdForGlCode(fineractAccountCode, null, isDebit);
            if (journalId == null) {
                log.info("Skipping journal entry {} with GL code {}, business event type {} and isDebit {} - no journal mapping found",
                        fineractEntry.getId(), fineractAccountCode, null, isDebit);
                return null; // Skip instead of throwing error
            }

            // Get account mapping
            Integer odooAccountId = odooIntegrationService.getOdooAccountId(fineractAccountCode);
            if (odooAccountId == null) {
                throw new RuntimeException(String.format(
                        "Could not map Fineract GL account '%s' to Odoo account - account may not exist in Odoo chart of accounts",
                        fineractAccountCode));
            }

            // Create the account move
            Map<String, Object> moveValues = buildAccountMoveValues(fineractEntry, journalId, odooAccountId);

            Long odooMoveId = odooApiClient.create(uid, "account.move", moveValues);
            if (odooMoveId == null) {
                throw new RuntimeException(
                        String.format("Failed to create account move in Odoo for journal entry %d - check move data and Odoo permissions",
                                fineractEntry.getId()));
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
            throw new RuntimeException(
                    String.format("Unexpected error posting journal entry %d to Odoo: %s", fineractEntry.getId(), e.getMessage()), e);
        }
    }

    /**
     * Build the account move values for Odoo - unified method for single and multiple journal entries
     */
    private Map<String, Object> buildAccountMoveValues(List<JournalEntry> journalEntries, Integer journalId, Long loanId,
            boolean consolidateLines) {
        Map<String, Object> moveValues = new HashMap<>();

        if (journalEntries.isEmpty()) {
            throw new IllegalArgumentException("Journal entries list cannot be empty");
        }

        // Use the first entry's date as move date
        JournalEntry firstEntry = journalEntries.get(0);
        moveValues.put("journal_id", journalId);
        moveValues.put("date", firstEntry.getTransactionDate().format(DateTimeFormatter.ISO_LOCAL_DATE));

        // Build reference based on whether it's a single entry or multiple entries
        if (journalEntries.size() == 1) {
            moveValues.put("ref", "Haider JE " + firstEntry.getId());
            moveValues.put("narration", buildNarration(firstEntry));
        } else {
            moveValues.put("ref", "Haider Loan " + loanId + " - JEs: "
                    + journalEntries.stream().map(je -> je.getId().toString()).collect(Collectors.joining(", ")));
            moveValues.put("narration", buildLoanNarration(loanId, journalEntries));
        }

        // Add loan_id if available
        if (loanId != null) {
            moveValues.put("x_studio_loan_id_new", loanId);
            // String clientName = getClientNameFromLoanId(loanId);
            // if (clientName != null) {
            // moveValues.put("x_sme_id", clientName);
            // }
        } else if (firstEntry.getLoanTransactionId() != null) {
            // Extract loan ID from the journal entry if not provided
            Long extractedLoanId = getLoanIdFromTransactionId(firstEntry.getLoanTransactionId());
            if (extractedLoanId != null) {
                moveValues.put("x_studio_loan_id_new", extractedLoanId);
            }
        }

        // Build line items based on consolidation preference
        List<Object> lines;
        if (consolidateLines && journalEntries.size() > 1) {
            lines = buildConsolidatedMoveLines(journalEntries);
        } else if (journalEntries.size() == 1) {
            // For single entries, we need to get the account ID
            String accountCode = firstEntry.getGlAccount().getGlCode();
            Integer accountId = odooIntegrationService.getOdooAccountId(accountCode);
            if (accountId == null) {
                throw new RuntimeException(String.format("Could not map Fineract GL account '%s' to Odoo account", accountCode));
            }
            lines = buildMoveLines(firstEntry, accountId);
        } else {
            // Multiple entries but no consolidation - treat each separately
            lines = buildConsolidatedMoveLines(journalEntries);
        }

        moveValues.put("line_ids", lines);
        return moveValues;
    }

    /**
     * Build account move values for a single journal entry
     */
    private Map<String, Object> buildAccountMoveValues(JournalEntry fineractEntry, Integer journalId, Integer accountId) {
        return buildAccountMoveValues(List.of(fineractEntry), journalId, null, false);
    }

    /**
     * Build account move values for multiple journal entries of a loan
     */
    private Map<String, Object> buildAccountMoveValuesForLoan(Long loanId, List<JournalEntry> journalEntries, Integer journalId) {
        return buildAccountMoveValues(journalEntries, journalId, loanId, true);
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
     * Post multiple journal entries for a loan as grouped account moves in Odoo Groups entries by their target journal
     * based on GL codes
     */
    public Map<Integer, Long> postJournalEntriesForLoan(Long loanId, List<JournalEntryOdooSync> journalEntryOdooSyncs) {
        Map<Integer, Long> journalToMoveMap = new HashMap<>();

        try {
            // Authenticate with Odoo
            Integer uid = odooApiClient.authenticate();
            if (uid == null) {
                throw new RuntimeException(
                        "Odoo authentication failed for loan " + loanId + " - check credentials and connection settings");
            }

            // Group journal entries by target journal
            List<JournalEntry> journalEntries = journalEntryOdooSyncs.stream().map(JournalEntryOdooSync::getJournalEntry).toList();

            // Validate all entries
            for (JournalEntry entry : journalEntries) {
                if (!canPostToOdoo(entry)) {
                    throw new RuntimeException(
                            String.format("Journal entry %d for loan %d failed validation - check GL account, amount, or transaction date",
                                    entry.getId(), loanId));
                }
            }

            // Group entries by journal using business event type from tracking records
            Map<Integer, List<JournalEntry>> entriesByJournal = groupEntriesByJournal(journalEntryOdooSyncs);

            if (entriesByJournal.isEmpty()) {
                log.info("No journal entries with valid mappings found for loan {} - all entries skipped", loanId);
                return journalToMoveMap; // Return empty map
            }

            // Create separate account moves for each journal
            for (Map.Entry<Integer, List<JournalEntry>> journalGroup : entriesByJournal.entrySet()) {
                Integer journalId = journalGroup.getKey();
                List<JournalEntry> entries = journalGroup.getValue();

                // Create the account move with multiple lines for this journal
                Map<String, Object> moveValues = buildAccountMoveValuesForLoan(loanId, entries, journalId);

                Long odooMoveId = odooApiClient.create(uid, "account.move", moveValues);
                if (odooMoveId == null) {
                    throw new RuntimeException(String.format(
                            "Failed to create account move in Odoo for loan %d and journal %d - check move data and Odoo permissions",
                            loanId, journalId));
                }

                // Post the move (from draft to posted state)
                Boolean posted = odooApiClient.postAccountMove(uid, odooMoveId);
                if (!posted) {
                    log.warn("Created move {} in Odoo but failed to post it - move remains in draft state", odooMoveId);
                    // Continue anyway as the move was created
                }

                journalToMoveMap.put(journalId, odooMoveId);
                log.info("Successfully posted {} journal entries for loan {} to Odoo journal {} as move {}", entries.size(), loanId,
                        journalId, odooMoveId);
            }

            // Log summary of skipped entries
            int totalEntries = journalEntries.size();
            int processedEntries = entriesByJournal.values().stream().mapToInt(List::size).sum();
            int skippedEntries = totalEntries - processedEntries;

            if (skippedEntries > 0) {
                log.info("Processed {} entries, skipped {} entries without journal mappings for loan {}", processedEntries, skippedEntries,
                        loanId);
            }

            return journalToMoveMap;

        } catch (RuntimeException e) {
            // Re-throw our specific exceptions
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while posting journal entries for loan {} to Odoo", loanId, e);
            throw new RuntimeException(
                    String.format("Unexpected error posting journal entries for loan %d to Odoo: %s", loanId, e.getMessage()), e);
        }
    }

    /**
     * Group journal entries by their target journal based on GL codes, business event type and debit flag
     */
    private Map<Integer, List<JournalEntry>> groupEntriesByJournal(List<JournalEntryOdooSync> journalEntryOdooSyncs) {
        Map<Integer, List<JournalEntry>> groupedEntries = new HashMap<>();

        for (JournalEntryOdooSync sync : journalEntryOdooSyncs) {
            JournalEntry entry = sync.getJournalEntry();
            String glCode = entry.getGlAccount().getGlCode();

            // Use the business event type from the tracking record
            String businessEventType = sync.getBusinessEventType();
            boolean isDebit = entry.isDebitEntry();

            Integer journalId = odooIntegrationService.getJournalIdForGlCode(glCode, businessEventType, isDebit);

            if (journalId != null) {
                groupedEntries.computeIfAbsent(journalId, k -> new ArrayList<>()).add(entry);
                log.debug("Assigned journal entry {} (GL: {}, business event type: {}, isDebit: {}) to journal {}", entry.getId(), glCode,
                        businessEventType, isDebit, journalId);
            } else {
                log.info("Skipping journal entry {} with GL code {}, business event type {} and isDebit {} - no journal mapping found",
                        entry.getId(), glCode, businessEventType, isDebit);
            }
        }

        return groupedEntries;
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

    /**
     * Get loan ID from loan transaction ID
     */
    private Long getLoanIdFromTransactionId(Long loanTransactionId) {
        if (loanTransactionId == null) {
            return null;
        }

        try {
            String sql = "SELECT loan_id FROM m_loan_transaction WHERE id = ?";
            List<Long> loanIds = jdbcTemplate.queryForList(sql, Long.class, loanTransactionId);
            return loanIds.isEmpty() ? null : loanIds.get(0);
        } catch (Exception e) {
            log.warn("Failed to get loan ID for transaction ID: {}", loanTransactionId, e);
            return null;
        }
    }

    /**
     * Get client name from loan ID
     */
    private String getClientNameFromLoanId(Long loanId) {
        if (loanId == null) {
            return null;
        }

        try {
            String sql = "SELECT c.display_name FROM m_client c " + "JOIN m_loan l ON l.client_id = c.id " + "WHERE l.id = ?";
            List<String> clientNames = jdbcTemplate.queryForList(sql, String.class, loanId);
            return clientNames.isEmpty() ? null : clientNames.get(0);
        } catch (Exception e) {
            log.warn("Failed to get client name for loan ID: {}", loanId, e);
            return null;
        }
    }

    /**
     * Post accrual journal entries directly to Odoo without creating Fineract journal entry records. Creates debit and
     * credit journal lines on-the-fly and posts them to Odoo.
     */
    public Long postAccrualJournalEntriesToOdoo(Long loanId, String transactionId, java.time.LocalDate transactionDate, String description,
            BigDecimal accrualAmount, String creditGLCode, String debitGLCode, String OdooJournalCode) {

        try {
            // Authenticate with Odoo
            Integer uid = odooApiClient.authenticate();
            if (uid == null) {
                throw new RuntimeException("Odoo authentication failed - check credentials and connection settings");
            }

            // Get journal ID for accrual entries (you might want to configure this or derive it from business logic)
            // For now, we'll use the debit GL code to determine the journal
            Integer journalId = odooIntegrationService.getJournalIdByOdooCode(OdooJournalCode);
            if (journalId == null) {
                throw new RuntimeException("No journal mapping found for accrual entries with GL code: " + debitGLCode);
            }

            // Get Odoo account IDs for both GL codes
            Integer creditAccountId = odooIntegrationService.getOdooAccountId(creditGLCode);
            if (creditAccountId == null) {
                throw new RuntimeException("Could not map GL account " + creditGLCode + " to Odoo account");
            }

            Integer debitAccountId = odooIntegrationService.getOdooAccountId(debitGLCode);
            if (debitAccountId == null) {
                throw new RuntimeException("Could not map GL account " + debitGLCode + " to Odoo account");
            }

            // Build account move values
            Map<String, Object> moveValues = buildAccrualMoveValues(loanId, transactionId, transactionDate, description, accrualAmount,
                    journalId, creditAccountId, debitAccountId);

            // Create the move in Odoo
            Long odooMoveId = odooApiClient.create(uid, "account.move", moveValues);
            if (odooMoveId == null) {
                throw new RuntimeException("Failed to create accrual move in Odoo for loan " + loanId);
            }

            // Post the move (from draft to posted state)
            Boolean posted = odooApiClient.postAccountMove(uid, odooMoveId);
            if (!posted) {
                log.warn("Created accrual move {} in Odoo but failed to post it - move remains in draft state", odooMoveId);
            }

            log.info("Successfully posted accrual journal entries to Odoo for loan {} as move {} with amount {}", loanId, odooMoveId,
                    accrualAmount);

            return odooMoveId;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while posting accrual entries for loan {} to Odoo", loanId, e);
            throw new RuntimeException("Unexpected error posting accrual entries to Odoo: " + e.getMessage(), e);
        }
    }

    /**
     * Build account move values for accrual journal entries
     */
    private Map<String, Object> buildAccrualMoveValues(Long loanId, String transactionId, java.time.LocalDate transactionDate,
            String description, BigDecimal accrualAmount, Integer journalId, Integer creditAccountId, Integer debitAccountId) {

        Map<String, Object> moveValues = new HashMap<>();
        moveValues.put("journal_id", journalId);
        moveValues.put("date", transactionDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        moveValues.put("ref", "Accrual " + transactionId);
        moveValues.put("narration", description);
        moveValues.put("x_studio_loan_id_new", loanId);

        // Create journal lines
        List<Object> lines = new ArrayList<>();

        // Credit line (Interest Income)
        Map<String, Object> creditLine = new HashMap<>();
        creditLine.put("account_id", creditAccountId);
        creditLine.put("debit", BigDecimal.ZERO);
        creditLine.put("credit", accrualAmount);
        creditLine.put("name", description + " - Interest Income");

        // Debit line (Interest Receivable)
        Map<String, Object> debitLine = new HashMap<>();
        debitLine.put("account_id", debitAccountId);
        debitLine.put("debit", accrualAmount);
        debitLine.put("credit", BigDecimal.ZERO);
        debitLine.put("name", description + " - Interest Receivable");

        // Add lines using Odoo's line creation format: (0, 0, values)
        lines.add(Arrays.asList(0, 0, creditLine));
        lines.add(Arrays.asList(0, 0, debitLine));

        moveValues.put("line_ids", lines);
        return moveValues;
    }
}
