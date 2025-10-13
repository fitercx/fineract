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
package com.crediblex.fineract.integration.job;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanMonthlyAccrualJobAuditRepository;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch configuration for Odoo Journal Entries Sync Job
 */
@Configuration
@RequiredArgsConstructor
public class OdooJournalEntriesSyncJobConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OdooJournalEntriesSyncJobTasklet tasklet;

    @Bean
    protected Step odooJournalEntriesSyncJobStep() {
        return new StepBuilder(CrediblexJobName.ODOO_JOURNAL_ENTRIES_SYNC_JOB.name(), jobRepository).tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    public Job odooJournalEntriesSyncJob() {
        return new JobBuilder(CrediblexJobName.ODOO_JOURNAL_ENTRIES_SYNC_JOB.name(), jobRepository).start(odooJournalEntriesSyncJobStep())
                .incrementer(new RunIdIncrementer()).build();
    }
}
