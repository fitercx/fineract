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

import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSync;
import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSyncRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.infrastructure.event.business.domain.journalentry.LoanJournalEntryCreatedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "odoo.enabled", havingValue = "true")
public class JournalEntryOdooTrackingService {

    private final BusinessEventNotifierService businessEventNotifierService;
    private final JournalEntryOdooSyncRepository journalEntryOdooSyncRepository;
    private final LoanTransactionRepository loanTransactionRepository;

    @PostConstruct
    public void addListeners() {
        log.info("Initializing Journal Entry Odoo tracking listeners");
        
        businessEventNotifierService.addPostBusinessEventListener(LoanJournalEntryCreatedBusinessEvent.class, event -> {
            try {
                JournalEntry journalEntry = event.get();
                createTrackingRecord(journalEntry);
            } catch (Exception e) {
                log.error("Error creating journal entry tracking record", e);
            }
        });
    }

    @Transactional
    public void createTrackingRecord(JournalEntry journalEntry) {
        log.debug("Creating tracking record for journal entry ID: {}", journalEntry.getId());
        
        // Check if tracking record already exists
        if (journalEntryOdooSyncRepository.findByJournalEntryId(journalEntry.getId()).isEmpty()) {
            // Get loan ID from loan transaction if available
            Long loanId = null;
            if (journalEntry.getLoanTransactionId() != null) {
                loanId = getLoanIdFromTransactionId(journalEntry.getLoanTransactionId());
            }
            
            JournalEntryOdooSync trackingRecord = new JournalEntryOdooSync(journalEntry, loanId);
            journalEntryOdooSyncRepository.save(trackingRecord);
            
            log.info("Created Odoo sync tracking record for journal entry ID: {} with loan ID: {}", 
                journalEntry.getId(), loanId);
        } else {
            log.debug("Tracking record already exists for journal entry ID: {}", journalEntry.getId());
        }
    }

    /**
     * Get loan ID from loan transaction ID
     */
    private Long getLoanIdFromTransactionId(Long loanTransactionId) {
        if (loanTransactionId == null) {
            return null;
        }
        
        try {
            LoanTransaction loanTransaction = loanTransactionRepository.findById(loanTransactionId).orElse(null);
            if (loanTransaction != null && loanTransaction.getLoan() != null) {
                return loanTransaction.getLoan().getId();
            }
        } catch (Exception e) {
            log.warn("Failed to get loan ID for transaction ID: {}", loanTransactionId, e);
        }
        
        return null;
    }

    @Transactional
    public void markAsPosted(Long journalEntryId, Long odooMoveId) {
        journalEntryOdooSyncRepository.findByJournalEntryId(journalEntryId).ifPresent(sync -> {
            sync.markAsPosted(odooMoveId);
            journalEntryOdooSyncRepository.save(sync);
            log.info("Marked journal entry {} as posted to Odoo with move ID: {}", journalEntryId, odooMoveId);
        });
    }

    @Transactional
    public void markAsFailed(Long journalEntryId, String errorMessage) {
        journalEntryOdooSyncRepository.findByJournalEntryId(journalEntryId).ifPresent(sync -> {
            sync.markAsFailed(errorMessage);
            journalEntryOdooSyncRepository.save(sync);
            log.warn("Marked journal entry {} as failed to post to Odoo: {}", journalEntryId, errorMessage);
        });
    }
}
