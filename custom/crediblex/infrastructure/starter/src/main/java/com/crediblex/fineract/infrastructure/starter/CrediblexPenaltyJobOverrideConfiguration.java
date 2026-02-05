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
package com.crediblex.fineract.infrastructure.starter;

import com.crediblex.fineract.infrastructure.jobs.applychargetooverdueloaninstallment.CustomApplyChargeToOverdueLoanInstallmentTasklet;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration to override the core ApplyChargeToOverdueLoanInstallmentTasklet with custom implementation that
 * includes: - Deadlock prevention through sorted processing - Deadlock retry logic with exponential backoff - Batch
 * processing for better performance - Enhanced logging and metrics
 */
@Configuration
public class CrediblexPenaltyJobOverrideConfiguration {

    /**
     * Override the core tasklet bean with custom implementation. The @Primary annotation ensures Spring uses this bean
     * instead of the core one.
     *
     * Dependencies are injected directly via method parameters - Spring will automatically resolve them from the
     * application context.
     */
    @Bean
    @Primary
    public Tasklet applyChargeToOverdueLoanInstallmentTasklet(ConfigurationDomainService configurationDomainService,
            LoanReadPlatformService loanReadPlatformService, LoanChargeWritePlatformService loanChargeWritePlatformService,
            PlatformTransactionManager transactionManager,
            com.crediblex.fineract.infrastructure.jobs.applychargetooverdueloaninstallment.PenaltyJobProperties penaltyJobProperties) {
        return new CustomApplyChargeToOverdueLoanInstallmentTasklet(configurationDomainService, loanReadPlatformService,
                loanChargeWritePlatformService, transactionManager, penaltyJobProperties);
    }

    /**
     * Override the Step bean to use our custom tasklet. This ensures the step uses our custom tasklet implementation
     * and avoids type mismatch issues. Uses @Qualifier to specify the exact bean name since there are multiple @Primary
     * Tasklet beans.
     */
    @Bean
    @Primary
    protected Step applyChargeToOverdueLoanInstallmentStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            @Qualifier("applyChargeToOverdueLoanInstallmentTasklet") Tasklet applyChargeToOverdueLoanInstallmentTasklet) {
        return new StepBuilder(JobName.APPLY_CHARGE_TO_OVERDUE_LOAN_INSTALLMENT.name(), jobRepository)
                .tasklet(applyChargeToOverdueLoanInstallmentTasklet, transactionManager).build();
    }
}
