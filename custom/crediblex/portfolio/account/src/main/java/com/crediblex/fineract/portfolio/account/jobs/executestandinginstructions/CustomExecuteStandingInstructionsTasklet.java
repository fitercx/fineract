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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformServiceUnavailableException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.data.StandingInstructionData;
import org.apache.fineract.portfolio.account.data.StandingInstructionDuesData;
import org.apache.fineract.portfolio.account.domain.AccountTransferRecurrenceType;
import org.apache.fineract.portfolio.account.domain.StandingInstructionStatus;
import org.apache.fineract.portfolio.account.domain.StandingInstructionType;
import org.apache.fineract.portfolio.account.jobs.executestandinginstructions.ExecuteStandingInstructionsTasklet;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.account.service.StandingInstructionReadPlatformService;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ScheduledDateGenerator;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.exception.InsufficientAccountBalanceException;
import org.apache.fineract.portfolio.savings.exception.SavingsAccountNotFoundException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class CustomExecuteStandingInstructionsTasklet extends ExecuteStandingInstructionsTasklet {

    private final StandingInstructionReadPlatformService standingInstructionReadPlatformService;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final AccountTransfersWritePlatformService accountTransfersWritePlatformService;
    private final SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper;
    private final PlatformTransactionManager transactionManager;

    public CustomExecuteStandingInstructionsTasklet(StandingInstructionReadPlatformService standingInstructionReadPlatformService,
            JdbcTemplate jdbcTemplate, DatabaseSpecificSQLGenerator sqlGenerator,
            AccountTransfersWritePlatformService accountTransfersWritePlatformService,
            SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper, PlatformTransactionManager transactionManager) {

        super(standingInstructionReadPlatformService, jdbcTemplate, sqlGenerator, accountTransfersWritePlatformService);
        this.standingInstructionReadPlatformService = standingInstructionReadPlatformService;
        this.jdbcTemplate = jdbcTemplate;
        this.sqlGenerator = sqlGenerator;
        this.accountTransfersWritePlatformService = accountTransfersWritePlatformService;
        this.savingsAccountRepositoryWrapper = savingsAccountRepositoryWrapper;
        this.transactionManager = transactionManager;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Collection<StandingInstructionData> instructionData = standingInstructionReadPlatformService
                .retrieveAll(StandingInstructionStatus.ACTIVE.getValue());
        List<Throwable> errors = new ArrayList<>();

        log.info("Processing {} standing instructions", instructionData.size());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        int processedCount = 0;
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        int dueCount = 0;

        for (StandingInstructionData data : instructionData) {
            boolean isDueForTransfer = false;
            AccountTransferRecurrenceType recurrenceType = data.getRecurrenceType();
            StandingInstructionType instructionType = data.getInstructionType();
            LocalDate transactionDate = DateUtils.getBusinessLocalDate();

            if (recurrenceType.isPeriodicRecurrence()) {
                final ScheduledDateGenerator scheduledDateGenerator = new DefaultScheduledDateGenerator();
                PeriodFrequencyType frequencyType = data.getRecurrenceFrequency();
                LocalDate startDate = data.getValidFrom();
                if (frequencyType.isMonthly()) {
                    startDate = startDate.withDayOfMonth(data.getRecurrenceOnDay());
                    if (DateUtils.isBefore(startDate, data.getValidFrom())) {
                        startDate = startDate.plusMonths(1);
                    }
                } else if (frequencyType.isYearly()) {
                    startDate = startDate.withDayOfMonth(data.getRecurrenceOnDay()).withMonth(data.getRecurrenceOnMonth());
                    if (DateUtils.isBefore(startDate, data.getValidFrom())) {
                        startDate = startDate.plusYears(1);
                    }
                }
                isDueForTransfer = scheduledDateGenerator.isDateFallsInSchedule(frequencyType, data.getRecurrenceInterval(), startDate,
                        transactionDate);

            }
            BigDecimal transactionAmount = data.getAmount();
            if (data.getToAccountType().isLoanAccount()
                    && (recurrenceType.isDuesRecurrence() || (isDueForTransfer && instructionType.isDuesAmoutTransfer()))) {
                StandingInstructionDuesData standingInstructionDuesData = standingInstructionReadPlatformService
                        .retriveLoanDuesData(data.getToAccount().getId());
                if (data.getInstructionType().isDuesAmoutTransfer()) {
                    transactionAmount = standingInstructionDuesData.totalDueAmount();
                }
                if (recurrenceType.isDuesRecurrence()) {
                    isDueForTransfer = isDueForTransfer(standingInstructionDuesData);
                }
            }

            if (isDueForTransfer && transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) > 0) {
                dueCount++;
                final SavingsAccount fromSavingsAccount = null;
                final boolean isRegularTransaction = true;
                final boolean isExceptionForBalanceCheck = false;
                AccountTransferDTO accountTransferDTO = new AccountTransferDTO(transactionDate, transactionAmount,
                        data.getFromAccountType(), data.getToAccountType(), data.getFromAccount().getId(), data.getToAccount().getId(),
                        data.getName() + " Standing instruction transfer ", null, null, null, null, data.toTransferType(), null, null,
                        data.getTransferType().getValue(), null, null, ExternalId.empty(), null, null, fromSavingsAccount,
                        isRegularTransaction, isExceptionForBalanceCheck);

                try {
                    BigDecimal finalTransactionAmount = transactionAmount;
                    transactionTemplate.execute(status -> {
                        final boolean transferCompleted = transferAmountWithPartialPaymentSupport(errors, accountTransferDTO, data.getId(),
                                finalTransactionAmount);
                        if (transferCompleted) {
                            final String updateQuery = "UPDATE m_account_transfer_standing_instructions SET last_run_date = ? where id = ?";
                            jdbcTemplate.update(updateQuery, transactionDate, data.getId());
                            successCount.getAndIncrement();
                        } else {
                            failureCount.getAndIncrement();
                        }
                        return null;
                    });
                    log.debug("Successfully processed standing instruction {} for amount {}", data.getId(), transactionAmount);
                } catch (Exception e) {
                    failureCount.getAndIncrement();
                    log.error("Transfer failed for standing instruction {}: {}", data.getId(), e.getMessage(), e);
                    errors.add(e);
                }
                processedCount++;
            }
        }

        log.info("Standing instructions processing completed. Processed: {}, Success: {}, Failed: {}, Due: {}", processedCount,
                successCount, failureCount, dueCount);

        if (!errors.isEmpty()) {
            throw new JobExecutionException(errors);
        }
        return RepeatStatus.FINISHED;
    }

    /**
     * Enhanced transfer method that supports partial payments when insufficient balance is available
     */
    private boolean transferAmountWithPartialPaymentSupport(final List<Throwable> errors, final AccountTransferDTO accountTransferDTO,
            final Long instructionId, final BigDecimal originalAmount) {
        boolean transferCompleted = true;
        StringBuilder errorLog = new StringBuilder();
        BigDecimal actualTransferAmount = accountTransferDTO.getTransactionAmount();
        boolean isPartialPayment = false;

        StringBuilder updateQuery = new StringBuilder(
                "INSERT INTO m_account_transfer_standing_instructions_history (standing_instruction_id, " + sqlGenerator.escape("status")
                        + ", amount, execution_time, error_log) VALUES (");

        try {
            // First attempt: try the full transfer
            accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
            log.info("Standing Instruction {} executed successfully for full amount: {}", instructionId, actualTransferAmount);

        } catch (final InsufficientAccountBalanceException e) {
            // Handle insufficient balance with partial payment logic
            log.info("Insufficient balance for Standing Instruction {}. Attempting partial payment.", instructionId);

            try {
                // Get available balance from source account
                BigDecimal availableBalance = getAvailableBalance(accountTransferDTO.getFromAccountId(),
                        accountTransferDTO.getFromAccountType());

                if (availableBalance.compareTo(BigDecimal.ZERO) > 0) {
                    AccountTransferDTO partialTransferDTO = new AccountTransferDTO(accountTransferDTO.getTransactionDate(),
                            availableBalance, accountTransferDTO.getFromAccountType(), accountTransferDTO.getToAccountType(),
                            accountTransferDTO.getFromAccountId(), accountTransferDTO.getToAccountId(),
                            accountTransferDTO.getDescription() + " (Partial Payment)", accountTransferDTO.getLocale(),
                            accountTransferDTO.getFmt(), accountTransferDTO.getPaymentDetail(), accountTransferDTO.getFromTransferType(),
                            accountTransferDTO.getToTransferType(), accountTransferDTO.getChargeId(),
                            accountTransferDTO.getLoanInstallmentNumber(), accountTransferDTO.getTransferType(),
                            accountTransferDTO.getAccountTransferDetails(), accountTransferDTO.getNoteText(),
                            accountTransferDTO.getTxnExternalId(), accountTransferDTO.getLoan(), accountTransferDTO.getToSavingsAccount(),
                            accountTransferDTO.getFromSavingsAccount(), accountTransferDTO.isRegularTransaction(),
                            accountTransferDTO.isExceptionForBalanceCheck());

                    // Attempt partial transfer
                    accountTransfersWritePlatformService.transferFunds(partialTransferDTO);
                    actualTransferAmount = availableBalance;
                    isPartialPayment = true;

                    BigDecimal unpaidAmount = originalAmount.subtract(availableBalance);
                    log.info("Standing Instruction {} executed as partial payment. Paid: {}, Unpaid: {}", instructionId, availableBalance,
                            unpaidAmount);

                } else {
                    // No funds available at all
                    transferCompleted = false;
                    errors.add(new Exception("No funds available for Standing Instruction id " + instructionId + " from account "
                            + accountTransferDTO.getFromAccountId(), e));
                    errorLog.append("No funds available for transfer");
                }

            } catch (Exception partialTransferException) {
                // Partial transfer also failed
                transferCompleted = false;
                errors.add(new Exception(
                        "Partial payment failed for Standing Instruction id " + instructionId + " from "
                                + accountTransferDTO.getFromAccountId() + " to " + accountTransferDTO.getToAccountId(),
                        partialTransferException));
                errorLog.append("Partial payment failed: ").append(partialTransferException.getMessage());
            }

        } catch (final PlatformApiDataValidationException e) {
            transferCompleted = false;
            errors.add(new Exception("Validation exception while transferring funds for standing Instruction id" + instructionId + " from "
                    + accountTransferDTO.getFromAccountId() + " to " + accountTransferDTO.getToAccountId(), e));
            errorLog.append("Validation exception while transferring funds ").append(e.getDefaultUserMessage());

        } catch (final AbstractPlatformServiceUnavailableException e) {
            transferCompleted = false;
            errors.add(new Exception("Platform exception while transferring funds for standing Instruction id" + instructionId + " from "
                    + accountTransferDTO.getFromAccountId() + " to " + accountTransferDTO.getToAccountId(), e));
            errorLog.append("Platform exception while transferring funds ").append(e.getDefaultUserMessage());

        } catch (Exception e) {
            transferCompleted = false;
            errors.add(new Exception("Unhandled System Exception while transferring funds for standing Instruction id" + instructionId
                    + " from " + accountTransferDTO.getFromAccountId() + " to " + accountTransferDTO.getToAccountId(), e));
            errorLog.append("Exception while transferring funds ").append(e.getMessage());
        }

        // Log the transaction in history table
        updateQuery.append(instructionId).append(",");
        if (transferCompleted) {
            if (isPartialPayment) {
                updateQuery.append("'partial'").append(",");
            } else {
                updateQuery.append("'success'").append(",");
            }
        } else {
            updateQuery.append("'failed'").append(",");
        }
        updateQuery.append(actualTransferAmount.doubleValue());
        updateQuery.append(", now(),");
        updateQuery.append("'").append(errorLog).append("')");
        jdbcTemplate.update(updateQuery.toString());

        return transferCompleted;
    }

    /**
     * Fetches the available balance from the source account. Uses getWithdrawableBalance() for SAVINGS account type.
     */
    private BigDecimal getAvailableBalance(Long fromAccountId, PortfolioAccountType fromAccountType) {
        if (fromAccountType != null && fromAccountType.isSavingsAccount()) {
            try {
                SavingsAccount savingsAccount = savingsAccountRepositoryWrapper.findOneWithNotFoundDetection(fromAccountId);
                if (savingsAccount != null) {
                    return savingsAccount.getWithdrawableBalance();
                }
            } catch (SavingsAccountNotFoundException ex) {
                log.warn("Savings account not found for id {}", fromAccountId);
            }
        }
        return BigDecimal.ZERO;
    }
}
