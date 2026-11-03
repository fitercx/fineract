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
package com.crediblex.fineract.accounting.journalentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.event.business.domain.journalentry.LoanJournalEntryCreatedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.portfolio.PortfolioProductType;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomAccountingProcessorHelperSuffixedEntryTest {

    @BeforeEach
    void setUp() {
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 6));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "default", "UTC", null));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void suffixedEntryKeepsLoanTransactionLinkAndNotifiesTracking() {
        JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
        BusinessEventNotifierService businessEventNotifierService = mock(BusinessEventNotifierService.class);
        when(journalEntryRepository.saveAndFlush(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomAccountingProcessorHelper helper = new CustomAccountingProcessorHelper(journalEntryRepository,
                mock(ProductToGLAccountMappingRepository.class), mock(FinancialActivityAccountRepositoryWrapper.class),
                mock(GLClosureRepository.class), mock(GLAccountRepository.class), mock(OfficeRepository.class),
                mock(AccountTransfersReadPlatformService.class), mock(ChargeRepositoryWrapper.class), businessEventNotifierService);
        Office office = mock(Office.class);
        GLAccount account = mock(GLAccount.class);
        LocalDate date = LocalDate.of(2026, 10, 6);

        helper.createLoanJournalEntryUnderSuffixedId(office, "AED", account, 42L, 928018L, "-R", date, new BigDecimal("11331.51"),
                JournalEntryType.CREDIT);

        ArgumentCaptor<JournalEntry> captor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository).saveAndFlush(captor.capture());
        JournalEntry entry = captor.getValue();
        assertEquals("L928018-R", entry.getTransactionId());
        assertEquals(928018L, entry.getLoanTransactionId());
        assertEquals(42L, entry.getEntityId());
        assertEquals(PortfolioProductType.LOAN.getValue(), entry.getEntityType());
        assertEquals(JournalEntryType.CREDIT.getValue(), entry.getType());
        assertEquals(new BigDecimal("11331.51"), entry.getAmount());
        assertSame(account, entry.getGlAccount());
        assertEquals(date, entry.getTransactionDate());
        verify(businessEventNotifierService).notifyPostBusinessEvent(any(LoanJournalEntryCreatedBusinessEvent.class));
    }
}
