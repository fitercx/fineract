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
package com.crediblex.fineract.organisation.holiday.service;

import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.dateFormatParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.descriptionParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.fromDateParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.localeParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.nameParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.officesParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.repaymentsRescheduledToParamName;
import static org.apache.fineract.organisation.holiday.api.HolidayApiConstants.toDateParamName;

import com.google.gson.JsonArray;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
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
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    public CredibleXHolidayWritePlatformServiceJpaRepositoryImpl(HolidayDataValidator fromApiJsonDeserializer,
            HolidayRepositoryWrapper holidayRepository, WorkingDaysRepositoryWrapper daysRepositoryWrapper, PlatformSecurityContext context,
            OfficeRepositoryWrapper officeRepositoryWrapper, FromJsonHelper fromApiJsonHelper, LoanRepositoryWrapper loanRepositoryWrapper,
            LoanScheduleService loanScheduleService, LoanUtilService loanUtilService,
            ConfigurationDomainService configurationDomainService, LoanTermVariationsMapper loanTermVariationsMapper) {
        super(fromApiJsonDeserializer, holidayRepository, daysRepositoryWrapper, context, officeRepositoryWrapper, fromApiJsonHelper);
        log.info("CredibleX: Custom holiday service initialized successfully");
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
    }

    @Transactional
    @Override
    public CommandProcessingResult updateHoliday(final JsonCommand command) {
        log.info("CredibleX: Updating holiday with custom implementation - allowing active holiday edits for holiday ID: {}",
                command.entityId());
        try {
            this.context.authenticatedUser();

            final Holiday holiday = this.holidayRepository.findOneWithNotFoundDetection(command.entityId());

            // Store original dates to identify affected loans
            final LocalDate originalFromDate = holiday.getFromDate();
            final LocalDate originalToDate = holiday.getToDate();

            // Custom update logic that bypasses the active state restriction
            Map<String, Object> changes = updateHolidayBypassingActiveStateRestriction(holiday, command);

            validateInputDates(holiday.getFromDate(), holiday.getToDate(), holiday.getRepaymentsRescheduledTo());

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
                recalculateAffectedLoanSchedules(holiday, originalFromDate, originalToDate, changes);
            }

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(holiday.getId()).with(changes)
                    .build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            final Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            log.error("CredibleX: Error updating holiday: {}", throwable.getMessage());
            throw new PlatformDataIntegrityException("error.msg.holiday.unknown.data.integrity.issue",
                    "Unknown data integrity issue with resource: " + throwable.getMessage(), dve);
        }
    }

    /**
     * Recalculate schedules for loans affected by holiday date changes
     */
    private void recalculateAffectedLoanSchedules(Holiday holiday, LocalDate originalFromDate, LocalDate originalToDate,
            Map<String, Object> changes) {

        // Check if holiday dates were changed
        boolean fromDateChanged = changes.containsKey(fromDateParamName);
        boolean toDateChanged = changes.containsKey(toDateParamName);

        if (!fromDateChanged && !toDateChanged) {
            log.info("CredibleX: No date changes detected, skipping loan schedule recalculation");
            return;
        }

        log.info("CredibleX: Holiday dates changed - recalculating affected loan schedules. Original: {} to {}, New: {} to {}",
                originalFromDate, originalToDate, holiday.getFromDate(), holiday.getToDate());

        // Get all affected loans (both old and new date ranges)
        Set<Loan> affectedLoans = new HashSet<>();

        // Find loans affected by original dates
        if (fromDateChanged || toDateChanged) {
            affectedLoans.addAll(findLoansAffectedByHolidayDates(holiday.getOffices(), originalFromDate, originalToDate));
        }

        // Find loans affected by new dates
        affectedLoans.addAll(findLoansAffectedByHolidayDates(holiday.getOffices(), holiday.getFromDate(), holiday.getToDate()));

        log.info("CredibleX: Found {} loans affected by holiday date changes", affectedLoans.size());

        // Recalculate schedules for affected loans
        for (Loan loan : affectedLoans) {
            try {
                recalculateLoanSchedule(loan);
                log.info("CredibleX: Successfully recalculated schedule for loan ID: {}", loan.getId());
            } catch (Exception e) {
                log.error("CredibleX: Error recalculating schedule for loan ID: {} - {}", loan.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Find loans that are affected by the given holiday date range
     */
    private List<Loan> findLoansAffectedByHolidayDates(Set<Office> offices, LocalDate fromDate, LocalDate toDate) {
        if (offices == null || offices.isEmpty()) {
            log.info("CredibleX: No offices found for holiday date range {}-{}", fromDate, toDate);
            return new ArrayList<>();
        }

        // Get office IDs
        Collection<Long> officeIds = new ArrayList<>();
        for (Office office : offices) {
            officeIds.add(office.getId());
        }

        log.info("CredibleX: Looking for loans in offices {} for holiday date range {}-{}", officeIds, fromDate, toDate);

        // Define loan statuses to consider
        Collection<LoanStatus> loanStatuses = new ArrayList<>(
                Arrays.asList(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL, LoanStatus.APPROVED, LoanStatus.ACTIVE));

        // Find loans by office
        List<Loan> loans = new ArrayList<>();
        loans.addAll(loanRepositoryWrapper.findByClientOfficeIdsAndLoanStatus(officeIds, loanStatuses));
        loans.addAll(loanRepositoryWrapper.findByGroupOfficeIdsAndLoanStatus(officeIds, loanStatuses));

        log.info("CredibleX: Found {} total loans in offices {}", loans.size(), officeIds);

        // Filter loans that have repayment schedules overlapping with the holiday date range
        List<Loan> affectedLoans = new ArrayList<>();
        for (Loan loan : loans) {
            if (hasRepaymentScheduleOverlapWithHoliday(loan, fromDate, toDate)) {
                affectedLoans.add(loan);
                log.info("CredibleX: Loan {} has repayment schedule overlap with holiday {}-{}", loan.getId(), fromDate, toDate);
            }
        }

        log.info("CredibleX: Found {} loans with repayment schedule overlap", affectedLoans.size());
        return affectedLoans;
    }

    /**
     * Check if a loan has repayment schedules that overlap with the holiday date range
     */
    private boolean hasRepaymentScheduleOverlapWithHoliday(Loan loan, LocalDate holidayFromDate, LocalDate holidayToDate) {
        if (loan.getRepaymentScheduleInstallments() == null) {
            log.info("CredibleX: Loan {} has no repayment schedule installments", loan.getId());
            return false;
        }

        log.info("CredibleX: Checking loan {} repayment schedule for overlap with holiday {}-{}", loan.getId(), holidayFromDate, holidayToDate);
        
        for (var installment : loan.getRepaymentScheduleInstallments()) {
            LocalDate dueDate = installment.getDueDate();
            if (dueDate != null && !dueDate.isBefore(holidayFromDate) && !dueDate.isAfter(holidayToDate)) {
                log.info("CredibleX: Loan {} installment {} due date {} overlaps with holiday {}-{}", 
                        loan.getId(), installment.getInstallmentNumber(), dueDate, holidayFromDate, holidayToDate);
                return true;
            }
        }

        log.info("CredibleX: Loan {} has no repayment schedule overlap with holiday {}-{}", loan.getId(), holidayFromDate, holidayToDate);
        return false;
    }

    /**
     * Recalculate the schedule for a specific loan
     */
    private void recalculateLoanSchedule(Loan loan) {
        try {
            // Build schedule generator DTO
            var scheduleGeneratorDTO = loanUtilService.buildScheduleGeneratorDTO(loan, null);

            // Recalculate the schedule
            loanScheduleService.recalculateSchedule(loan, scheduleGeneratorDTO);

            // Save the loan
            loanRepositoryWrapper.saveAndFlush(loan);

        } catch (Exception e) {
            log.error("CredibleX: Error recalculating schedule for loan {}: {}", loan.getId(), e.getMessage(), e);
            throw e;
        }
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
                log.info("CredibleX: Fixing fromDate for installment {} from {} to {}",
                    installment.getInstallmentNumber(), installment.getFromDate(), tmpFromDate);
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
        log.info("CredibleX: Deleting holiday with direct due date restoration - holiday ID: {}", holidayId);

        this.context.authenticatedUser();
        final Holiday holiday = this.holidayRepository.findOneWithNotFoundDetection(holidayId);

        // Store holiday information before deletion
        final LocalDate holidayFromDate = holiday.getFromDate();
        final LocalDate holidayToDate = holiday.getToDate();
        final LocalDate repaymentsRescheduledTo = holiday.getRepaymentsRescheduledTo();
        final Set<Office> holidayOffices = holiday.getOffices();

        log.info("CredibleX: Holiday details - From: {}, To: {}, Rescheduled to: {}",
                holidayFromDate, holidayToDate, repaymentsRescheduledTo);

        // Find loans affected by this holiday
        List<Loan> affectedLoans = findLoansAffectedByHolidayDates(holidayOffices, holidayFromDate, holidayToDate);

        log.info("CredibleX: Found {} loans affected by holiday deletion", affectedLoans.size());

        // Process each affected loan
        int totalRestoredInstallments = 0;
        for (Loan loan : affectedLoans) {
            int restoredForThisLoan = restoreInstallmentsDirectly(loan, holidayFromDate, holidayToDate, repaymentsRescheduledTo);
            totalRestoredInstallments += restoredForThisLoan;

            if (restoredForThisLoan > 0) {
                loanRepositoryWrapper.saveAndFlush(loan);
                log.info("CredibleX: Restored {} installments for loan ID: {}", restoredForThisLoan, loan.getId());
            }
        }

        // Delete the holiday
        holiday.delete();
        this.holidayRepository.saveAndFlush(holiday);

        log.info("CredibleX: Holiday deletion completed. Total installments restored: {}", totalRestoredInstallments);

        return new CommandProcessingResultBuilder().withEntityId(holidayId).build();
    }

    /**
     * Directly restore installment due dates affected by the holiday
     */
    private int restoreInstallmentsDirectly(Loan loan, LocalDate holidayFromDate, LocalDate holidayToDate, LocalDate repaymentsRescheduledTo) {
        int restoredCount = 0;

        log.info("CredibleX: Processing loan ID: {} for direct installment restoration", loan.getId());

        for (var installment : loan.getRepaymentScheduleInstallments()) {
            if (installment.getInstallmentNumber() == 0) continue; // Skip disbursement

            LocalDate currentDueDate = installment.getDueDate();

            log.info("CredibleX: Checking installment {} with due date {} against holiday {}-{} (reschedule type: {})",
                    installment.getInstallmentNumber(), currentDueDate, holidayFromDate, holidayToDate,
                    repaymentsRescheduledTo != null ? "specific date" : "next repayment date");

            // Check if this installment was affected by the deleted holiday
            if (isInstallmentAffectedByDeletedHoliday(currentDueDate, holidayFromDate, holidayToDate, repaymentsRescheduledTo)) {

                // Calculate the original due date based on holiday type
                LocalDate originalDueDate = calculateOriginalDueDate(currentDueDate, holidayFromDate, holidayToDate, repaymentsRescheduledTo);

                log.info("CredibleX: Loan {} Installment {} - Current due: {}, Restoring to: {}, Paid: {}",
                        loan.getId(), installment.getInstallmentNumber(), currentDueDate, originalDueDate,
                        !installment.isNotFullyPaidOff());

                // Only restore unpaid installments to avoid data inconsistency
                if (installment.isNotFullyPaidOff()) {
                    log.info("CredibleX: Restoring unpaid installment {} from {} to {}",
                            installment.getInstallmentNumber(), currentDueDate, originalDueDate);

                    installment.updateDueDate(originalDueDate);
                    restoredCount++;

                } else {
                    log.warn("CredibleX: Installment {} already paid on {} - leaving as is (audit note: original should have been {})",
                            installment.getInstallmentNumber(), currentDueDate, originalDueDate);
                }
            } else {
                log.info("CredibleX: Installment {} with due date {} is NOT affected by holiday", 
                        installment.getInstallmentNumber(), currentDueDate);
            }
        }

        return restoredCount;
    }

    /**
     * Check if this installment was affected by the deleted holiday
     */
    private boolean isInstallmentAffectedByDeletedHoliday(LocalDate currentDueDate, LocalDate holidayFromDate,
                                                          LocalDate holidayToDate, LocalDate repaymentsRescheduledTo) {

        // Case 1: Holiday had a specific reschedule date and current due date matches it
        if (repaymentsRescheduledTo != null && currentDueDate.equals(repaymentsRescheduledTo)) {
            log.info("CredibleX: Installment due {} matches holiday reschedule date - AFFECTED", currentDueDate);
            return true;
        }

        // Case 2: Holiday was "reschedule to next repayment date" 
        if (repaymentsRescheduledTo == null) {
            // For "reschedule to next repayment date", we need to be more specific
            // The installment was originally due on the holiday date, but got rescheduled to a later date
            // So we need to check if this installment's current due date is after the holiday date
            // and could have been the result of the holiday rescheduling
            
            // Check if the current due date is after the holiday date
            if (currentDueDate.isAfter(holidayToDate)) {
                log.info("CredibleX: Installment due {} is after holiday end date {} and could be affected by reschedule to next repayment - AFFECTED",
                        currentDueDate, holidayToDate);
                return true;
            }
        }

        return false;
    }

    /**
     * Calculate the original due date for an installment that was affected by a holiday
     */
    private LocalDate calculateOriginalDueDate(LocalDate currentDueDate, LocalDate holidayFromDate, LocalDate holidayToDate, LocalDate repaymentsRescheduledTo) {
        
        // Case 1: Holiday had a specific reschedule date
        if (repaymentsRescheduledTo != null) {
            // If current due date matches the reschedule date, the original was likely the holiday date
            if (currentDueDate.equals(repaymentsRescheduledTo)) {
                return holidayFromDate;
            }
        }
        
        // Case 2: Holiday was "reschedule to next repayment date"
        if (repaymentsRescheduledTo == null) {
            // For this type, if the current due date is after the holiday date,
            // the original due date was the holiday date itself
            if (currentDueDate.isAfter(holidayToDate)) {
                return holidayFromDate;
            }
        }
        
        // Fallback: use holiday from date as original
        return holidayFromDate;
    }
}
