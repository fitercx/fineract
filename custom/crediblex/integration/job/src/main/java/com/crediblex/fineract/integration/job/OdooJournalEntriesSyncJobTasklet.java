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
package com.crediblex.fineract.integration.job;

import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSync;
import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSyncRepository;
import com.crediblex.fineract.integration.odoo.service.JournalEntryOdooTrackingService;
import com.crediblex.fineract.integration.odoo.service.OdooJournalEntryService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Tasklet for synchronizing journal entries from Fineract to Odoo ERP system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OdooJournalEntriesSyncJobTasklet implements Tasklet {

    private final JournalEntryOdooSyncRepository journalEntryOdooSyncRepository;
    private final JournalEntryOdooTrackingService journalEntryOdooTrackingService;
    private final OdooJournalEntryService odooJournalEntryService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("Starting Odoo Journal Entries Sync Job execution...");

        try {
            // Get all pending journal entries that need to be synced to Odoo
            List<JournalEntryOdooSync> pendingEntries = journalEntryOdooSyncRepository.findPendingEntries();
            log.info("Found {} pending journal entries to sync to Odoo", pendingEntries.size());

            // Group entries by loan ID (entries without loan ID will be processed individually)
            Map<Long, List<JournalEntryOdooSync>> entriesByLoanId = pendingEntries.stream()
                .filter(entry -> entry.getLoanId() != null)
                .collect(Collectors.groupingBy(JournalEntryOdooSync::getLoanId));

            // Get entries without loan ID (savings transactions, manual entries, etc.)
            List<JournalEntryOdooSync> entriesWithoutLoanId = pendingEntries.stream()
                .filter(entry -> entry.getLoanId() == null)
                .collect(Collectors.toList());

            int successCount = 0;
            int failureCount = 0;
            int movesCreated = 0;

            // Process entries grouped by loan ID
            for (Map.Entry<Long, List<JournalEntryOdooSync>> loanGroup : entriesByLoanId.entrySet()) {
                Long loanId = loanGroup.getKey();
                List<JournalEntryOdooSync> loanEntries = loanGroup.getValue();
                
                log.info("Processing {} journal entries for loan ID: {}", loanEntries.size(), loanId);
                
                try {
                    // Post all journal entries for this loan as a single move
                    Long odooMoveId = odooJournalEntryService.postJournalEntriesForLoan(loanId, loanEntries);

                    if (odooMoveId != null) {
                        // Mark all entries in this loan as posted
                        for (JournalEntryOdooSync sync : loanEntries) {
                            journalEntryOdooTrackingService.markAsPosted(sync.getJournalEntry().getId(), odooMoveId);
                            successCount++;
                        }
                        movesCreated++;
                        log.info("Successfully posted {} journal entries for loan {} to Odoo with move ID: {}", 
                                loanEntries.size(), loanId, odooMoveId);
                    } else {
                        String errorMsg = "Failed to create move in Odoo for loan " + loanId;
                        for (JournalEntryOdooSync sync : loanEntries) {
                            journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), errorMsg);
                            failureCount++;
                        }
                        log.error("Failed to post journal entries for loan {} to Odoo", loanId);
                    }

                } catch (Exception e) {
                    log.error("Failed to post journal entries for loan {} to Odoo", loanId, e);
                    for (JournalEntryOdooSync sync : loanEntries) {
                        journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), e.getMessage());
                        failureCount++;
                    }
                }
            }

            // Process entries without loan ID individually i.e. cash margin etc
            for (JournalEntryOdooSync sync : entriesWithoutLoanId) {
                try {
                    // Validate the journal entry before posting
                    if (!odooJournalEntryService.canPostToOdoo(sync.getJournalEntry())) {
                        String errorMsg = "Journal entry validation failed";
                        log.warn("Skipping journal entry {} - {}", sync.getJournalEntry().getId(), errorMsg);
                        journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), errorMsg);
                        failureCount++;
                        continue;
                    }

                    // Post journal entry to Odoo
                    Long odooMoveId = odooJournalEntryService.postJournalEntryToOdoo(sync.getJournalEntry());

                    if (odooMoveId != null) {
                        journalEntryOdooTrackingService.markAsPosted(sync.getJournalEntry().getId(), odooMoveId);
                        log.info("Successfully posted journal entry {} to Odoo with move ID: {}", 
                                sync.getJournalEntry().getId(), odooMoveId);
                        successCount++;
                        movesCreated++;
                    } else {
                        String errorMsg = "Failed to create move in Odoo";
                        log.error("Failed to post journal entry {} to Odoo", sync.getJournalEntry().getId());
                        journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), errorMsg);
                        failureCount++;
                    }

                } catch (Exception e) {
                    log.error("Failed to post journal entry {} to Odoo", sync.getJournalEntry().getId(), e);
                    journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), e.getMessage());
                    failureCount++;
                }
            }

            log.info("Odoo Journal Entries Sync Job completed - Success: {}, Failures: {}, Moves Created: {}", 
                    successCount, failureCount, movesCreated);

        } catch (Exception e) {
            log.error("Error during Odoo Journal Entries Sync Job execution", e);
            throw e;
        }

        log.info("Completed Odoo Journal Entries Sync Job execution");
        return RepeatStatus.FINISHED;
    }
}
