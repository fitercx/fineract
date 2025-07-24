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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.annotation.Transactional;

/**
 * CredibleX implementation of {@link HolidayWritePlatformServiceJpaRepositoryImpl} that allows editing active holidays
 * by bypassing the validation restrictions.
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

    @Autowired
    public CredibleXHolidayWritePlatformServiceJpaRepositoryImpl(HolidayDataValidator fromApiJsonDeserializer,
            HolidayRepositoryWrapper holidayRepository, WorkingDaysRepositoryWrapper daysRepositoryWrapper, PlatformSecurityContext context,
            OfficeRepositoryWrapper officeRepositoryWrapper, FromJsonHelper fromApiJsonHelper) {
        super(fromApiJsonDeserializer, holidayRepository, daysRepositoryWrapper, context, officeRepositoryWrapper, fromApiJsonHelper);
        log.info("CredibleX: Custom holiday service initialized successfully");
        this.fromApiJsonDeserializer = fromApiJsonDeserializer;
        this.holidayRepository = holidayRepository;
        this.daysRepositoryWrapper = daysRepositoryWrapper;
        this.context = context;
        this.officeRepositoryWrapper = officeRepositoryWrapper;
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    @Transactional
    @Override
    public CommandProcessingResult updateHoliday(final JsonCommand command) {
        log.info("CredibleX: Updating holiday with custom implementation - allowing active holiday edits for holiday ID: {}",
                command.entityId());
        try {
            this.context.authenticatedUser();
            // Skip validation to allow active holiday edits
            // this.fromApiJsonDeserializer.validateForUpdate(command.json());

            final Holiday holiday = this.holidayRepository.findOneWithNotFoundDetection(command.entityId());

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
