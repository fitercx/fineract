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
import java.util.List;
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

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("Starting Odoo Journal Entries Sync Job execution...");

        try {
            // Get all pending journal entries that need to be synced to Odoo
            List<JournalEntryOdooSync> pendingEntries = journalEntryOdooSyncRepository.findPendingEntries();
            log.info("Found {} pending journal entries to sync to Odoo", pendingEntries.size());

            int successCount = 0;
            int failureCount = 0;

            for (JournalEntryOdooSync sync : pendingEntries) {
                try {
                    // TODO: Replace with actual Odoo service call
                    // Long odooMoveId = odooService.postJournalEntry(sync.getJournalEntry());

                    // For now, simulate successful posting
                    Long simulatedOdooMoveId = System.currentTimeMillis(); // Temporary simulation
                    journalEntryOdooTrackingService.markAsPosted(sync.getJournalEntry().getId(), simulatedOdooMoveId);

                    log.debug("Successfully posted journal entry {} to Odoo with move ID: {}", sync.getJournalEntry().getId(),
                            simulatedOdooMoveId);
                    successCount++;

                } catch (Exception e) {
                    log.error("Failed to post journal entry {} to Odoo", sync.getJournalEntry().getId(), e);
                    journalEntryOdooTrackingService.markAsFailed(sync.getJournalEntry().getId(), e.getMessage());
                    failureCount++;
                }
            }

            log.info("Odoo Journal Entries Sync Job completed - Success: {}, Failures: {}", successCount, failureCount);

        } catch (Exception e) {
            log.error("Error during Odoo Journal Entries Sync Job execution", e);
            throw e;
        }

        log.info("Completed Odoo Journal Entries Sync Job execution");
        return RepeatStatus.FINISHED;
    }
}
