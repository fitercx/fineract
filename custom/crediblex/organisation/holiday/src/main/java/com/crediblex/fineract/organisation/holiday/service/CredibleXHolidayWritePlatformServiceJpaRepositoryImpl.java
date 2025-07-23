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

import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.data.HolidayDataValidator;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.holiday.service.HolidayWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Slf4j
@Primary
@Service
public class CredibleXHolidayWritePlatformServiceJpaRepositoryImpl extends HolidayWritePlatformServiceJpaRepositoryImpl {

    public CredibleXHolidayWritePlatformServiceJpaRepositoryImpl(final HolidayDataValidator fromApiJsonDeserializer,
            final HolidayRepositoryWrapper holidayRepository, final WorkingDaysRepositoryWrapper daysRepositoryWrapper,
            final PlatformSecurityContext context, final OfficeRepositoryWrapper officeRepositoryWrapper,
            final FromJsonHelper fromApiJsonHelper) {
        super(fromApiJsonDeserializer, holidayRepository, daysRepositoryWrapper, context, officeRepositoryWrapper, fromApiJsonHelper);
    }


    @Override
    public CommandProcessingResult updateHoliday(final JsonCommand command) {
        log.info("CredibleX: Updating holiday with custom implementation");
        // Add any custom logic here before calling the parent method
        return super.updateHoliday(command);
    }
}
