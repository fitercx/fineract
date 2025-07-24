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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Autowired
    public CredibleXHolidayWritePlatformServiceJpaRepositoryImpl(HolidayDataValidator fromApiJsonDeserializer,
            HolidayRepositoryWrapper holidayRepository, WorkingDaysRepositoryWrapper daysRepositoryWrapper, PlatformSecurityContext context,
            OfficeRepositoryWrapper officeRepositoryWrapper, FromJsonHelper fromApiJsonHelper, LoanRepositoryWrapper loanRepositoryWrapper,
            LoanScheduleService loanScheduleService, LoanUtilService loanUtilService,
            ConfigurationDomainService configurationDomainService) {
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
            return new ArrayList<>();
        }

        // Get office IDs
        Collection<Long> officeIds = new ArrayList<>();
        for (Office office : offices) {
            officeIds.add(office.getId());
        }

        // Define loan statuses to consider
        Collection<LoanStatus> loanStatuses = new ArrayList<>(
                Arrays.asList(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL, LoanStatus.APPROVED, LoanStatus.ACTIVE));

        // Find loans by office
        List<Loan> loans = new ArrayList<>();
        loans.addAll(loanRepositoryWrapper.findByClientOfficeIdsAndLoanStatus(officeIds, loanStatuses));
        loans.addAll(loanRepositoryWrapper.findByGroupOfficeIdsAndLoanStatus(officeIds, loanStatuses));

        // Filter loans that have repayment schedules overlapping with the holiday date range
        List<Loan> affectedLoans = new ArrayList<>();
        for (Loan loan : loans) {
            if (hasRepaymentScheduleOverlapWithHoliday(loan, fromDate, toDate)) {
                affectedLoans.add(loan);
            }
        }

        return affectedLoans;
    }

    /**
     * Check if a loan has repayment schedules that overlap with the holiday date range
     */
    private boolean hasRepaymentScheduleOverlapWithHoliday(Loan loan, LocalDate holidayFromDate, LocalDate holidayToDate) {
        if (loan.getRepaymentScheduleInstallments() == null) {
            return false;
        }

        for (var installment : loan.getRepaymentScheduleInstallments()) {
            LocalDate dueDate = installment.getDueDate();
            if (dueDate != null && !dueDate.isBefore(holidayFromDate) && !dueDate.isAfter(holidayToDate)) {
                return true;
            }
        }

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
}
