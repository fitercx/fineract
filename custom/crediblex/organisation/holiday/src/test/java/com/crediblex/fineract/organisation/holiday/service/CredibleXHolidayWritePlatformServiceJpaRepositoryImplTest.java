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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.data.HolidayDataValidator;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredibleXHolidayWritePlatformServiceJpaRepositoryImplTest {

    @Mock
    private HolidayDataValidator fromApiJsonDeserializer;

    @Mock
    private HolidayRepositoryWrapper holidayRepository;

    @Mock
    private WorkingDaysRepositoryWrapper daysRepositoryWrapper;

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;

    @Mock
    private FromJsonHelper fromApiJsonHelper;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;

    @Mock
    private LoanScheduleService loanScheduleService;

    @Mock
    private LoanUtilService loanUtilService;

    @Mock
    private ConfigurationDomainService configurationDomainService;

    @Test
    void testCredibleXHolidayWritePlatformServiceJpaRepositoryImplCreation() {
        // Test that the custom implementation can be instantiated
        CredibleXHolidayWritePlatformServiceJpaRepositoryImpl service = new CredibleXHolidayWritePlatformServiceJpaRepositoryImpl(
                fromApiJsonDeserializer, holidayRepository, daysRepositoryWrapper, context, officeRepositoryWrapper, fromApiJsonHelper,
                loanRepositoryWrapper, loanScheduleService, loanUtilService, configurationDomainService);

        assertNotNull(service, "CredibleX holiday service should be created successfully");
    }
}
