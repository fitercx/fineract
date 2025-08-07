/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.organisation.holiday.service;

import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.dateFormatParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.descriptionParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.fromDateParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.localeParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.nameParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.officesParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.repaymentsRescheduledToParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.toDateParamName;
import static org.apache.fineract.infrastructure.core.service.DateUtils.isDateWithinRange;

import com.google.gson.JsonArray;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.config.TaskExecutorConstant;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.api.HolidayApiConstants;
import org.apache.fineract.organisation.holiday.data.HolidayDataValidator;
import org.apache.fineract.organisation.holiday.domain.Holiday;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.holiday.domain.RescheduleType;
import org.apache.fineract.organisation.holiday.service.HolidayWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.holiday.domain.HolidayStatusType;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTermVariationsMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CredibleX implementation of {@link HolidayWritePlatformServiceJpaRepositoryImpl} that allows editing active holidays
 * by bypassing the validation restrictions and recalculating affected loan schedules.
 */
@Slf4j
@Primary
public class CredibleXHolidayWritePlatformServiceJpaRepositoryImpl extends HolidayWritePlatformServiceJpaRepositoryImpl {

    private final HolidayDataValidator fromApiJsonDeserializer;
    private final HolidayRepositoryWrapper holidayRepository;
    private final WorkingDaysRepositoryWrapper daysRepositoryWrapper;
    private final PlatformSecurityContext context;
    private final OfficeRepositoryWrapper officeRepositoryWrapper;
    private final FromJsonHelper fromApiJsonHelper;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanScheduleService loanScheduleService;
    private final LoanUtilService loanUtilService;
    private final ConfigurationDomainService configurationDomainService;
    private final LoanTermVariationsMapper loanTermVariationsMapper;

    @Qualifier(TaskExecutorConstant.CONFIGURABLE_TASK_EXECUTOR_BEAN_NAME)
    private final ThreadPoolTaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public CredibleXHolidayWritePlatformServiceJpaRepositoryImpl(HolidayDataValidator fromApiJsonDeserializer,
                                                                 HolidayRepositoryWrapper holidayRepository, WorkingDaysRepositoryWrapper daysRepositoryWrapper, PlatformSecurityContext context,
                                                                 OfficeRepositoryWrapper officeRepositoryWrapper, FromJsonHelper fromApiJsonHelper, LoanRepositoryWrapper loanRepositoryWrapper,
                                                                 LoanScheduleService loanScheduleService, LoanUtilService loanUtilService,
                                                                 ConfigurationDomainService configurationDomainService, LoanTermVariationsMapper loanTermVariationsMapper,
                                                                 ThreadPoolTaskExecutor taskExecutor, TransactionTemplate transactionTemplate) {
        super(fromApiJsonDeserializer, holidayRepository, daysRepositoryWrapper, context, officeRepositoryWrapper, fromApiJsonHelper);
        this.fromApiJsonDeserializer = fromApiJsonDeserializer;
        this.holidayRepository = holidayRepository;
        this.daysRepositoryWrapper = daysRepositoryWrapper;
        this.context = context;
        this.officeRepositoryWrapper = officeRepositoryWrapper;
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.loanRepositoryWrapper = loanRepositoryWrapper;
        this.loanScheduleService = loanScheduleService;
        this.loanUtilService = loanUtilService;
        this.configurationDomainService = configurationDomainService;
        this.loanTermVariationsMapper = loanTermVariationsMapper;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    @Override
    public CommandProcessingResult updateHoliday(final JsonCommand command) {
        try {
            this.context.authenticatedUser();

            final Holiday holiday = this.holidayRepository.findOneWithNotFoundDetection(command.entityId());

            // Store original dates to identify affected loans
            final LocalDate originalFromDate = holiday.getFromDate();
            final LocalDate originalToDate = holiday.getToDate();

            validateInputDates(holiday.getFromDate(), holiday.getToDate(), holiday.getRepaymentsRescheduledTo());

            // Custom update logic that bypasses the active state restriction
            Map<String, Object> changes = updateHolidayBypassingActiveStateRestriction(holiday, command);

            // Handle office updates directly without calling the original holiday.update(offices) method
            if (changes.containsKey(officesParamName)) {
                final Set<Office> offices = getSelectedOffices(command);
                final boolean updated = updateHolidayOffices(holiday, offices);
                if (!updated) {
                    changes.remove(officesParamName);
                }
            }

            if (!changes.isEmpty()) {
                this.holidayRepository.saveAndFlush(holiday);

                // Recalculate schedules for affected loans
                try {
                    recalculateAffectedLoanSchedules(holiday, originalFromDate, originalToDate, changes);
                } catch (JobExecutionException e) {
                    log.error("Failed to recalculate loan schedules due to holiday changes", e);
                }
            }

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(holiday.getId()).with(changes)
                    .build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            final Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            throw new PlatformDataIntegrityException("error.msg.holiday.unknown.data.integrity.issue",
                    "Unknown data integrity issue with resource: " + throwable.getMessage(), dve);
        }
    }

    /**
     * Recalculate schedules for loans affected by holiday date changes using pagination and threading
     */
    private void recalculateAffectedLoanSchedules(Holiday holiday, LocalDate originalFromDate, LocalDate originalToDate,
                                                  Map<String, Object> changes) throws JobExecutionException {

        // Check if holiday dates or reschedule date were changed
        boolean fromDateChanged = changes.containsKey(fromDateParamName);
        boolean toDateChanged = changes.containsKey(toDateParamName);
        boolean repaymentsRescheduledToChanged = changes.containsKey(repaymentsRescheduledToParamName);

        if (!fromDateChanged && !toDateChanged && !repaymentsRescheduledToChanged) {
            return;
        }

        // Get the new holiday dates
        LocalDate newFromDate = holiday.getFromDate();
        LocalDate newToDate = holiday.getToDate();

        // Process loans affected by either the original or new holiday date ranges
        // This ensures we catch all loans that might be affected by the change
        Set<Office> offices = holiday.getOffices();
        
        // First, process loans affected by the original date range
        if (fromDateChanged || toDateChanged) {
            processAffectedLoansWithPagination(offices, originalFromDate, originalToDate, holiday);
        }
        
        // Then, process loans affected by the new date range (if different)
        if ((fromDateChanged || toDateChanged) && 
            (!originalFromDate.equals(newFromDate) || !originalToDate.equals(newToDate))) {
            processAffectedLoansWithPagination(offices, newFromDate, newToDate, holiday);
        }
        
        // If only the reschedule date changed, process loans affected by the holiday date range
        if (repaymentsRescheduledToChanged && !fromDateChanged && !toDateChanged) {
            processAffectedLoansWithPagination(offices, newFromDate, newToDate, holiday);
        }
    }

    /**
     * Process affected loans using pagination and threading to avoid memory issues and transaction deadlocks
     */
    private void processAffectedLoansWithPagination(Set<Office> offices, LocalDate fromDate, LocalDate toDate, Holiday holiday) throws JobExecutionException {
        if (offices == null || offices.isEmpty()) {
            return;
        }

        // Configure thread pool for this operation
        int threadPoolSize = 4; // Default thread pool size
        int batchSize = 50; // Default batch size per thread
        final int pageSize = batchSize * threadPoolSize;
        
        taskExecutor.setCorePoolSize(threadPoolSize);
        taskExecutor.setMaxPoolSize(threadPoolSize);

        Long maxLoanIdInList = 0L;
        List<Long> loanIds = findLoanIdsAffectedByHolidayDates(offices, fromDate, toDate, pageSize, maxLoanIdInList);

        do {
            int totalFilteredRecords = loanIds.size();
            log.debug("Starting holiday schedule recalculation - total filtered records - {}", totalFilteredRecords);
            
            if (!loanIds.isEmpty()) {
                processLoanBatch(loanIds, threadPoolSize, fromDate, toDate, holiday);
            }
            
            maxLoanIdInList += pageSize + 1;
            loanIds = findLoanIdsAffectedByHolidayDates(offices, fromDate, toDate, pageSize, maxLoanIdInList);
        } while (!loanIds.isEmpty());
    }

    /**
     * Find loan IDs affected by holiday dates using pagination
     */
    private List<Long> findLoanIdsAffectedByHolidayDates(Set<Office> offices, LocalDate fromDate, LocalDate toDate, 
                                                        Integer pageSize, Long maxLoanIdInList) {
        return findLoanIdsAffectedByHolidayDates(offices, fromDate, toDate, pageSize, maxLoanIdInList, null);
    }

    /**
     * Find loan IDs affected by holiday dates using pagination (with reschedule date for deletion)
     */
    private List<Long> findLoanIdsAffectedByHolidayDates(Set<Office> offices, LocalDate fromDate, LocalDate toDate, 
                                                        Integer pageSize, Long maxLoanIdInList, LocalDate repaymentsRescheduledTo) {
        // Get office IDs
        Collection<Long> officeIds = new ArrayList<>();
        for (Office office : offices) {
            officeIds.add(office.getId());
        }

        // Define loan statuses to consider
        Collection<LoanStatus> loanStatuses = new ArrayList<>(
                Arrays.asList(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL, LoanStatus.APPROVED, LoanStatus.ACTIVE));

        // Find loans by office (using existing methods)
        List<Loan> allLoans = new ArrayList<>();
        allLoans.addAll(loanRepositoryWrapper.findByClientOfficeIdsAndLoanStatus(officeIds, loanStatuses));
        allLoans.addAll(loanRepositoryWrapper.findByGroupOfficeIdsAndLoanStatus(officeIds, loanStatuses));

        // Apply pagination manually since the repository methods don't support it
        List<Loan> paginatedLoans = allLoans.stream()
                .filter(loan -> loan.getId() > maxLoanIdInList)
                .limit(pageSize)
                .toList();

        // Filter loans that have repayment schedules overlapping with the holiday date range
        List<Long> affectedLoanIds = new ArrayList<>();
        for (Loan loan : paginatedLoans) {
            boolean isAffected = false;
            
            if (repaymentsRescheduledTo != null) {
                // For deletion, use the comprehensive check
                isAffected = hasRepaymentScheduleAffectedByHolidayDeletion(loan, fromDate, toDate, repaymentsRescheduledTo);
            } else {
                // For updates, use the original check
                isAffected = hasRepaymentScheduleOverlapWithHoliday(loan, fromDate, toDate);
            }
            
            if (isAffected) {
                affectedLoanIds.add(loan.getId());
            }
        }

        return affectedLoanIds;
    }

    /**
     * Process a batch of loans using threading
     */
    private void processLoanBatch(List<Long> loanIds, int threadPoolSize, LocalDate fromDate, LocalDate toDate, Holiday holiday) throws JobExecutionException {
        if (loanIds == null || loanIds.isEmpty()) {
            return;
        }

        int actualBatchSize = (int) Math.ceil(loanIds.size() / (double) threadPoolSize);
        List<List<Long>> batches = new ArrayList<>();
        
        for (int i = 0; i < loanIds.size(); i += actualBatchSize) {
            int end = Math.min(i + actualBatchSize, loanIds.size());
            batches.add(loanIds.subList(i, end));
        }

        List<Future<?>> loanTasks = new ArrayList<>();
        FineractContext context = ThreadLocalContextUtil.getContext();

        for (List<Long> batch : batches) {
            if (!batch.isEmpty()) {
                loanTasks.add(taskExecutor.submit(() -> {
                    try {
                        ThreadLocalContextUtil.init(context);
                        for (Long loanId : batch) {
                            transactionTemplate.executeWithoutResult(status -> {
                                try {
                                    log.debug("Applying holiday changes to loan '{}'", loanId);
                                    Loan loan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
                                    applyHolidayChangesToLoan(loan, holiday);
                                    log.debug("Successfully applied holiday changes to loan: '{}'", loanId);
                                } catch (Exception e) {
                                    log.error("Failed to recalculate schedule for loan {}", loanId, e);
                                    throw new RuntimeException("Failed to recalculate schedule for loan " + loanId, e);
                                }
                            });
                        }
                    } finally {
                        ThreadLocalContextUtil.reset();
                    }
                }));
            }
        }

        // Wait for all tasks to complete
        List<Throwable> errors = new ArrayList<>();
        for (Future<?> task : loanTasks) {
            try {
                task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errors.add(e);
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        
        if (!errors.isEmpty()) {
            throw new JobExecutionException(errors);
        }
    }

    /**
     * Process affected loans for holiday deletion using pagination and threading
     */
    private void processAffectedLoansForDeletion(Set<Office> offices, LocalDate fromDate, LocalDate toDate, LocalDate repaymentsRescheduledTo) throws JobExecutionException {
        if (offices == null || offices.isEmpty()) {
            return;
        }

        // Configure thread pool for this operation
        int threadPoolSize = 4;
        int batchSize = 50;
        final int pageSize = batchSize * threadPoolSize;
        
        taskExecutor.setCorePoolSize(threadPoolSize);
        taskExecutor.setMaxPoolSize(threadPoolSize);

        Long maxLoanIdInList = 0L;
        List<Long> loanIds = findLoanIdsAffectedByHolidayDates(offices, fromDate, toDate, pageSize, maxLoanIdInList, repaymentsRescheduledTo);

        do {
            if (!loanIds.isEmpty()) {
                processLoanBatchForHolidayDeletion(loanIds, threadPoolSize, fromDate, toDate, repaymentsRescheduledTo);
            }
            
            maxLoanIdInList += pageSize + 1;
            loanIds = findLoanIdsAffectedByHolidayDates(offices, fromDate, toDate, pageSize, maxLoanIdInList, repaymentsRescheduledTo);
        } while (!loanIds.isEmpty());
    }

    /**
     * Process a batch of loans for holiday deletion using threading
     */
    private void processLoanBatchForHolidayDeletion(List<Long> loanIds, int threadPoolSize, LocalDate fromDate, LocalDate toDate, LocalDate repaymentsRescheduledTo) throws JobExecutionException {
        if (loanIds == null || loanIds.isEmpty()) {
            return;
        }

        int actualBatchSize = (int) Math.ceil(loanIds.size() / (double) threadPoolSize);
        List<List<Long>> batches = new ArrayList<>();
        
        for (int i = 0; i < loanIds.size(); i += actualBatchSize) {
            int end = Math.min(i + actualBatchSize, loanIds.size());
            batches.add(loanIds.subList(i, end));
        }

        List<Future<?>> loanTasks = new ArrayList<>();
        FineractContext context = ThreadLocalContextUtil.getContext();

        for (List<Long> batch : batches) {
            if (!batch.isEmpty()) {
                loanTasks.add(taskExecutor.submit(() -> {
                    try {
                        ThreadLocalContextUtil.init(context);
                        for (Long loanId : batch) {
                            transactionTemplate.executeWithoutResult(status -> {
                                try {
                                    Loan loan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
                                    int restoredForThisLoan = restoreInstallmentsDirectly(loan, fromDate, toDate, repaymentsRescheduledTo);
                                    if (restoredForThisLoan > 0) {
                                        loanRepositoryWrapper.saveAndFlush(loan);
                                    }
                                } catch (Exception e) {
                                    throw new RuntimeException("Failed to process loan " + loanId + " for holiday deletion", e);
                                }
                            });
                        }
                    } finally {
                        ThreadLocalContextUtil.reset();
                    }
                }));
            }
        }

        // Wait for all tasks to complete
        List<Throwable> errors = new ArrayList<>();
        for (Future<?> task : loanTasks) {
            try {
                task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errors.add(e);
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        
        if (!errors.isEmpty()) {
            throw new JobExecutionException(errors);
        }
    }

    /**
     * Check if a loan has repayment schedules that overlap with the holiday date range
     */
    private boolean hasRepaymentScheduleOverlapWithHoliday(Loan loan, LocalDate holidayFromDate, LocalDate holidayToDate) {
        if (loan.getRepaymentScheduleInstallments() == null) {
            return false;
        }

        // Check if any installment has a due date that falls within the holiday period
        for (var installment : loan.getRepaymentScheduleInstallments()) {
            LocalDate dueDate = installment.getDueDate();
            if (dueDate != null && !dueDate.isBefore(holidayFromDate) && !dueDate.isAfter(holidayToDate)) {
                // Only consider unpaid installments or installments that are not fully paid off
                if (installment.isNotFullyPaidOff()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a loan has repayment schedules that might be affected by holiday deletion
     * This includes both loans with installments in the holiday date range and loans with installments
     * on the rescheduling date
     */
    private boolean hasRepaymentScheduleAffectedByHolidayDeletion(Loan loan, LocalDate holidayFromDate, LocalDate holidayToDate, LocalDate repaymentsRescheduledTo) {
        if (loan.getRepaymentScheduleInstallments() == null) {
            return false;
        }

        for (var installment : loan.getRepaymentScheduleInstallments()) {
            LocalDate dueDate = installment.getDueDate();
            if (dueDate != null && installment.isNotFullyPaidOff()) {
                
                // Case 1: Installment due date is within the holiday period
                if (!dueDate.isBefore(holidayFromDate) && !dueDate.isAfter(holidayToDate)) {
                    return true;
                }
                
                // Case 2: Installment due date matches the rescheduling date
                if (repaymentsRescheduledTo != null && dueDate.equals(repaymentsRescheduledTo)) {
                    return true;
                }
                
                // Case 3: For "reschedule to next repayment date" type, check if installment is after holiday
                if (repaymentsRescheduledTo == null && dueDate.isAfter(holidayToDate)) {
                    long daysDifference = ChronoUnit.DAYS.between(holidayToDate, dueDate);
                    if (daysDifference <= 60) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Apply holiday changes directly to loan installments (similar to ApplyHolidaysToLoansTasklet)
     */
    private void applyHolidayChangesToLoan(Loan loan, Holiday holiday) {
        try {
            LocalDate adjustedRescheduleToDate = null;
            boolean isResheduleToNextRepaymentDate = holiday.getReScheduleType().isResheduleToNextRepaymentDate();
            
            if (holiday.getReScheduleType().isResheduleToNextRepaymentDate()) {
                adjustedRescheduleToDate = getNextRepaymentDate(loan, holiday);
            } else {
                adjustedRescheduleToDate = holiday.getRepaymentsRescheduledTo();
            }

            if (isRepaymentScheduleAdjustmentNeeded(adjustedRescheduleToDate)) {
                if (isResheduleToNextRepaymentDate) {
                    adjustAllRepaymentSchedules(loan, holiday, adjustedRescheduleToDate);
                } else {
                    adjustRepaymentSchedules(loan, holiday, adjustedRescheduleToDate);
                }
                log.debug("Applied holiday changes to loan: {}", loan.getId());
            }

            // Fix from dates to ensure consistency
            fixFromDates(loan);

            // Save the loan
            loanRepositoryWrapper.saveAndFlush(loan);

        } catch (Exception e) {
            log.error("Failed to apply holiday changes to loan: {}", loan.getId(), e);
            throw e;
        }
    }

    /**
     * Check if repayment schedule adjustment is needed
     */
    private boolean isRepaymentScheduleAdjustmentNeeded(LocalDate adjustedRescheduleToDate) {
        return adjustedRescheduleToDate != null;
    }

    /**
     * Adjust repayment schedules for specific reschedule date type
     */
    private void adjustRepaymentSchedules(Loan loan, Holiday holiday, LocalDate adjustedRescheduleToDate) {
        final DefaultScheduledDateGenerator scheduledDateGenerator = new DefaultScheduledDateGenerator();
        ScheduleGeneratorDTO scheduleGeneratorDTO = loanUtilService.buildScheduleGeneratorDTO(loan, holiday.getFromDate());
        final LoanApplicationTerms loanApplicationTerms = loanTermVariationsMapper.constructLoanApplicationTerms(scheduleGeneratorDTO, loan);

        // first repayment's from date is same as disbursement date.
        LocalDate tmpFromDate = loan.getDisbursementDate();

        // Loop through all loanRepayments
        List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        for (final LoanRepaymentScheduleInstallment loanRepaymentScheduleInstallment : installments) {
            final LocalDate oldDueDate = loanRepaymentScheduleInstallment.getDueDate();

            // update from date if it's not same as previous installment's due date.
            if (!DateUtils.isEqual(tmpFromDate, loanRepaymentScheduleInstallment.getFromDate())) {
                loanRepaymentScheduleInstallment.updateFromDate(tmpFromDate);
            }

            if (isDateWithinRange(oldDueDate, holiday.getFromDate(), holiday.getToDate())) {
                // Only adjust unpaid installments
                if (loanRepaymentScheduleInstallment.isNotFullyPaidOff()) {
                    adjustedRescheduleToDate = scheduledDateGenerator.generateNextRepaymentDateWhenHolidayApply(adjustedRescheduleToDate, loanApplicationTerms);
                    loanRepaymentScheduleInstallment.updateDueDate(adjustedRescheduleToDate);
                    log.debug("Updated installment {} due date from {} to {} for loan {}", 
                             loanRepaymentScheduleInstallment.getInstallmentNumber(), oldDueDate, adjustedRescheduleToDate, loan.getId());
                }
            }
            tmpFromDate = loanRepaymentScheduleInstallment.getDueDate();
        }
    }

    /**
     * Adjust all repayment schedules for "reschedule to next repayment date" type
     */
    private void adjustAllRepaymentSchedules(Loan loan, Holiday holiday, LocalDate adjustedRescheduleToDate) {
        final DefaultScheduledDateGenerator scheduledDateGenerator = new DefaultScheduledDateGenerator();
        ScheduleGeneratorDTO scheduleGeneratorDTO = loanUtilService.buildScheduleGeneratorDTO(loan, holiday.getFromDate());
        final LoanApplicationTerms loanApplicationTerms = loanTermVariationsMapper.constructLoanApplicationTerms(scheduleGeneratorDTO, loan);

        // first repayment's from date is same as disbursement date.
        LocalDate tmpFromDate = loan.getDisbursementDate();

        // Loop through all loanRepayments
        List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        for (final LoanRepaymentScheduleInstallment loanRepaymentScheduleInstallment : installments) {
            final LocalDate oldDueDate = loanRepaymentScheduleInstallment.getDueDate();

            // update from date if it's not same as previous installment's due date.
            if (!DateUtils.isEqual(tmpFromDate, loanRepaymentScheduleInstallment.getFromDate())) {
                loanRepaymentScheduleInstallment.updateFromDate(tmpFromDate);
            }

            if (!DateUtils.isBefore(oldDueDate, holiday.getFromDate())) {
                // Only adjust unpaid installments
                if (loanRepaymentScheduleInstallment.isNotFullyPaidOff()) {
                    adjustedRescheduleToDate = scheduledDateGenerator.generateNextRepaymentDate(adjustedRescheduleToDate, loanApplicationTerms, false);
                    loanRepaymentScheduleInstallment.updateDueDate(adjustedRescheduleToDate);
                    log.debug("Updated installment {} due date from {} to {} for loan {}", 
                             loanRepaymentScheduleInstallment.getInstallmentNumber(), oldDueDate, adjustedRescheduleToDate, loan.getId());
                }
            }
            tmpFromDate = loanRepaymentScheduleInstallment.getDueDate();
        }
    }

    /**
     * Get next repayment date for "reschedule to next repayment date" type
     */
    private LocalDate getNextRepaymentDate(Loan loan, Holiday holiday) {
        LocalDate adjustedRescheduleToDate = null;
        final LocalDate rescheduleToDate = holiday.getToDate();
        for (final LoanRepaymentScheduleInstallment loanRepaymentScheduleInstallment : loan.getRepaymentScheduleInstallments()) {
            if (DateUtils.isEqual(rescheduleToDate, loanRepaymentScheduleInstallment.getDueDate())) {
                adjustedRescheduleToDate = rescheduleToDate;
                break;
            } else {
                adjustedRescheduleToDate = doStandardMonthlyCheck(adjustedRescheduleToDate, rescheduleToDate, loanRepaymentScheduleInstallment);
            }
        }
        return adjustedRescheduleToDate;
    }

    /**
     * Standard monthly check for next repayment date calculation
     */
    private LocalDate doStandardMonthlyCheck(LocalDate adjustedRescheduleToDate, LocalDate rescheduleToDate, LoanRepaymentScheduleInstallment loanRepaymentScheduleInstallment) {
        // Standard Monthly Loan Holiday check
        LocalDate dueDate = loanRepaymentScheduleInstallment.getDueDate();
        if (DateUtils.isAfter(rescheduleToDate, dueDate) && DateUtils.isBefore(rescheduleToDate, dueDate.plusDays(30))) {
            adjustedRescheduleToDate = dueDate;
        }
        return adjustedRescheduleToDate;
    }

    /**
     * Custom method to update holiday that bypasses the active state restriction This allows editing of dates and
     * offices even when the holiday is active
     */
    private Map<String, Object> updateHolidayBypassingActiveStateRestriction(final Holiday holiday, final JsonCommand command) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>(7);
        final String dateFormatAsInput = command.dateFormat();
        final String localeAsInput = command.locale();

        // Always allow name and description updates
        if (command.isChangeInStringParameterNamed(nameParamName, holiday.getName())) {
            final String newValue = command.stringValueOfParameterNamed(nameParamName);
            actualChanges.put(nameParamName, newValue);
            holiday.setName(StringUtils.defaultIfEmpty(newValue, null));
        }

        if (command.isChangeInStringParameterNamed(descriptionParamName, holiday.getDescription())) {
            final String newValue = command.stringValueOfParameterNamed(descriptionParamName);
            actualChanges.put(descriptionParamName, newValue);
            holiday.setDescription(StringUtils.defaultIfEmpty(newValue, null));
        }

        // Allow rescheduling type updates
        if (command.isChangeInIntegerParameterNamed(HolidayApiConstants.reschedulingType, holiday.getReschedulingType())) {
            final Integer newValue = command.integerValueOfParameterNamed(HolidayApiConstants.reschedulingType);
            actualChanges.put(HolidayApiConstants.reschedulingType, newValue);
            holiday.setReschedulingType(RescheduleType.fromInt(newValue).getValue());
            if (newValue.equals(RescheduleType.RESCHEDULETONEXTREPAYMENTDATE.getValue())) {
                holiday.setRepaymentsRescheduledTo(null);
            }
        }

        // Allow date updates even for active holidays (bypassing the restriction)
        if (command.isChangeInLocalDateParameterNamed(fromDateParamName, holiday.getFromDate())) {
            final String valueAsInput = command.stringValueOfParameterNamed(fromDateParamName);
            actualChanges.put(fromDateParamName, valueAsInput);
            actualChanges.put(dateFormatParamName, dateFormatAsInput);
            actualChanges.put(localeParamName, localeAsInput);
            holiday.setFromDate(command.localDateValueOfParameterNamed(fromDateParamName));
        }

        if (command.isChangeInLocalDateParameterNamed(toDateParamName, holiday.getToDate())) {
            final String valueAsInput = command.stringValueOfParameterNamed(toDateParamName);
            actualChanges.put(toDateParamName, valueAsInput);
            actualChanges.put(dateFormatParamName, dateFormatAsInput);
            actualChanges.put(localeParamName, localeAsInput);
            holiday.setToDate(command.localDateValueOfParameterNamed(toDateParamName));
        }

        if (command.isChangeInLocalDateParameterNamed(repaymentsRescheduledToParamName, holiday.getRepaymentsRescheduledTo())) {
            final String valueAsInput = command.stringValueOfParameterNamed(repaymentsRescheduledToParamName);
            actualChanges.put(repaymentsRescheduledToParamName, valueAsInput);
            actualChanges.put(dateFormatParamName, dateFormatAsInput);
            actualChanges.put(localeParamName, localeAsInput);
            holiday.setRepaymentsRescheduledTo(command.localDateValueOfParameterNamed(repaymentsRescheduledToParamName));
        }

        // Allow office updates even for active holidays
        if (command.hasParameter(officesParamName)) {
            final JsonArray jsonArray = command.arrayOfParameterNamed(officesParamName);
            if (jsonArray != null) {
                actualChanges.put(officesParamName, command.jsonFragment(officesParamName));
            }
        }

        return actualChanges;
    }


    /**
     * Fix fromDate issues by updating them to be the previous installment's due date
     */
    private void fixFromDates(Loan loan) {
        LocalDate tmpFromDate = loan.getDisbursementDate();

        for (var installment : loan.getRepaymentScheduleInstallments()) {
            // Update from date to be the previous installment's due date
            if (!DateUtils.isEqual(tmpFromDate, installment.getFromDate())) {
                installment.updateFromDate(tmpFromDate);
            }
            tmpFromDate = installment.getDueDate();
        }
    }

    /**
     * Custom method to update holiday offices that bypasses the active state restriction
     */
    private boolean updateHolidayOffices(final Holiday holiday, final Set<Office> newOffices) {
        if (newOffices == null) {
            return false;
        }

        boolean updated = false;
        if (holiday.getOffices() != null) {
            final Set<Office> currentSetOfOffices = new HashSet<>(holiday.getOffices());
            final Set<Office> newSetOfOffices = new HashSet<>(newOffices);

            if (!currentSetOfOffices.equals(newSetOfOffices)) {
                updated = true;
                holiday.setOffices(newOffices);
            }
        } else {
            updated = true;
            holiday.setOffices(newOffices);
        }
        return updated;
    }


    /**
     * Simple and direct holiday deletion - just edit the due dates of affected installments
     */
    @Transactional
    @Override
    public CommandProcessingResult deleteHoliday(final Long holidayId) {
        this.context.authenticatedUser();
        final Holiday holiday = this.holidayRepository.findOneWithNotFoundDetection(holidayId);

        // Store holiday information before deletion
        final LocalDate holidayFromDate = holiday.getFromDate();
        final LocalDate holidayToDate = holiday.getToDate();
        final LocalDate repaymentsRescheduledTo = holiday.getRepaymentsRescheduledTo();
        final Set<Office> holidayOffices = holiday.getOffices();

        // Process affected loans with pagination and threading
        try {
            processAffectedLoansForDeletion(holidayOffices, holidayFromDate, holidayToDate, repaymentsRescheduledTo);
        } catch (JobExecutionException e) {
            log.debug("Failed to process affected loans for holiday deletion", e);
        }

        // Delete the holiday
        holiday.delete();
        this.holidayRepository.saveAndFlush(holiday);

        return new CommandProcessingResultBuilder().withEntityId(holidayId).build();
    }

    /**
     * Directly restore installment due dates affected by the holiday
     */
    private int restoreInstallmentsDirectly(Loan loan, LocalDate holidayFromDate, LocalDate holidayToDate, LocalDate repaymentsRescheduledTo) {
        int restoredCount = 0;

        log.debug("Checking loan {} for holiday deletion restoration. Holiday: {} to {}, reschedule: {}", 
                 loan.getId(), holidayFromDate, holidayToDate, repaymentsRescheduledTo);

        for (var installment : loan.getRepaymentScheduleInstallments()) {
            if (installment.getInstallmentNumber() == 0) continue; // Skip disbursement

            LocalDate currentDueDate = installment.getDueDate();

            log.debug("Checking installment {} with due date {} (paid: {})", 
                     installment.getInstallmentNumber(), currentDueDate, !installment.isNotFullyPaidOff());

            // Check if this installment was affected by the deleted holiday
            if (isInstallmentAffectedByDeletedHoliday(currentDueDate, holidayFromDate, holidayToDate, repaymentsRescheduledTo)) {

                // Calculate the original due date based on holiday type
                LocalDate originalDueDate = calculateOriginalDueDate(currentDueDate, holidayFromDate, holidayToDate, repaymentsRescheduledTo);

                // Only restore unpaid installments to avoid data inconsistency
                if (installment.isNotFullyPaidOff()) {
                    installment.updateDueDate(originalDueDate);
                    restoredCount++;
                    log.debug("Restored installment {} due date from {} to {} for loan {}", 
                             installment.getInstallmentNumber(), currentDueDate, originalDueDate, loan.getId());
                } else {
                    log.debug("Skipping paid installment {} with due date {}", 
                             installment.getInstallmentNumber(), currentDueDate);
                }
            } else {
                log.debug("Installment {} with due date {} not affected by holiday deletion", 
                         installment.getInstallmentNumber(), currentDueDate);
            }
        }

        log.debug("Restored {} installments for loan {}", restoredCount, loan.getId());
        return restoredCount;
    }

    /**
     * Check if this installment was affected by the deleted holiday
     */
    private boolean isInstallmentAffectedByDeletedHoliday(LocalDate currentDueDate, LocalDate holidayFromDate,
                                                          LocalDate holidayToDate, LocalDate repaymentsRescheduledTo) {

        // Case 1: Holiday had a specific reschedule date and current due date matches it
        if (repaymentsRescheduledTo != null && currentDueDate.equals(repaymentsRescheduledTo)) {
            log.debug("Installment due date {} matches reschedule date {}", currentDueDate, repaymentsRescheduledTo);
            return true;
        }

        // Case 2: Holiday was "reschedule to next repayment date" 
        if (repaymentsRescheduledTo == null) {
            // For "reschedule to next repayment date", we need to check if this installment
            // was originally due during the holiday period but got rescheduled
            // The current due date should be after the holiday period
            if (currentDueDate.isAfter(holidayToDate)) {
                // Additional check: the installment should not be too far from the holiday period
                // to avoid restoring installments that were naturally due after the holiday
                long daysDifference = ChronoUnit.DAYS.between(holidayToDate, currentDueDate);
                // Only consider installments within a reasonable range (e.g., 60 days) from the holiday
                if (daysDifference <= 60) {
                    log.debug("Installment due date {} is within {} days after holiday end date {}", 
                             currentDueDate, daysDifference, holidayToDate);
                    return true;
                }
            }
        }

        // Case 3: Check if the current due date is exactly the holiday date (for edge cases)
        if (currentDueDate.equals(holidayFromDate) || currentDueDate.equals(holidayToDate)) {
            log.debug("Installment due date {} matches holiday date range {} to {}", 
                     currentDueDate, holidayFromDate, holidayToDate);
            return true;
        }

        return false;
    }

    /**
     * Calculate the original due date for an installment that was affected by a holiday
     */
    private LocalDate calculateOriginalDueDate(LocalDate currentDueDate, LocalDate holidayFromDate, LocalDate holidayToDate, LocalDate repaymentsRescheduledTo) {

        // Case 1: Holiday had a specific reschedule date
        if (repaymentsRescheduledTo != null) {
            // If current due date matches the rescheduling date, the original was the holiday date
            if (currentDueDate.equals(repaymentsRescheduledTo)) {
                log.debug("Restoring installment from reschedule date {} to original holiday date {}", 
                         currentDueDate, holidayFromDate);
                return holidayFromDate;
            }
        }

        // Case 2: Holiday was "reschedule to next repayment date"
        if (repaymentsRescheduledTo == null) {
            // For this type, if the current due date is after the holiday date,
            // the original due date was the holiday from date
            if (currentDueDate.isAfter(holidayToDate)) {
                log.debug("Restoring installment from {} to original holiday date {} (next repayment type)", 
                         currentDueDate, holidayFromDate);
                return holidayFromDate;
            }
        }

        // Case 3: If the current due date is exactly the holiday date, keep it as is
        if (currentDueDate.equals(holidayFromDate) || currentDueDate.equals(holidayToDate)) {
            log.debug("Installment due date {} is already at holiday date, no change needed", currentDueDate);
            return currentDueDate;
        }

        // Fallback: use holiday from date as original
        log.debug("Using fallback: restoring installment from {} to holiday from date {}", 
                 currentDueDate, holidayFromDate);
        return holidayFromDate;
    }
}
