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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.crediblex.fineract.integration.odoo.data.ExtendedSavingsAccountSummaryData;
import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSync;
import com.crediblex.fineract.integration.odoo.domain.JournalEntryOdooSyncRepository;
import com.crediblex.fineract.integration.odoo.event.LoanJournalEntryCreatedBusinessEvent;
import com.crediblex.fineract.integration.odoo.event.SavingsJournalEntryCreatedBusinessEvent;
import com.crediblex.fineract.portfolio.client.service.CredXAccountDetailsReadPlatformServiceJpaRepositoryImpl;

@ExtendWith(MockitoExtension.class)
class JournalEntryOdooTrackingServiceTest {

    @Mock
    private JournalEntryOdooSyncRepository journalEntryOdooSyncRepository;

    @Mock
    private CredXAccountDetailsReadPlatformServiceJpaRepositoryImpl credXAccountDetailsService;

    @InjectMocks
    private JournalEntryOdooTrackingService trackingService;

    private JournalEntry mockJournalEntry;
    private Loan mockLoan;
    private SavingsAccount mockSavingsAccount;

    @BeforeEach
    void setUp() {
        mockJournalEntry = mock(JournalEntry.class);
        mockLoan = mock(Loan.class);
        mockSavingsAccount = mock(SavingsAccount.class);
        
        when(mockJournalEntry.getId()).thenReturn(1L);
    }

    @Test
    void testHandleLoanJournalEntryEvent() {
        // Given
        when(mockLoan.getId()).thenReturn(100L);
        LoanJournalEntryCreatedBusinessEvent event = new LoanJournalEntryCreatedBusinessEvent(mockJournalEntry, mockLoan);

        // When
        trackingService.handleLoanJournalEntryEvent(event);

        // Then
        verify(journalEntryOdooSyncRepository).save(argThat(sync -> 
            sync.getJournalEntryId().equals(1L) &&
            sync.getLoanId().equals(100L) &&
            !sync.isSyncedToOdoo()
        ));
    }

    @Test
    void testHandleSavingsJournalEntryEventWithLinkedLoan() {
        // Given
        when(mockSavingsAccount.getId()).thenReturn(200L);
        
        ExtendedSavingsAccountSummaryData savingsData = new ExtendedSavingsAccountSummaryData();
        savingsData.getAdditionalProperties().put("linkedLoanAccountId", 150L);
        
        when(credXAccountDetailsService.getSavingsAccountDetails(200L))
            .thenReturn(List.of(savingsData));
        
        SavingsJournalEntryCreatedBusinessEvent event = new SavingsJournalEntryCreatedBusinessEvent(mockJournalEntry, mockSavingsAccount);

        // When
        trackingService.handleSavingsJournalEntryEvent(event);

        // Then
        verify(journalEntryOdooSyncRepository).save(argThat(sync -> 
            sync.getJournalEntryId().equals(1L) &&
            sync.getLoanId().equals(150L) &&
            !sync.isSyncedToOdoo()
        ));
    }

    @Test
    void testHandleSavingsJournalEntryEventWithoutLinkedLoan() {
        // Given
        when(mockSavingsAccount.getId()).thenReturn(300L);
        
        ExtendedSavingsAccountSummaryData savingsData = new ExtendedSavingsAccountSummaryData();
        // No linked loan
        
        when(credXAccountDetailsService.getSavingsAccountDetails(300L))
            .thenReturn(List.of(savingsData));
        
        SavingsJournalEntryCreatedBusinessEvent event = new SavingsJournalEntryCreatedBusinessEvent(mockJournalEntry, mockSavingsAccount);

        // When
        trackingService.handleSavingsJournalEntryEvent(event);

        // Then
        verify(journalEntryOdooSyncRepository).save(argThat(sync -> 
            sync.getJournalEntryId().equals(1L) &&
            sync.getLoanId() == null &&
            !sync.isSyncedToOdoo()
        ));
    }

    @Test
    void testGetLoanIdFromSavingsTransactionIdMultipleResults() {
        // Given
        Long savingsAccountId = 400L;
        
        ExtendedSavingsAccountSummaryData data1 = new ExtendedSavingsAccountSummaryData();
        data1.setAccountId(400L);
        data1.getAdditionalProperties().put("linkedLoanAccountId", 100L);
        
        ExtendedSavingsAccountSummaryData data2 = new ExtendedSavingsAccountSummaryData();
        data2.setAccountId(500L); // Different ID
        data2.getAdditionalProperties().put("linkedLoanAccountId", 200L);
        
        when(credXAccountDetailsService.getSavingsAccountDetails(savingsAccountId))
            .thenReturn(List.of(data1, data2));

        // When
        Optional<Long> result = trackingService.getLoanIdFromSavingsTransactionId(savingsAccountId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(100L, result.get());
    }

    @Test
    void testGetLoanIdFromSavingsTransactionIdNoMatchingAccount() {
        // Given
        Long savingsAccountId = 500L;
        
        ExtendedSavingsAccountSummaryData data = new ExtendedSavingsAccountSummaryData();
        data.setAccountId(600L); // Different ID, no match
        data.getAdditionalProperties().put("linkedLoanAccountId", 100L);
        
        when(credXAccountDetailsService.getSavingsAccountDetails(savingsAccountId))
            .thenReturn(List.of(data));

        // When
        Optional<Long> result = trackingService.getLoanIdFromSavingsTransactionId(savingsAccountId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testGetLoanIdFromSavingsTransactionIdEmptyResults() {
        // Given
        Long savingsAccountId = 600L;
        
        when(credXAccountDetailsService.getSavingsAccountDetails(savingsAccountId))
            .thenReturn(List.of());

        // When
        Optional<Long> result = trackingService.getLoanIdFromSavingsTransactionId(savingsAccountId);

        // Then
        assertFalse(result.isPresent());
    }
}
