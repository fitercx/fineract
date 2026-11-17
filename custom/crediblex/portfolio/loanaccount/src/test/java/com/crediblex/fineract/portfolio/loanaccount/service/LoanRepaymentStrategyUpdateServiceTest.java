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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;
import org.apache.fineract.portfolio.loanproduct.data.TransactionProcessingStrategyData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanRepaymentStrategyUpdateServiceTest {

    private static final Long LOAN_ID = 331L;
    private static final String STANDARD = "mifos-standard-strategy";
    private static final String STANDARD_NAME = "Penalties, Fees, Interest, Principal order";
    private static final String PRINCIPAL_FIRST = "principal-interest-penalties-fees-order-strategy";
    private static final String PRINCIPAL_FIRST_NAME = "Principal, Interest, Penalties, Fees Order";
    private static final String CROSS_EMI = CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE;
    private static final String CROSS_EMI_NAME = CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor.STRATEGY_NAME;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private LoanRepaymentScheduleTransactionProcessorFactory processorFactory;

    private LoanRepaymentStrategyUpdateService service;

    @BeforeEach
    void setUp() {
        this.service = new LoanRepaymentStrategyUpdateService(this.loanRepositoryWrapper, this.processorFactory);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Dubai", null));
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 11, 4))));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void updateStoresTheNewStrategyAndLeavesPostedTransactionsAlone() {
        final Loan loan = activeLoan(STANDARD, STANDARD_NAME);
        when(this.processorFactory.getStrategies()).thenReturn(strategies());

        final CommandProcessingResult result = this.service.update(LOAN_ID, command(PRINCIPAL_FIRST, "Switch before settlement"));

        verify(loan).updateTransactionProcessingStrategy(PRINCIPAL_FIRST, PRINCIPAL_FIRST_NAME);
        verify(this.loanRepositoryWrapper).saveAndFlush(loan);
        assertThat(result.getLoanId()).isEqualTo(LOAN_ID);
        assertThat(result.getChanges())
                .containsEntry(LoanRepaymentStrategyUpdateService.TRANSACTION_PROCESSING_STRATEGY_CODE, PRINCIPAL_FIRST)
                .containsEntry("previousTransactionProcessingStrategyCode", STANDARD)
                .containsEntry(LoanRepaymentStrategyUpdateService.NOTE, "Switch before settlement");
    }

    @Test
    void updateKeepsPostedRepaymentPortionsWhenTheStrategyChanges() {
        final Loan loan = activeLoan(STANDARD, STANDARD_NAME);
        final LoanTransaction postedRepayment = mock(LoanTransaction.class);
        when(postedRepayment.getId()).thenReturn(900L);
        when(postedRepayment.isReversed()).thenReturn(false);
        when(postedRepayment.isRepaymentLikeType()).thenReturn(true);
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>(List.of(postedRepayment)));
        when(this.processorFactory.getStrategies()).thenReturn(strategies());

        this.service.update(LOAN_ID, command(PRINCIPAL_FIRST, null));

        verify(postedRepayment).setManuallyAdjustedOrReversed();
        verify(loan).updateTransactionProcessingStrategy(PRINCIPAL_FIRST, PRINCIPAL_FIRST_NAME);
    }

    @Test
    void updateRejectsAnInactiveLoanWithoutSaving() {
        final Loan loan = mock(Loan.class);
        when(loan.isOpen()).thenReturn(false);
        when(this.loanRepositoryWrapper.findOneWithNotFoundDetection(LOAN_ID)).thenReturn(loan);

        assertThatThrownBy(() -> this.service.update(LOAN_ID, command(PRINCIPAL_FIRST, null)))
                .isInstanceOf(PlatformApiDataValidationException.class);
        verify(loan, never()).updateTransactionProcessingStrategy(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(this.loanRepositoryWrapper, never()).saveAndFlush(loan);
    }

    @Test
    void updateRejectsAdvancedPaymentAllocation() {
        final Loan loan = activeLoan(LoanProductConstants.ADVANCED_PAYMENT_ALLOCATION_STRATEGY, "Advanced payment allocation");

        assertThatThrownBy(() -> this.service.update(LOAN_ID, command(PRINCIPAL_FIRST, null)))
                .isInstanceOf(PlatformApiDataValidationException.class);
        verify(this.loanRepositoryWrapper, never()).saveAndFlush(loan);
    }

    @Test
    void updateRejectsAnUnknownStrategy() {
        final Loan loan = activeLoan(STANDARD, STANDARD_NAME);
        when(this.processorFactory.getStrategies()).thenReturn(strategies());

        assertThatThrownBy(() -> this.service.update(LOAN_ID, command("not-a-strategy", null)))
                .isInstanceOf(PlatformApiDataValidationException.class);
        verify(loan, never()).updateTransactionProcessingStrategy(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRejectsTheStrategyAlreadyOnTheLoan() {
        final Loan loan = activeLoan(STANDARD, STANDARD_NAME);
        when(this.processorFactory.getStrategies()).thenReturn(strategies());

        assertThatThrownBy(() -> this.service.update(LOAN_ID, command(STANDARD, null)))
                .isInstanceOf(PlatformApiDataValidationException.class);
        verify(this.loanRepositoryWrapper, never()).saveAndFlush(loan);
    }

    @Test
    void templateOmitsAdvancedAllocationAndExplainsWhenTheLoanUsesIt() {
        final Loan loan = activeLoan(LoanProductConstants.ADVANCED_PAYMENT_ALLOCATION_STRATEGY, "Advanced payment allocation");
        when(this.processorFactory.getStrategies()).thenReturn(strategies());

        final Map<String, Object> template = this.service.template(LOAN_ID);

        @SuppressWarnings("unchecked")
        final List<Map<String, String>> options = (List<Map<String, String>>) template.get("transactionProcessingStrategyOptions");
        assertThat(options).extracting(option -> option.get("code")).containsExactly(STANDARD, PRINCIPAL_FIRST, CROSS_EMI);
        assertThat(template.get("unsupportedReason")).isNotNull();
    }

    private Loan activeLoan(final String code, final String name) {
        final Loan loan = mock(Loan.class);
        lenient().when(loan.isOpen()).thenReturn(true);
        lenient().when(loan.getId()).thenReturn(LOAN_ID);
        lenient().when(loan.getTransactionProcessingStrategyCode()).thenReturn(code);
        lenient().when(loan.getTransactionProcessingStrategyName()).thenReturn(name);
        lenient().when(loan.getLoanTransactions()).thenReturn(new ArrayList<>());
        when(this.loanRepositoryWrapper.findOneWithNotFoundDetection(LOAN_ID)).thenReturn(loan);
        return loan;
    }

    private JsonCommand command(final String strategyCode, final String note) {
        final JsonCommand command = mock(JsonCommand.class);
        lenient().when(command.stringValueOfParameterNamed(LoanRepaymentStrategyUpdateService.TRANSACTION_PROCESSING_STRATEGY_CODE))
                .thenReturn(strategyCode);
        lenient().when(command.stringValueOfParameterNamedAllowingNull(LoanRepaymentStrategyUpdateService.NOTE)).thenReturn(note);
        lenient().when(command.getCommandId()).thenReturn(99L);
        return command;
    }

    @Test
    void updateRejectsCrossEmiWhenTheLoanIsNotOverdue() {
        final Loan loan = activeLoan(STANDARD, STANDARD_NAME);
        when(this.processorFactory.getStrategies()).thenReturn(strategies());

        assertThatThrownBy(() -> this.service.update(LOAN_ID, command(CROSS_EMI, null)))
                .isInstanceOf(PlatformApiDataValidationException.class);
        verify(loan, never()).updateTransactionProcessingStrategy(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(this.loanRepositoryWrapper, never()).saveAndFlush(loan);
    }

    @Test
    void updateAcceptsCrossEmiWhenAnInstallmentIsOverdue() {
        final Loan loan = activeLoan(STANDARD, STANDARD_NAME);
        final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 1);
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        final Money outstanding = mock(Money.class);
        when(outstanding.isGreaterThanZero()).thenReturn(true);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(installment));
        when(installment.isDownPayment()).thenReturn(false);
        when(installment.isAdditional()).thenReturn(false);
        when(installment.getDueDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(installment.getPrincipalOutstanding(currency)).thenReturn(outstanding);
        when(this.processorFactory.getStrategies()).thenReturn(strategies());

        this.service.update(LOAN_ID, command(CROSS_EMI, null));

        verify(loan).updateTransactionProcessingStrategy(CROSS_EMI, CROSS_EMI_NAME);
    }

    private List<TransactionProcessingStrategyData> strategies() {
        return List.of(new TransactionProcessingStrategyData(1L, STANDARD, STANDARD_NAME),
                new TransactionProcessingStrategyData(2L, PRINCIPAL_FIRST, PRINCIPAL_FIRST_NAME),
                new TransactionProcessingStrategyData(4L, CROSS_EMI, CROSS_EMI_NAME), new TransactionProcessingStrategyData(3L,
                        LoanProductConstants.ADVANCED_PAYMENT_ALLOCATION_STRATEGY, "Advanced payment allocation"));
    }
}
