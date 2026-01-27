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
package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.infrastructure.events.business.domain.accounttransfer.SavingsToLoanAccountTransferBusinessEvent;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditSummary;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionType;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditBalanceUpdateService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetails;
import org.apache.fineract.portfolio.account.domain.AccountTransferTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tests for SavingsToLoanTransferBusinessEventListener.
 *
 * Note: Since the event listener is a private inner class, we use reflection to access it for testing. In a production
 * scenario, integration tests would be preferred to test the full event flow.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SavingsToLoanTransferBusinessEventListener Tests")
class SavingsToLoanTransferBusinessEventListenerTest {

    @Mock
    private LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

    @Mock
    private LineOfCreditBalanceUpdateService lineOfCreditBalanceUpdateService;

    private Object eventListener;
    private SavingsToLoanAccountTransferBusinessEvent event;
    private AccountTransferDetails accountTransferDetails;
    private Loan loan;
    private SavingsAccount savingsAccount;
    private LineOfCredit lineOfCredit;
    private LoanTransaction loanTransaction;
    private AccountTransferTransaction accountTransferTransaction;
    private LoanLineOfCreditParams locParams;

    @BeforeEach
    void setUp() throws Exception {
        // Create mocks
        loan = mock(Loan.class);
        when(loan.getId()).thenReturn(100L);

        savingsAccount = mock(SavingsAccount.class);
        when(savingsAccount.getId()).thenReturn(200L);

        lineOfCredit = createLineOfCredit(1000L, new BigDecimal("10000.00"), new BigDecimal("3000.00"), new BigDecimal("7000.00"));

        locParams = mock(LoanLineOfCreditParams.class);
        when(locParams.getLineOfCredit()).thenReturn(lineOfCredit);

        loanTransaction = mock(LoanTransaction.class);
        when(loanTransaction.getId()).thenReturn(300L);
        when(loanTransaction.getTransactionDate()).thenReturn(LocalDate.of(2025, 1, 15));
        when(loanTransaction.getAmount()).thenReturn(new BigDecimal("500.00"));
        when(loanTransaction.getPrincipalPortion()).thenReturn(new BigDecimal("400.00"));

        accountTransferTransaction = mock(AccountTransferTransaction.class);
        when(accountTransferTransaction.getToLoanTransaction()).thenReturn(loanTransaction);

        accountTransferDetails = mock(AccountTransferDetails.class);
        when(accountTransferDetails.fromSavingsAccount()).thenReturn(savingsAccount);
        when(accountTransferDetails.toLoanAccount()).thenReturn(loan);
        when(accountTransferDetails.getAccountTransferTransactions()).thenReturn(List.of(accountTransferTransaction));

        event = mock(SavingsToLoanAccountTransferBusinessEvent.class);
        when(event.get()).thenReturn(accountTransferDetails);

        // Create event listener using reflection
        createEventListener();
    }

    private void createEventListener() throws Exception {
        // Create a minimal service instance to hold the listener
        CustomLoanWritePlatformServiceJpaRepositoryImpl service = mock(CustomLoanWritePlatformServiceJpaRepositoryImpl.class);

        // Use reflection to access the private inner class
        Class<?> serviceClass = CustomLoanWritePlatformServiceJpaRepositoryImpl.class;
        Class<?>[] innerClasses = serviceClass.getDeclaredClasses();
        Class<?> listenerClass = null;
        for (Class<?> innerClass : innerClasses) {
            if (innerClass.getSimpleName().equals("SavingsToLoanTransferBusinessEventListener")) {
                listenerClass = innerClass;
                break;
            }
        }

        if (listenerClass != null) {
            // Create instance using reflection
            Constructor<?> constructor = listenerClass.getDeclaredConstructor(serviceClass);
            constructor.setAccessible(true);
            eventListener = constructor.newInstance(service);

            // Inject dependencies using reflection
            Field repoField = serviceClass.getDeclaredField("loanLineOfCreditParamsRepository");
            repoField.setAccessible(true);
            repoField.set(service, loanLineOfCreditParamsRepository);

            Field locServiceField = serviceClass.getDeclaredField("lineOfCreditBalanceUpdateService");
            locServiceField.setAccessible(true);
            locServiceField.set(service, lineOfCreditBalanceUpdateService);
        }
    }

    private void invokeOnBusinessEvent() throws Exception {
        Method onBusinessEvent = eventListener.getClass().getDeclaredMethod("onBusinessEvent",
                SavingsToLoanAccountTransferBusinessEvent.class);
        onBusinessEvent.setAccessible(true);
        onBusinessEvent.invoke(eventListener, event);
    }

    @Test
    @DisplayName("Should update LOC balance for receivable LOC using full transaction amount")
    void testReceivableLoc_UseFullTransactionAmount() throws Exception {
        // Given: Receivable LOC
        when(lineOfCredit.getProductType()).thenReturn(LocProductType.RECEIVABLE);
        when(loanLineOfCreditParamsRepository.findByLoanId(100L)).thenReturn(Optional.of(locParams));

        // When: Event is processed
        invokeOnBusinessEvent();

        // Then: Should call computeLocBalance with full transaction amount
        verify(lineOfCreditBalanceUpdateService).computeLocBalance(eq(100L), eq(300L), eq(new BigDecimal("500.00")),
                eq(lineOfCredit), eq(LocalDate.of(2025, 1, 15)), eq(LineOfCreditTransactionType.REPAYMENT));
    }

    @Test
    @DisplayName("Should update LOC balance for non-receivable LOC using principal portion")
    void testNonReceivableLoc_UsePrincipalPortion() throws Exception {
        // Given: Non-receivable LOC
        when(lineOfCredit.getProductType()).thenReturn(LocProductType.PAYABLE);
        when(loanLineOfCreditParamsRepository.findByLoanId(100L)).thenReturn(Optional.of(locParams));

        // When: Event is processed
        invokeOnBusinessEvent();

        // Then: Should call computeLocBalance with principal portion
        verify(lineOfCreditBalanceUpdateService).computeLocBalance(eq(100L), eq(300L), eq(new BigDecimal("400.00")),
                eq(lineOfCredit), eq(LocalDate.of(2025, 1, 15)), eq(LineOfCreditTransactionType.REPAYMENT));
    }

    @Test
    @DisplayName("Should throw exception when account transfer transaction is missing")
    void testMissingAccountTransferTransaction_ThrowsException() throws Exception {
        // Given: No account transfer transaction
        when(accountTransferDetails.getAccountTransferTransactions()).thenReturn(List.of());
        when(loanLineOfCreditParamsRepository.findByLoanId(100L)).thenReturn(Optional.of(locParams));

        // When/Then: Should throw exception
        Exception thrownException = null;
        try {
            invokeOnBusinessEvent();
        } catch (Exception e) {
            thrownException = e;
        }

        assertThat(thrownException).isNotNull();
        assertThat(thrownException.getCause()).isInstanceOf(GeneralPlatformDomainRuleException.class);
        assertThat(thrownException.getCause().getMessage()).contains("Account transfer transaction not found");
    }

    @Test
    @DisplayName("Should handle LOC balance update failure with enhanced context")
    void testLocBalanceUpdateFailure_EnhancedErrorContext() throws Exception {
        // Given: LOC balance update fails
        when(lineOfCredit.getProductType()).thenReturn(LocProductType.RECEIVABLE);
        when(loanLineOfCreditParamsRepository.findByLoanId(100L)).thenReturn(Optional.of(locParams));

        PlatformApiDataValidationException originalException = new PlatformApiDataValidationException(
                "error.msg.loc.consumed.amount.negative", "Consumed amount cannot be negative after transaction",
                "consumedAmount", new BigDecimal("-50.00"));

        doThrow(originalException).when(lineOfCreditBalanceUpdateService).computeLocBalance(anyLong(), anyLong(),
                any(BigDecimal.class), any(LineOfCredit.class), any(LocalDate.class), any(LineOfCreditTransactionType.class));

        // When/Then: Should throw exception with enhanced context
        Exception thrownException = null;
        try {
            invokeOnBusinessEvent();
        } catch (Exception e) {
            thrownException = e;
        }

        assertThat(thrownException).isNotNull();
        // The exception is wrapped in InvocationTargetException by reflection, so we need to unwrap it
        Throwable actualException = thrownException;
        while (actualException.getCause() != null && !(actualException instanceof PlatformApiDataValidationException)) {
            actualException = actualException.getCause();
        }

        assertThat(actualException).isInstanceOf(PlatformApiDataValidationException.class);
        PlatformApiDataValidationException locException = (PlatformApiDataValidationException) actualException;

        // Check the errors list for the actual user message
        assertThat(locException.getErrors()).isNotEmpty();
        String errorMessage = locException.getErrors().get(0).getDefaultUserMessage();
        assertThat(errorMessage).contains("Failed to update LOC balance for Savings → Loan transfer")
                .contains("LOC ID: " + lineOfCredit.getId())
                .contains("Loan ID: 100")
                .contains("From Savings Account ID: 200");

        // Verify: computeLocBalance was called
        verify(lineOfCreditBalanceUpdateService).computeLocBalance(anyLong(), anyLong(), any(BigDecimal.class),
                any(LineOfCredit.class), any(LocalDate.class), any(LineOfCreditTransactionType.class));
    }

    @Test
    @DisplayName("Should handle unexpected errors gracefully")
    void testUnexpectedError_HandledGracefully() throws Exception {
        // Given: Unexpected error during LOC balance update
        when(lineOfCredit.getProductType()).thenReturn(LocProductType.RECEIVABLE);
        when(loanLineOfCreditParamsRepository.findByLoanId(100L)).thenReturn(Optional.of(locParams));

        RuntimeException unexpectedError = new RuntimeException("Database connection lost");
        doThrow(unexpectedError).when(lineOfCreditBalanceUpdateService).computeLocBalance(anyLong(), anyLong(),
                any(BigDecimal.class), any(LineOfCredit.class), any(LocalDate.class), any(LineOfCreditTransactionType.class));

        // When/Then: Should throw GeneralPlatformDomainRuleException with context
        Exception thrownException = null;
        try {
            invokeOnBusinessEvent();
        } catch (Exception e) {
            thrownException = e;
        }

        assertThat(thrownException).isNotNull();
        assertThat(thrownException.getCause()).isInstanceOf(GeneralPlatformDomainRuleException.class);
        assertThat(thrownException.getCause().getMessage()).contains("Unexpected error updating LOC balance")
                .contains("LOC ID: " + lineOfCredit.getId())
                .contains("Loan ID: 100")
                .contains("From Savings Account ID: 200");
    }

    @Test
    @DisplayName("Should not process if LOC params not found")
    void testNoLocParams_NoProcessing() throws Exception {
        // Given: No LOC params for loan
        when(loanLineOfCreditParamsRepository.findByLoanId(100L)).thenReturn(Optional.empty());

        // When: Event is processed
        invokeOnBusinessEvent();

        // Then: Should not call LOC balance update
        verify(lineOfCreditBalanceUpdateService, never()).computeLocBalance(anyLong(), anyLong(), any(BigDecimal.class),
                any(LineOfCredit.class), any(LocalDate.class), any(LineOfCreditTransactionType.class));
    }

    @Test
    @DisplayName("Should not process if from savings account is null")
    void testNullFromSavingsAccount_NoProcessing() throws Exception {
        // Given: No from savings account
        when(accountTransferDetails.fromSavingsAccount()).thenReturn(null);
        when(accountTransferDetails.toLoanAccount()).thenReturn(loan);

        // When: Event is processed
        invokeOnBusinessEvent();

        // Then: Should not call LOC balance update
        verify(lineOfCreditBalanceUpdateService, never()).computeLocBalance(anyLong(), anyLong(), any(BigDecimal.class),
                any(LineOfCredit.class), any(LocalDate.class), any(LineOfCreditTransactionType.class));
    }

    @Test
    @DisplayName("Should not process if to loan account is null")
    void testNullToLoanAccount_NoProcessing() throws Exception {
        // Given: No to loan account
        when(accountTransferDetails.fromSavingsAccount()).thenReturn(savingsAccount);
        when(accountTransferDetails.toLoanAccount()).thenReturn(null);

        // When: Event is processed
        invokeOnBusinessEvent();

        // Then: Should not call LOC balance update
        verify(lineOfCreditBalanceUpdateService, never()).computeLocBalance(anyLong(), anyLong(), any(BigDecimal.class),
                any(LineOfCredit.class), any(LocalDate.class), any(LineOfCreditTransactionType.class));
    }

    @Test
    @DisplayName("Should handle null loan transaction ID gracefully")
    void testNullLoanTransactionId_HandledGracefully() throws Exception {
        // Given: Loan transaction is null
        when(loanTransaction.getId()).thenReturn(null);
        when(lineOfCredit.getProductType()).thenReturn(LocProductType.RECEIVABLE);
        when(loanLineOfCreditParamsRepository.findByLoanId(100L)).thenReturn(Optional.of(locParams));

        // When: Event is processed
        invokeOnBusinessEvent();

        // Then: Should call with null loan transaction ID
        verify(lineOfCreditBalanceUpdateService).computeLocBalance(eq(100L), eq((Long) null), any(BigDecimal.class),
                eq(lineOfCredit), any(LocalDate.class), eq(LineOfCreditTransactionType.REPAYMENT));
    }

    // Helper method to create a LineOfCredit for testing
    private LineOfCredit createLineOfCredit(Long locId, BigDecimal maximumAmount, BigDecimal consumedAmount, BigDecimal availableBalance) {
        LineOfCredit loc = mock(LineOfCredit.class);
        when(loc.getId()).thenReturn(locId);
        when(loc.getMaximumAmount()).thenReturn(maximumAmount);

        LineOfCreditSummary summary = new LineOfCreditSummary();
        summary.setConsumedAmount(consumedAmount);
        summary.setAvailableBalance(availableBalance);
        summary.setTotalDrawDownCountDerived(BigDecimal.ZERO);
        summary.setTotalOfFeesDerived(BigDecimal.ZERO);
        summary.setNetOutstandingAmount(BigDecimal.ZERO);

        when(loc.getSummary()).thenReturn(summary);

        return loc;
    }
}
