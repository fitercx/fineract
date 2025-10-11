/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.jobs.generateloanmonthlyaccrualsummations;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanMonthlyAccrualJobAuditRepository;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class GenerateLoanMonthlyAccrualSummationsConfig {

    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LoanMonthlyAccrualJobAuditRepository loanMonthlyAccrualJobAuditRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Bean
    protected Step generateLoanMonthlyAccrualSummationsStep() {
        return new StepBuilder(JobName.GENERATE_LOAN_MONTHLY_ACCRUAL_SUMMATIONS.name(), jobRepository)
                .tasklet(generateLoanMonthlyAccrualSummationsTasklet(), transactionManager).build();
    }

    @Bean
    public Job generateLoanMonthlyAccrualSummationsJob() {
        return new JobBuilder(JobName.GENERATE_LOAN_MONTHLY_ACCRUAL_SUMMATIONS.name(), jobRepository)
                .start(generateLoanMonthlyAccrualSummationsStep()).incrementer(new RunIdIncrementer()).build();
    }

    @Bean
    public GenerateLoanMonthlyAccrualSummationsTasklet generateLoanMonthlyAccrualSummationsTasklet() {
        return new GenerateLoanMonthlyAccrualSummationsTasklet(this.loanMonthlyAccrualJobAuditRepository, this.jdbcTemplate);
    }
}
