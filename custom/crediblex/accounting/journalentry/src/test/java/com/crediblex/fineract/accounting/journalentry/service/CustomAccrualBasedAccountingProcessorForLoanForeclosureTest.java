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
package com.crediblex.fineract.accounting.journalentry.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.accounting.journalentry.CustomAccountingProcessorHelper;
import com.crediblex.fineract.accounting.journalentry.journalentry.CustomLoanDTO;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.accounting.common.AccountingConstants;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.data.LoanTransactionDTO;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomAccrualBasedAccountingProcessorForLoanForeclosureTest {

    private static final Long LOAN_ID = 42L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long OFFICE_ID = 1L;
    private static final Long DEFERRED_INCOME_GL_ID = 84L;
    private static final String CURRENCY = "AED";
    private static final Long LOAN_TRANSACTION_ID = 928018L;
    private static final String TRANSACTION_ID = "928018";
    private static final String SUFFIX = "-R";
    private static final LocalDate FORECLOSURE_DATE = LocalDate.of(2026, 10, 6);
    private static final BigDecimal INVOICE = new BigDecimal("100000.00");
    private static final BigDecimal SETTLEMENT = new BigDecimal("88668.49");
    private static final BigDecimal REFUND = new BigDecimal("11331.51");
    private static final BigDecimal UNEARNED = new BigDecimal("1331.51");

    private AccountingProcessorHelper helper;
    private CustomAccountingProcessorHelper customHelper;
    private JournalEntryWritePlatformService journalEntryWritePlatformService;
    private LOCAccountingHelper locAccountingHelper;
    private CustomAccrualBasedAccountingProcessorForLoan processor;
    private Office office;
    private GLAccount clearingAccount;
    private GLAccount bankAccount;
    private GLAccount deferredInterestAccount;
    private GLAccount interestReceivableAccount;

    @BeforeEach
    void setUp() throws Exception {
        helper = mock(AccountingProcessorHelper.class);
        customHelper = mock(CustomAccountingProcessorHelper.class);
        journalEntryWritePlatformService = mock(JournalEntryWritePlatformService.class);
        locAccountingHelper = mock(LOCAccountingHelper.class);
        processor = new CustomAccrualBasedAccountingProcessorForLoan(helper, journalEntryWritePlatformService);
        inject("locAccountingHelper", locAccountingHelper);
        inject("customAccountingProcessorHelper", customHelper);

        office = mock(Office.class);
        GLAccount deferredIncomeMapping = mock(GLAccount.class);
        clearingAccount = mock(GLAccount.class);
        bankAccount = mock(GLAccount.class);
        deferredInterestAccount = mock(GLAccount.class);
        interestReceivableAccount = mock(GLAccount.class);
        when(deferredIncomeMapping.getId()).thenReturn(DEFERRED_INCOME_GL_ID);
        when(helper.getOfficeById(OFFICE_ID)).thenReturn(office);
        when(helper.getLinkedGLAccountForLoanProduct(eq(PRODUCT_ID),
                eq(AccountingConstants.AccrualAccountsForLoan.DEFERRED_INCOME.getValue()), any())).thenReturn(deferredIncomeMapping);
        when(helper.getLinkedGLAccountForLoanProduct(eq(PRODUCT_ID),
                eq(AccountingConstants.AccrualAccountsForLoan.INTEREST_RECEIVABLE.getValue()), any()))
                .thenReturn(interestReceivableAccount);
        when(locAccountingHelper.getLOCPayableCreditGLAccount()).thenReturn(clearingAccount);
        when(locAccountingHelper.getReceivableLOCUnearnedInterestRefundGLAccount()).thenReturn(bankAccount);
        when(locAccountingHelper.getReceivableLOCDeferredInterestGLAccount()).thenReturn(deferredInterestAccount);
        when(locAccountingHelper.getInvoiceAmount(LOAN_ID)).thenReturn(INVOICE);
        when(locAccountingHelper.getLoanCreditBalanceForGLAccount(LOAN_ID, DEFERRED_INCOME_GL_ID)).thenReturn(UNEARNED);
        when(locAccountingHelper.isForeclosureAccountTransfer(TRANSACTION_ID)).thenReturn(true);
    }

    @Test
    void receivableForeclosurePostsBalancedRefundGroupUnderSuffixedId() {
        processor.postForeclosureRefundGroups(receivableLoan(repayment(false)));

        verifyPosted(clearingAccount, REFUND, JournalEntryType.DEBIT);
        verifyPosted(bankAccount, REFUND, JournalEntryType.CREDIT);
        verifyPosted(deferredInterestAccount, UNEARNED, JournalEntryType.DEBIT);
        verifyPosted(interestReceivableAccount, UNEARNED, JournalEntryType.CREDIT);
        verify(helper, never()).createDebitJournalEntryForLoan(any(), anyString(), anyLong(), anyString(), any(), any(),
                any(GLAccount.class));
        verify(helper, never()).createCreditJournalEntryForLoan(any(), anyString(), anyLong(), anyString(), any(), any(),
                any(GLAccount.class));
    }

    @Test
    void settlementCoveringInvoiceSkipsRefundPairOnly() {
        when(locAccountingHelper.getInvoiceAmount(LOAN_ID)).thenReturn(SETTLEMENT);

        processor.postForeclosureRefundGroups(receivableLoan(repayment(false)));

        verifyNotPosted(clearingAccount);
        verifyNotPosted(bankAccount);
        verifyPosted(deferredInterestAccount, UNEARNED, JournalEntryType.DEBIT);
        verifyPosted(interestReceivableAccount, UNEARNED, JournalEntryType.CREDIT);
    }

    @Test
    void noDeferredBalanceSkipsUnearnedPairOnly() {
        when(locAccountingHelper.getLoanCreditBalanceForGLAccount(LOAN_ID, DEFERRED_INCOME_GL_ID)).thenReturn(BigDecimal.ZERO);

        processor.postForeclosureRefundGroups(receivableLoan(repayment(false)));

        verifyPosted(clearingAccount, REFUND, JournalEntryType.DEBIT);
        verifyPosted(bankAccount, REFUND, JournalEntryType.CREDIT);
        verifyNotPosted(deferredInterestAccount);
        verifyNotPosted(interestReceivableAccount);
    }

    @Test
    void receivableNormalRepaymentPostsNothing() {
        when(locAccountingHelper.isForeclosureAccountTransfer(TRANSACTION_ID)).thenReturn(false);

        processor.postForeclosureRefundGroups(receivableLoan(repayment(false)));

        verifyNothingPosted();
    }

    @Test
    void nonReceivableForeclosurePostsNothing() {
        CustomLoanDTO loan = receivableLoan(repayment(false));
        loan.setLocReceivable(false);

        processor.postForeclosureRefundGroups(loan);

        verifyNothingPosted();
    }

    @Test
    void missingInvoiceAmountFailsForeclosure() {
        when(locAccountingHelper.getInvoiceAmount(LOAN_ID)).thenReturn(null);

        CustomLoanDTO loan = receivableLoan(repayment(false));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> processor.postForeclosureRefundGroups(loan));
        verifyNothingPosted();
    }

    @Test
    void missingDeferredInterestGlFailsForeclosure() {
        when(locAccountingHelper.getReceivableLOCDeferredInterestGLAccount()).thenReturn(null);

        CustomLoanDTO loan = receivableLoan(repayment(false));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> processor.postForeclosureRefundGroups(loan));
        verifyNothingPosted();
    }

    @Test
    void missingBankGlFailsForeclosure() {
        when(locAccountingHelper.getReceivableLOCUnearnedInterestRefundGLAccount()).thenReturn(null);

        CustomLoanDTO loan = receivableLoan(repayment(false));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> processor.postForeclosureRefundGroups(loan));
        verifyNothingPosted();
    }

    @Test
    void reversedRepaymentReversesRefundGroup() {
        processor.postForeclosureRefundGroups(receivableLoan(repayment(true)));

        verify(journalEntryWritePlatformService).createJournalEntryForReversedLoanTransaction(FORECLOSURE_DATE, TRANSACTION_ID + SUFFIX,
                OFFICE_ID);
        verifyNothingPosted();
    }

    @Test
    void reversedRepaymentOnNonReceivableLoanIsLeftToCore() {
        CustomLoanDTO loan = receivableLoan(repayment(true));
        loan.setLocReceivable(false);

        processor.postForeclosureRefundGroups(loan);

        verify(journalEntryWritePlatformService, never()).createJournalEntryForReversedLoanTransaction(any(), anyString(), anyLong());
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = CustomAccrualBasedAccountingProcessorForLoan.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(processor, value);
    }

    private void verifyPosted(GLAccount account, BigDecimal amount, JournalEntryType type) {
        verify(customHelper).createLoanJournalEntryUnderSuffixedId(office, CURRENCY, account, LOAN_ID, LOAN_TRANSACTION_ID, SUFFIX,
                FORECLOSURE_DATE, amount, type);
    }

    private void verifyNotPosted(GLAccount account) {
        verify(customHelper, never()).createLoanJournalEntryUnderSuffixedId(any(), anyString(), eq(account), anyLong(), anyLong(),
                anyString(), any(), any(), any());
    }

    private void verifyNothingPosted() {
        verify(customHelper, never()).createLoanJournalEntryUnderSuffixedId(any(), anyString(), any(), anyLong(), anyLong(), anyString(),
                any(), any(), any());
    }

    private static LoanTransactionDTO repayment(boolean reversed) {
        LoanTransactionEnumData type = mock(LoanTransactionEnumData.class);
        when(type.isRepayment()).thenReturn(true);
        LoanTransactionDTO transaction = mock(LoanTransactionDTO.class);
        when(transaction.getTransactionType()).thenReturn(type);
        when(transaction.getTransactionId()).thenReturn(TRANSACTION_ID);
        when(transaction.getTransactionDate()).thenReturn(FORECLOSURE_DATE);
        when(transaction.getAmount()).thenReturn(SETTLEMENT);
        when(transaction.isReversed()).thenReturn(reversed);
        return transaction;
    }

    private static CustomLoanDTO receivableLoan(LoanTransactionDTO transaction) {
        CustomLoanDTO loan = new CustomLoanDTO(LOAN_ID, PRODUCT_ID, OFFICE_ID, CURRENCY, false, false, true, List.of(transaction), false,
                false, null, null);
        loan.setLocReceivable(true);
        return loan;
    }
}
