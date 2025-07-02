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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
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
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.exception.InsufficientAccountBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;

class CustomExecuteStandingInstructionsTaskletTest {

    private StandingInstructionReadPlatformService standingInstructionReadPlatformService;
    private JdbcTemplate jdbcTemplate;
    private DatabaseSpecificSQLGenerator sqlGenerator;
    private AccountTransfersWritePlatformService accountTransfersWritePlatformService;
    private SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper;
    private CustomExecuteStandingInstructionsTasklet tasklet;

    @BeforeEach
    void setUp() {
        standingInstructionReadPlatformService = mock(StandingInstructionReadPlatformService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
        accountTransfersWritePlatformService = mock(AccountTransfersWritePlatformService.class);
        savingsAccountRepositoryWrapper = mock(SavingsAccountRepositoryWrapper.class);

        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DateUtils.parseLocalDate("2025-05-20"))));

        when(sqlGenerator.escape(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        tasklet = new CustomExecuteStandingInstructionsTasklet(standingInstructionReadPlatformService, jdbcTemplate, sqlGenerator,
                accountTransfersWritePlatformService, savingsAccountRepositoryWrapper);
    }

    @Test
    void testFullPaymentIsProcessed() throws Exception {
        StandingInstructionData instruction = mock(StandingInstructionData.class);

        // Set up enums
        when(instruction.getRecurrenceType()).thenReturn(AccountTransferRecurrenceType.PERIODIC);
        when(instruction.getInstructionType()).thenReturn(StandingInstructionType.FIXED);
        when(instruction.getRecurrenceFrequency()).thenReturn(PeriodFrequencyType.MONTHS);
        when(instruction.getFromAccountType()).thenReturn(PortfolioAccountType.SAVINGS);
        when(instruction.getToAccountType()).thenReturn(PortfolioAccountType.LOAN);
        when(instruction.getRecurrenceInterval()).thenReturn(1);
        when(standingInstructionReadPlatformService.retrieveAll(StandingInstructionStatus.ACTIVE.getValue()))
                .thenReturn(Collections.singletonList(instruction));
        when(instruction.getRecurrenceOnDay()).thenReturn(5);
        tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));
    }

    @Test
    void testPartialPaymentIsProcessedWhenInsufficientBalance() throws Exception {
        Long fromAccountId = 1L;
        BigDecimal requiredAmount = new BigDecimal("100.00");
        BigDecimal availableBalance = new BigDecimal("40.00");
        StandingInstructionData instruction = mock(StandingInstructionData.class);

        // Set up enums
        when(instruction.getRecurrenceType()).thenReturn(AccountTransferRecurrenceType.PERIODIC);
        when(instruction.getInstructionType()).thenReturn(StandingInstructionType.FIXED);
        when(instruction.getRecurrenceFrequency()).thenReturn(PeriodFrequencyType.MONTHS);
        when(instruction.getFromAccountType()).thenReturn(PortfolioAccountType.SAVINGS);
        when(instruction.getToAccountType()).thenReturn(PortfolioAccountType.LOAN);
        when(instruction.getRecurrenceInterval()).thenReturn(1);

        when(standingInstructionReadPlatformService.retrieveAll(StandingInstructionStatus.ACTIVE.getValue()))
                .thenReturn(Collections.singletonList(instruction));

        // Simulate insufficient balance on first transfer, then partial transfer with available balance
        doThrow(new InsufficientAccountBalanceException("amount", availableBalance, null, requiredAmount))
                .when(accountTransfersWritePlatformService).transferFunds(any(AccountTransferDTO.class));

        SavingsAccount savingsAccount = mock(SavingsAccount.class);
        when(savingsAccount.getWithdrawableBalance()).thenReturn(availableBalance);
        when(savingsAccountRepositoryWrapper.findOneWithNotFoundDetection(fromAccountId)).thenReturn(savingsAccount);
        when(instruction.getRecurrenceOnDay()).thenReturn(5);
        tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));
        verify(jdbcTemplate, never()).update(contains("UPDATE m_account_transfer_standing_instructions SET last_run_date"), any(), any());
    }

    @Test
    void testNoPaymentWhenNoFundsAvailable() {
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        BigDecimal requiredAmount = new BigDecimal("100.00");
        LocalDate validFrom = DateUtils.parseLocalDate("2025-05-20");

        StandingInstructionData instruction = mock(StandingInstructionData.class);

        // Set up enums
        when(instruction.getRecurrenceType()).thenReturn(AccountTransferRecurrenceType.PERIODIC);
        when(instruction.getInstructionType()).thenReturn(StandingInstructionType.FIXED);
        when(instruction.getRecurrenceFrequency()).thenReturn(PeriodFrequencyType.MONTHS);
        when(instruction.getFromAccountType()).thenReturn(PortfolioAccountType.SAVINGS);
        when(instruction.getToAccountType()).thenReturn(PortfolioAccountType.LOAN);
        when(instruction.getRecurrenceOnDay()).thenReturn(20);
        when(instruction.getRecurrenceInterval()).thenReturn(1);
        when(instruction.getTransferType()).thenReturn(AccountTransferType.ACCOUNT_TRANSFER);

        // *** Add these stubs ***
        when(instruction.getAmount()).thenReturn(requiredAmount);
        when(instruction.getFromAccount()).thenReturn(realPortfolioAccountData(fromAccountId));
        when(instruction.getToAccount()).thenReturn(realPortfolioAccountData(toAccountId));
        when(instruction.getName()).thenReturn("No Funds SI");
        when(instruction.getId()).thenReturn(30L);
        when(instruction.getValidFrom()).thenReturn(validFrom);
        when(instruction.toTransferType()).thenReturn(1);

        when(standingInstructionReadPlatformService.retrieveAll(StandingInstructionStatus.ACTIVE.getValue()))
                .thenReturn(Collections.singletonList(instruction));

        // Simulate insufficient balance on first transfer, and no funds available for partial
        doThrow(new InsufficientAccountBalanceException("amount", BigDecimal.ZERO, null, requiredAmount))
                .when(accountTransfersWritePlatformService).transferFunds(any(AccountTransferDTO.class));

        when(savingsAccountRepositoryWrapper.findOneWithNotFoundDetection(fromAccountId)).thenReturn(null);

        assertThatThrownBy(() -> tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class)))
                .isInstanceOf(JobExecutionException.class);

        verify(jdbcTemplate, never()).update(contains("UPDATE m_account_transfer_standing_instructions SET last_run_date"), any(), any());
    }

    private PortfolioAccountData realPortfolioAccountData(Long id) {
        return new PortfolioAccountData(id, "dummyAccountNo", null, null, null, null, null, null, null, null, null, null);
    }
}
