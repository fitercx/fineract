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

import com.crediblex.fineract.commands.CredXSynchronousCommandProcessingService;
import com.crediblex.fineract.commands.LineOfCreditStatusWebhookPublisher;
import com.crediblex.fineract.commands.LoanStatusWebhookPublisher;
import com.crediblex.fineract.infrastructure.commands.utils.LoanTransactionInstallmentUtils;
import com.crediblex.fineract.portfolio.account.repository.EzySqlLoanLocRepository;
import com.crediblex.fineract.portfolio.loanaccount.data.LocStatusAggregationData;
import com.crediblex.fineract.portfolio.loanaccount.util.LocStatusAggregationUtils;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepository;
import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformServiceUnavailableException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
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
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ScheduledDateGenerator;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.exception.InsufficientAccountBalanceException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class CustomExecuteStandingInstructionsTasklet extends ExecuteStandingInstructionsTasklet implements ApplicationContextAware {

    private static final String DATE_FORMAT = "dd MMMM yyyy";

    private final StandingInstructionReadPlatformService standingInstructionReadPlatformService;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final AccountTransfersWritePlatformService accountTransfersWritePlatformService;
    private final SavingsAccountAssembler savingsAccountAssembler;
    private final PlatformTransactionManager transactionManager;
    private final CredXSynchronousCommandProcessingService customCommandProcessingService;
    private final FromJsonHelper fromApiJsonHelper;
    private ApplicationContext applicationContext;
    private LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanStatusWebhookPublisher loanStatusWebhookPublisher;
    private final TransactionTemplate transactionTemplate;
    private final EzySqlLoanLocRepository ezySqlLoanLocRepository;
    private final LineOfCreditStatusWebhookPublisher lineOfCreditStatusWebhookPublisher;

    @Autowired(required = false)
    private LineOfCreditRepository lineOfCreditRepository;
    @Autowired(required = false)
    private LocStatusAggregationUtils locStatusAggregationUtils;

    public CustomExecuteStandingInstructionsTasklet(StandingInstructionReadPlatformService standingInstructionReadPlatformService,
            JdbcTemplate jdbcTemplate, DatabaseSpecificSQLGenerator sqlGenerator,
            AccountTransfersWritePlatformService accountTransfersWritePlatformService, SavingsAccountAssembler savingsAccountAssembler,
            PlatformTransactionManager transactionManager, CredXSynchronousCommandProcessingService customCommandProcessingService,
            FromJsonHelper fromApiJsonHelper, LoanStatusWebhookPublisher loanStatusWebhookPublisher,
            TransactionTemplate transactionTemplate, EzySqlLoanLocRepository ezySqlLoanLocRepository,
            LineOfCreditStatusWebhookPublisher lineOfCreditStatusWebhookPublisher) {

        super(standingInstructionReadPlatformService, jdbcTemplate, sqlGenerator, accountTransfersWritePlatformService);
        this.standingInstructionReadPlatformService = standingInstructionReadPlatformService;
        this.jdbcTemplate = jdbcTemplate;
        this.sqlGenerator = sqlGenerator;
        this.accountTransfersWritePlatformService = accountTransfersWritePlatformService;
        this.transactionManager = transactionManager;
        this.savingsAccountAssembler = savingsAccountAssembler;
        this.customCommandProcessingService = customCommandProcessingService;
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.loanStatusWebhookPublisher = loanStatusWebhookPublisher;
        this.transactionTemplate = transactionTemplate;
        this.ezySqlLoanLocRepository = ezySqlLoanLocRepository;
        this.lineOfCreditStatusWebhookPublisher = lineOfCreditStatusWebhookPublisher;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        // Get LoanRepositoryWrapper from Spring context when available
        this.loanRepositoryWrapper = applicationContext.getBean(LoanRepositoryWrapper.class);
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

                            // Begin: Custom loan status + LOC aggregation + publish (moved from
                            // triggerRepaymentWebhook)
                            try {
                                Long loanId = data.getToAccount().getId();
                                Loan loan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);

                                // Find the most recent repayment transaction matching amount/date
                                LoanTransaction recentTransaction = loan.getLoanTransactions().stream()
                                        .filter(t -> t.isRepayment() && t.getAmount().compareTo(finalTransactionAmount) == 0
                                                && t.getTransactionDate().equals(DateUtils.getBusinessLocalDate()))
                                        .max((t1, t2) -> t1.getId().compareTo(t2.getId())).orElse(null);

                                if (recentTransaction != null) {
                                    // Capture custom old status before update
                                    CustomLoanStatus oldCustomStatus = loan.hasCustomStatus() ? loan.getCustomLoanStatus() : null;

                                    // Compute and update the custom loan status based on affected installments
                                    CustomLoanStatus customLoanStatus = LoanTransactionInstallmentUtils
                                            .computeCustomLoanStatusForLoan(loan);
                                    loan.setCustomLoanStatus(customLoanStatus);

                                    // Precompute drawdown flags before commit using LOC lookup repository
                                    boolean isDrawdown = ezySqlLoanLocRepository.existsByLoanId(loan.getId());
                                    Optional<Long> locIdOpt = ezySqlLoanLocRepository.findLocIdByLoanId(loan.getId());

                                    // If drawdown, compute LOC status aggregation data and persist only if changed
                                    LocStatusAggregationData locStatusAggregationData;
                                    if (isDrawdown && locIdOpt.isPresent() && lineOfCreditRepository != null
                                            && locStatusAggregationUtils != null) {
                                        Optional<LineOfCredit> locOpt = lineOfCreditRepository.findById(locIdOpt.get());
                                        if (locOpt.isPresent()) {
                                            LineOfCredit loc = locOpt.get();
                                            locStatusAggregationData = this.locStatusAggregationUtils.computeLocStatusAggregationData(loc,
                                                    loan);
                                            if (locStatusAggregationData != null) {
                                                lineOfCreditRepository.save(loc);
                                            }
                                        } else {
                                            locStatusAggregationData = null;
                                        }
                                    } else {
                                        locStatusAggregationData = null;
                                    }

                                    // Schedule webhook publish after successful commit, in a new transaction
                                    final LocStatusAggregationData finalLocStatusAggregationData = locStatusAggregationData;
                                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                                        @Override
                                        public void afterCommit() {
                                            transactionTemplate.execute(innerStatus -> {
                                                loanStatusWebhookPublisher.publish(loan, oldCustomStatus, isDrawdown, locIdOpt);
                                                if (finalLocStatusAggregationData != null) {
                                                    lineOfCreditStatusWebhookPublisher.publish(loan,
                                                            finalLocStatusAggregationData.getDefaultLocStatus().name(),
                                                            finalLocStatusAggregationData.getOldLocCustomStatus().name(),
                                                            finalLocStatusAggregationData.getNewLocCustomStatus().name(), isDrawdown,
                                                            locIdOpt);
                                                }
                                                return null;
                                            });
                                        }
                                    });
                                } else {
                                    log.warn("Could not find recent repayment transaction for standing instruction {} with amount {}",
                                            data.getId(), finalTransactionAmount);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to compute/publish loan/LOC status for standing instruction {}: {}", data.getId(),
                                        e.getMessage());
                            }
                            // End: Custom loan status + LOC aggregation + publish
                        } else {
                            failureCount.getAndIncrement();
                        }
                        // Trigger repayment webhook after successful transfer
                        triggerRepaymentWebhook(data, finalTransactionAmount);
                        return null;
                    });
                    log.debug("Successfully processed standing instruction {} for amount {}", data.getId(), transactionAmount);
                } catch (Exception e) {
                    failureCount.getAndIncrement();
                    errors.add(e);
                }
                processedCount++;
            }
        }

        log.info("Standing instructions processing completed. Processed: {}, Success: {}, Failed: {}, Due: {}", processedCount,
                successCount, failureCount, dueCount);

        if (!errors.isEmpty()) {
            // Log all errors with context before throwing
            log.error("Standing instruction job completed with {} error(s). Details:", errors.size());
            for (int i = 0; i < errors.size(); i++) {
                Throwable error = errors.get(i);
                log.error("Error {} of {}:", i + 1, errors.size(), error);
            }

            // Create a more informative exception with summary using text block
            String summaryMessage = """
                    Standing instruction job completed with %d error(s) out of %d processed instructions.
                    Success: %d, Failed: %d. See error details above.
                    """.formatted(errors.size(), processedCount, successCount.get(), failureCount.get());

            // Wrap errors in JobExecutionException with better context
            // JobExecutionException requires both message and list of throwables
            throw new JobExecutionException(summaryMessage, errors);
        }
        return RepeatStatus.FINISHED;
    }

    /**
     * Enhanced transfer method that supports partial payments when insufficient balance is available
     */
    private boolean transferAmountWithPartialPaymentSupport(final List<Throwable> errors, final AccountTransferDTO accountTransferDTO,
            final Long instructionId, final BigDecimal originalAmount) {
        boolean transferCompleted = false;
        boolean isPartialPayment = false;

        BigDecimal availableBalance = getAvailableBalance(accountTransferDTO.getFromAccountId());
        if (availableBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return transferCompleted;
        }

        BigDecimal amount = accountTransferDTO.getTransactionAmount();
        if (amount.compareTo(availableBalance) > 0) {
            amount = availableBalance;
            isPartialPayment = true;
        }

        AccountTransferDTO updatedDTO = new AccountTransferDTO(accountTransferDTO.getTransactionDate(), amount,
                accountTransferDTO.getFromAccountType(), accountTransferDTO.getToAccountType(), accountTransferDTO.getFromAccountId(),
                accountTransferDTO.getToAccountId(), accountTransferDTO.getDescription() + (isPartialPayment ? " (Partial Payment)" : ""),
                accountTransferDTO.getLocale(), accountTransferDTO.getFmt(), accountTransferDTO.getPaymentDetail(),
                accountTransferDTO.getFromTransferType(), accountTransferDTO.getToTransferType(), accountTransferDTO.getChargeId(),
                accountTransferDTO.getLoanInstallmentNumber(), accountTransferDTO.getTransferType(),
                accountTransferDTO.getAccountTransferDetails(), accountTransferDTO.getNoteText(), accountTransferDTO.getTxnExternalId(),
                accountTransferDTO.getLoan(), accountTransferDTO.getToSavingsAccount(), accountTransferDTO.getFromSavingsAccount(),
                accountTransferDTO.isRegularTransaction(), accountTransferDTO.isExceptionForBalanceCheck());

        transferCompleted = true;
        StringBuilder errorLog = new StringBuilder();
        StringBuilder updateQuery = new StringBuilder(
                "INSERT INTO m_account_transfer_standing_instructions_history (standing_instruction_id, " + sqlGenerator.escape("status")
                        + ", amount, execution_time, error_log) VALUES (");

        try {
            accountTransfersWritePlatformService.transferFunds(updatedDTO);
            if (isPartialPayment) {
                BigDecimal unpaidAmount = originalAmount.subtract(amount);
                errorLog.append("Partial payment executed. Paid: ").append(amount).append(", Unpaid: ").append(unpaidAmount);
            }
        } catch (final PlatformApiDataValidationException e) {
            transferCompleted = false;

            // Enhanced error context for LOC-related validation failures
            String errorMessage = e.getDefaultUserMessage();
            boolean isLocError = errorMessage != null && (errorMessage.contains("Consumed amount cannot be negative")
                    || errorMessage.contains("LOC balance") || errorMessage.contains("loc.balance"));

            String contextualError;
            if (isLocError) {
                contextualError = """
                        LOC balance validation failed for standing Instruction id %d from account %d to account %d.
                        Amount: %s, Error: %s
                        This may indicate a data inconsistency that requires reconciliation.
                        """.formatted(instructionId, accountTransferDTO.getFromAccountId(), accountTransferDTO.getToAccountId(), amount,
                        errorMessage);
                log.warn("LOC balance validation error for standing instruction {}: {}", instructionId, errorMessage, e);
            } else {
                contextualError = """
                        Validation exception while transferring funds for standing Instruction id %d from account %d to account %d.
                        Amount: %s, Error: %s
                        """.formatted(instructionId, accountTransferDTO.getFromAccountId(), accountTransferDTO.getToAccountId(), amount,
                        errorMessage);
                log.error("Validation error for standing instruction {}: {}", instructionId, errorMessage, e);
            }

            errors.add(new Exception(contextualError, e));
            errorLog.append("Validation exception: ").append(errorMessage);

        } catch (final InsufficientAccountBalanceException e) {
            transferCompleted = false;
            errorLog.append("Insufficient balance for Standing Instruction " + instructionId + ". Attempted "
                    + (isPartialPayment ? "partial" : "full") + " payment, from " + accountTransferDTO.getFromAccountId() + " to "
                    + accountTransferDTO.getToAccountId() + ". ");
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
        updateQuery.append(amount.doubleValue());
        updateQuery.append(", now(),");
        updateQuery.append("'").append(errorLog).append("')");
        jdbcTemplate.update(updateQuery.toString());

        return transferCompleted;
    }

    /**
     * Fetches the available balance from the source account. Uses getWithdrawableBalance() for SAVINGS account type.
     */
    public BigDecimal getAvailableBalance(Long savingsAccountId) {
        SavingsAccount account = savingsAccountAssembler.assembleFrom(savingsAccountId, false);
        return account.getWithdrawableBalance();
    }

    private void triggerRepaymentWebhook(StandingInstructionData data, BigDecimal transactionAmount) {
        try {
            // Format current date in the required format (dd MMMM yyyy)
            String formattedDate = DateUtils.getBusinessLocalDate().format(java.time.format.DateTimeFormatter.ofPattern(DATE_FORMAT));

            // Construct the request part of the webhook payload
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("note", "");
            requestMap.put("paymentTypeId", "");
            requestMap.put("dateFormat", DATE_FORMAT);
            requestMap.put("transactionAmount", transactionAmount);
            requestMap.put("externalId", "");
            requestMap.put("locale", "en");
            requestMap.put("transactionDate", formattedDate);

            // Construct the response part of the webhook payload
            Map<String, Object> changes = new HashMap<>();
            changes.put("dateFormat", DATE_FORMAT);
            changes.put("transactionAmount", transactionAmount.toString());
            changes.put("locale", "en");
            changes.put("transactionDate", formattedDate);
            changes.put("isDrawdown", Boolean.FALSE);

            // Follow the EXACT SAME pattern as UI repayment - find the actual transaction and use its mappings
            Long loanId = data.getToAccount().getId();
            try {
                // After a successful transfer, the system creates a loan transaction
                // We need to find this transaction and use its mappings (same as UI repayment)
                Loan loan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);

                // Find the most recent repayment transaction that matches our criteria
                // We look for the transaction by amount and date (similar to how UI gets the resourceId)
                LoanTransaction recentTransaction = loan.getLoanTransactions().stream()
                        .filter(t -> t.isRepayment() && t.getAmount().compareTo(transactionAmount) == 0
                                && t.getTransactionDate().equals(DateUtils.getBusinessLocalDate()))
                        .max((t1, t2) -> t1.getId().compareTo(t2.getId())).orElse(null);

                if (recentTransaction != null) {
                    // Extract affected installments using the shared utility method (same logic as UI repayment)
                    List<Map<String, Object>> affectedInstallments = LoanTransactionInstallmentUtils.extractAffectedInstallments(loan,
                            recentTransaction);

                    // Add affected installments and transaction details - SAME AS UI
                    if (!affectedInstallments.isEmpty()) {
                        changes.put("affectedInstallments", affectedInstallments);
                    }
                    changes.put("transactionId", recentTransaction.getId());
                } else {
                    log.warn("Could not find recent repayment transaction for standing instruction {} with amount {}", data.getId(),
                            transactionAmount);
                }

            } catch (Exception e) {
                log.warn("Failed to get affected installments from actual transaction for standing instruction {}: {}", data.getId(),
                        e.getMessage());
            }

            // Get client and office IDs
            Long clientId = (data.getToClient() != null) ? data.getToClient().getId() : 0L;
            Long officeId = 1L; // Default
            if (data.getToClient() != null && data.getToClient().getOfficeId() != null) {
                officeId = data.getToClient().getOfficeId();
            }

            // Convert the map to JSON string using Gson
            String jsonCommandString = new com.google.gson.Gson().toJson(requestMap);
            JsonElement parsedCommand = this.fromApiJsonHelper.parse(jsonCommandString);

            // Use JsonCommand.from() with complete payload
            Long commandClientId = (data.getToClient() != null) ? data.getToClient().getId() : null;

            JsonCommand command = JsonCommand.from(jsonCommandString, parsedCommand, fromApiJsonHelper, "LOAN", loanId, null, // subresourceId
                    null, // groupId
                    commandClientId, // clientId
                    loanId, // loanId
                    null, // savingsId
                    null, // transactionId
                    null, // url
                    null, // productId
                    null, // creditBureauId
                    null, // organisationCreditBureauId
                    "ExecuteStandingInstructions", // jobName
                    ExternalId.empty() // loanExternalId
            );

            CommandProcessingResult result = CommandProcessingResult.fromDetails(null, // commandId
                    officeId, null, // groupId
                    clientId, loanId, // loanId,
                    null, // savingsId
                    loanId.toString(), // resourceIdentifier
                    loanId, null, // gsimId
                    null, // glimId
                    null, // creditBureauReportData
                    null, // transactionId
                    changes, null, // productId
                    null, // rollbackTransaction
                    null, // subResourceId
                    ExternalId.empty(), // resourceExternalId
                    ExternalId.empty(), // subResourceExternalId
                    ExternalId.empty() // loanExternalId
            );

            // Pass the full command as the result object as well to match UI webhook structure
            customCommandProcessingService.publishHookEvent("LOAN", "REPAYMENT", command, result);
            log.info("Repayment webhook triggered for standing instruction ID: {} with amount {}", data.getId(), transactionAmount);
        } catch (Exception e) {
            log.error("Failed to trigger repayment webhook for standing instruction ID: {}", data.getId(), e);
        }
    }
}
