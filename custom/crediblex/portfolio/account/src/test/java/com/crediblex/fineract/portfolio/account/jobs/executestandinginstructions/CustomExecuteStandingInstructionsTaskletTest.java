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
package com.crediblex.fineract.portfolio.account.jobs.executestandinginstructions;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.crediblex.fineract.commands.CredXSynchronousCommandProcessingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.crediblex.fineract.commands.LoanStatusWebhookPublisher;
import com.crediblex.fineract.portfolio.account.repository.EzySqlLoanLocRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.data.PortfolioAccountData;
import org.apache.fineract.portfolio.account.data.StandingInstructionData;
import org.apache.fineract.portfolio.account.domain.AccountTransferRecurrenceType;
import org.apache.fineract.portfolio.account.domain.AccountTransferType;
import org.apache.fineract.portfolio.account.domain.StandingInstructionStatus;
import org.apache.fineract.portfolio.account.domain.StandingInstructionType;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.account.service.StandingInstructionReadPlatformService;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.exception.InsufficientAccountBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CustomExecuteStandingInstructionsTaskletTest {

    private StandingInstructionReadPlatformService standingInstructionReadPlatformService;
    private JdbcTemplate jdbcTemplate;
    private DatabaseSpecificSQLGenerator sqlGenerator;
    private AccountTransfersWritePlatformService accountTransfersWritePlatformService;
    private CustomExecuteStandingInstructionsTasklet tasklet;
    private PlatformTransactionManager platformTransactionManager;
    private SavingsAccountAssembler savingsAccountAssembler;
    private CredXSynchronousCommandProcessingService customCommandProcessingService;
    private FromJsonHelper fromApiJsonHelper;
    private LoanStatusWebhookPublisher loanStatusWebhookPublisher;
    private TransactionTemplate transactionTemplate;
    private EzySqlLoanLocRepository ezySqlLoanLocRepository;

    @BeforeEach
    void setUp() {
        standingInstructionReadPlatformService = mock(StandingInstructionReadPlatformService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
        accountTransfersWritePlatformService = mock(AccountTransfersWritePlatformService.class);
        savingsAccountAssembler = mock(SavingsAccountAssembler.class);
        platformTransactionManager = mock(PlatformTransactionManager.class);
        customCommandProcessingService = mock(CredXSynchronousCommandProcessingService.class);
        fromApiJsonHelper = mock(FromJsonHelper.class);
        loanStatusWebhookPublisher = mock(LoanStatusWebhookPublisher.class);
        transactionTemplate = new TransactionTemplate(platformTransactionManager);
        ezySqlLoanLocRepository = mock(EzySqlLoanLocRepository.class);

        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DateUtils.parseLocalDate("2025-05-20"))));

        when(sqlGenerator.escape(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        tasklet = new CustomExecuteStandingInstructionsTasklet(standingInstructionReadPlatformService, jdbcTemplate, sqlGenerator,
                accountTransfersWritePlatformService, savingsAccountAssembler, platformTransactionManager, customCommandProcessingService,
                fromApiJsonHelper, loanStatusWebhookPublisher, transactionTemplate, ezySqlLoanLocRepository);
    }

    @Test
    void testFullPaymentIsProcessed() throws Exception {
        // Arrange
        StandingInstructionData instruction = realStandingInstructionData(10L, "Full Payment SI", new BigDecimal("100.00"),
                PortfolioAccountType.SAVINGS, 1L, PortfolioAccountType.LOAN, 2L, StandingInstructionType.FIXED,
                AccountTransferRecurrenceType.PERIODIC, PeriodFrequencyType.MONTHS, 1, 20, LocalDate.of(2025, 5, 20));
        when(standingInstructionReadPlatformService.retrieveAll(StandingInstructionStatus.ACTIVE.getValue()))
                .thenReturn(Collections.singletonList(instruction));

        SavingsAccount savingsAccount = mock(SavingsAccount.class);
        when(savingsAccount.getWithdrawableBalance()).thenReturn(new BigDecimal("1000.00"));
        when(savingsAccountAssembler.assembleFrom(1L, false)).thenReturn(savingsAccount);

        // Act
        tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

        // Assert
        // Should call transferFunds with the full amount
        ArgumentCaptor<AccountTransferDTO> transferCaptor = ArgumentCaptor.forClass(AccountTransferDTO.class);
        verify(accountTransfersWritePlatformService).transferFunds(transferCaptor.capture());
        assertThat(transferCaptor.getValue().getTransactionAmount()).isEqualByComparingTo("100.00");

        // Should update last_run_date
        verify(jdbcTemplate).update(eq("UPDATE m_account_transfer_standing_instructions SET last_run_date = ? where id = ?"),
                eq(LocalDate.of(2025, 5, 20)), eq(10L));
    }

    @Test
    void testPartialPaymentIsProcessedWhenInsufficientBalance() throws Exception {
        // Arrange
        Long fromAccountId = 1L;
        BigDecimal requiredAmount = new BigDecimal("100.00");
        BigDecimal availableBalance = new BigDecimal("40.00");
        StandingInstructionData instruction = realStandingInstructionData(20L, "Partial Payment SI", requiredAmount,
                PortfolioAccountType.SAVINGS, fromAccountId, PortfolioAccountType.LOAN, 2L, StandingInstructionType.FIXED,
                AccountTransferRecurrenceType.PERIODIC, PeriodFrequencyType.MONTHS, 1, 20, LocalDate.of(2025, 5, 20));
        when(standingInstructionReadPlatformService.retrieveAll(StandingInstructionStatus.ACTIVE.getValue()))
                .thenReturn(Collections.singletonList(instruction));

        // Simulate insufficient balance on transfer with available balance
        doAnswer(invocation -> {
            AccountTransferDTO dto = invocation.getArgument(0);
            if (dto.getTransactionAmount().compareTo(requiredAmount) == 0) {
                throw new InsufficientAccountBalanceException("amount", availableBalance, null, requiredAmount);
            }
            return null; // partial payment succeeds
        }).when(accountTransfersWritePlatformService).transferFunds(any(AccountTransferDTO.class));

        SavingsAccount savingsAccount = mock(SavingsAccount.class);
        when(savingsAccount.getWithdrawableBalance()).thenReturn(availableBalance);
        when(savingsAccountAssembler.assembleFrom(fromAccountId, false)).thenReturn(savingsAccount);

        // Act
        tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

        // Assert
        ArgumentCaptor<AccountTransferDTO> transferCaptor = ArgumentCaptor.forClass(AccountTransferDTO.class);
        verify(accountTransfersWritePlatformService).transferFunds(transferCaptor.capture());
        AccountTransferDTO capturedTransferDTO = transferCaptor.getValue();
        assertThat(capturedTransferDTO.getTransactionAmount()).isEqualByComparingTo("40.00");

        // Should NOT update last_run_date (partial payment)
        verify(jdbcTemplate, never()).update(contains("UPDATE m_account_transfer_standing_instructions SET last_run_date"), any(), any());
    }

    @Test
    void testNoPaymentWhenNoFundsAvailable() throws Exception {
        // Arrange
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal requiredAmount = new BigDecimal("100.00");
        LocalDate validFrom = LocalDate.of(2025, 5, 20);

        StandingInstructionData instruction = realStandingInstructionData(30L, "No Funds SI", requiredAmount, PortfolioAccountType.SAVINGS,
                fromAccountId, PortfolioAccountType.LOAN, toAccountId, StandingInstructionType.FIXED,
                AccountTransferRecurrenceType.PERIODIC, PeriodFrequencyType.MONTHS, 1, 20, validFrom);
        when(standingInstructionReadPlatformService.retrieveAll(StandingInstructionStatus.ACTIVE.getValue()))
                .thenReturn(Collections.singletonList(instruction));

        // Simulate insufficient balance on first transfer, and no funds available for partial
        doThrow(new InsufficientAccountBalanceException("amount", BigDecimal.ZERO, null, requiredAmount))
                .when(accountTransfersWritePlatformService).transferFunds(any(AccountTransferDTO.class));
        SavingsAccount savingsAccount = mock(SavingsAccount.class);
        when(savingsAccount.getWithdrawableBalance()).thenReturn(BigDecimal.ZERO);
        when(savingsAccountAssembler.assembleFrom(fromAccountId, false)).thenReturn(savingsAccount);

        // Act
        tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

        // Assert
        verify(accountTransfersWritePlatformService, never()).transferFunds(any(AccountTransferDTO.class));
        verify(jdbcTemplate, never()).update(contains("UPDATE m_account_transfer_standing_instructions SET last_run_date"), any(), any());
    }

    @Test
    void testTransactionCommitmentWhenSomeTransfersFail() throws Exception {
        Long fromAccountId1 = 1L;
        Long fromAccountId2 = 3L;
        Long fromAccountId3 = 5L;
        Long toAccountId1 = 2L;
        Long toAccountId2 = 4L;
        Long toAccountId3 = 6L;
        StandingInstructionData successfulInstruction1 = realStandingInstructionData(40L, "Successful SI 1", new BigDecimal("50.00"),
                PortfolioAccountType.SAVINGS, fromAccountId1, PortfolioAccountType.LOAN, toAccountId1, StandingInstructionType.FIXED,
                AccountTransferRecurrenceType.PERIODIC, PeriodFrequencyType.MONTHS, 1, 20, LocalDate.of(2025, 5, 20));

        StandingInstructionData successfulInstruction2 = realStandingInstructionData(41L, "Successful SI 2", new BigDecimal("75.00"),
                PortfolioAccountType.SAVINGS, fromAccountId2, PortfolioAccountType.LOAN, toAccountId2, StandingInstructionType.FIXED,
                AccountTransferRecurrenceType.PERIODIC, PeriodFrequencyType.MONTHS, 1, 20, LocalDate.of(2025, 5, 20));

        StandingInstructionData failingInstruction = realStandingInstructionData(42L, "Failing SI", new BigDecimal("100.00"),
                PortfolioAccountType.SAVINGS, fromAccountId3, PortfolioAccountType.LOAN, toAccountId3, StandingInstructionType.FIXED,
                AccountTransferRecurrenceType.PERIODIC, PeriodFrequencyType.MONTHS, 1, 20, LocalDate.of(2025, 5, 20));

        when(standingInstructionReadPlatformService.retrieveAll(StandingInstructionStatus.ACTIVE.getValue()))
                .thenReturn(Arrays.asList(successfulInstruction1, successfulInstruction2, failingInstruction));

        doAnswer(invocation -> {
            AccountTransferDTO dto = invocation.getArgument(0);
            Long instructionId = dto.getFromAccountId();

            if (instructionId.equals(1L)) {
                // Successful transfer for instruction 40
                return null;
            } else if (instructionId.equals(3L)) {
                // Successful transfer for instruction 41
                return null;
            } else if (instructionId.equals(5L)) {
                // Failing transfer for instruction 42
                throw new RuntimeException("Simulated transfer failure");
            }
            return null;
        }).when(accountTransfersWritePlatformService).transferFunds(any(AccountTransferDTO.class));

        // Mock accounts and balances
        SavingsAccount savingsAccount1 = mock(SavingsAccount.class);
        when(savingsAccount1.getWithdrawableBalance()).thenReturn(new BigDecimal("1000.00"));
        when(savingsAccountAssembler.assembleFrom(fromAccountId1, false)).thenReturn(savingsAccount1);

        SavingsAccount savingsAccount2 = mock(SavingsAccount.class);
        when(savingsAccount2.getWithdrawableBalance()).thenReturn(new BigDecimal("1000.00"));
        when(savingsAccountAssembler.assembleFrom(fromAccountId2, false)).thenReturn(savingsAccount2);

        SavingsAccount savingsAccount3 = mock(SavingsAccount.class);
        when(savingsAccount3.getWithdrawableBalance()).thenReturn(new BigDecimal("1000.00"));
        when(savingsAccountAssembler.assembleFrom(fromAccountId3, false)).thenReturn(savingsAccount3);

        assertThatThrownBy(() -> tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class)))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("Unhandled System Exception while transferring funds for standing Instruction id42");

        verify(accountTransfersWritePlatformService, times(3)).transferFunds(any(AccountTransferDTO.class));

        verify(jdbcTemplate).update(eq("UPDATE m_account_transfer_standing_instructions SET last_run_date = ? where id = ?"),
                eq(LocalDate.of(2025, 5, 20)), eq(40L));
        verify(jdbcTemplate).update(eq("UPDATE m_account_transfer_standing_instructions SET last_run_date = ? where id = ?"),
                eq(LocalDate.of(2025, 5, 20)), eq(41L));

        verify(jdbcTemplate, never()).update(eq("UPDATE m_account_transfer_standing_instructions SET last_run_date = ? where id = ?"),
                eq(LocalDate.of(2025, 5, 20)), eq(42L));

        verify(jdbcTemplate, times(3)).update(contains("INSERT INTO m_account_transfer_standing_instructions_history"));

    }

    private EnumOptionData enumOptionData(int id, String code) {
        return new EnumOptionData((long) id, code, null);
    }

    private StandingInstructionData realStandingInstructionData(Long id, String name, BigDecimal amount, PortfolioAccountType fromType,
            Long fromId, PortfolioAccountType toType, Long toId, StandingInstructionType instructionType,
            AccountTransferRecurrenceType recurrenceType, PeriodFrequencyType frequencyType, Integer interval, Integer day,
            LocalDate validFrom) {
        return StandingInstructionData.instance(id, // id
                null, // accountDetailId
                name, // name
                null, // fromOffice
                null, // toOffice
                null, // fromClient
                null, // toClient
                enumOptionData(fromType.getValue(), fromType.getCode()), // fromAccountType
                new PortfolioAccountData(fromId, "fromAcc", null, null, null, null, null, null, null, null, null, null), // fromAccount
                enumOptionData(toType.getValue(), toType.getCode()), // toAccountType
                new PortfolioAccountData(toId, "toAcc", null, null, null, null, null, null, null, null, null, null), // toAccount
                enumOptionData(AccountTransferType.ACCOUNT_TRANSFER.getValue(), AccountTransferType.ACCOUNT_TRANSFER.getCode()), // transferType
                null, // priority
                enumOptionData(instructionType.getValue(), instructionType.getCode()), // instructionType
                null, // status
                amount, // amount
                validFrom, // validFrom
                null, // validTill
                enumOptionData(recurrenceType.getValue(), recurrenceType.getCode()), // recurrenceType
                enumOptionData(frequencyType.getValue(), frequencyType.getCode()), // recurrenceFrequency
                interval, // recurrenceInterval
                MonthDay.of(5, day) // recurrenceOnMonthDay
        );
    }
}
