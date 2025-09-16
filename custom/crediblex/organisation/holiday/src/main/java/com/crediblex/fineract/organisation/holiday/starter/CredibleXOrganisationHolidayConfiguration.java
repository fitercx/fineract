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
package com.crediblex.fineract.organisation.holiday.starter;

import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.config.TaskExecutorConstant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.data.HolidayDataValidator;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.holiday.service.HolidayWritePlatformService;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTermVariationsMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Autoconfiguration for CredibleX Organisation Holiday module.
 */
@AutoConfiguration
public class CredibleXOrganisationHolidayConfiguration {

    @Bean
    @Primary
    public HolidayWritePlatformService holidayWritePlatformService(HolidayDataValidator fromApiJsonDeserializer,
            HolidayRepositoryWrapper holidayRepository, PlatformSecurityContext context, OfficeRepositoryWrapper officeRepositoryWrapper,
            FromJsonHelper fromApiJsonHelper, WorkingDaysRepositoryWrapper daysRepositoryWrapper,
            LoanRepositoryWrapper loanRepositoryWrapper, LoanScheduleService loanScheduleService, LoanUtilService loanUtilService,
            ConfigurationDomainService configurationDomainService, LoanTermVariationsMapper loanTermVariationsMapper,
            @Qualifier(TaskExecutorConstant.CONFIGURABLE_TASK_EXECUTOR_BEAN_NAME) ThreadPoolTaskExecutor taskExecutor,
            TransactionTemplate transactionTemplate) {
        return new com.crediblex.fineract.organisation.holiday.service.CredibleXHolidayWritePlatformServiceJpaRepositoryImpl(
                fromApiJsonDeserializer, holidayRepository, daysRepositoryWrapper, context, officeRepositoryWrapper, fromApiJsonHelper,
                loanRepositoryWrapper, loanScheduleService, loanUtilService, configurationDomainService, loanTermVariationsMapper,
                taskExecutor, transactionTemplate);
    }
}
