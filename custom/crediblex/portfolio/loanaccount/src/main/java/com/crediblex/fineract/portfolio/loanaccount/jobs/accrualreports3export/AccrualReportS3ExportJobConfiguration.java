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
package com.crediblex.fineract.portfolio.loanaccount.jobs.accrualreports3export;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.fineract.infrastructure.jobs.data.JobParameterDTO;
import org.apache.fineract.infrastructure.jobs.service.jobname.JobNameData;
import org.apache.fineract.infrastructure.jobs.service.jobname.JobNameProvider;
import org.apache.fineract.infrastructure.jobs.service.jobname.SimpleJobNameProvider;
import org.apache.fineract.infrastructure.jobs.service.jobparameterprovider.AbstractJobParameterProvider;
import org.apache.fineract.infrastructure.jobs.service.jobparameterprovider.JobParameterProvider;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class AccrualReportS3ExportJobConfiguration {

    private static final String JOB_NAME = AccrualReportS3ExportJobName.ACCRUAL_REPORT_S3_EXPORT_JOB.name();

    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private AccrualReportS3ExportTasklet accrualReportS3ExportTasklet;

    @Bean
    public JobNameProvider accrualReportS3ExportJobNameProvider() {
        return new SimpleJobNameProvider(
                List.of(new JobNameData(JOB_NAME, AccrualReportS3ExportJobName.ACCRUAL_REPORT_S3_EXPORT_JOB.toString())));
    }

    @Bean
    public JobParameterProvider<String> accrualReportS3ExportJobParameterProvider() {
        return new AbstractJobParameterProvider<>() {

            @Override
            public Map<String, JobParameter<String>> provide(final Set<JobParameterDTO> jobParameterDTOs) {
                return jobParameterDTOs.stream()
                        .filter(dto -> AccrualReportS3ExportJobName.ACCRUAL_STATUS_PARAMETER.equals(dto.getParameterName()))
                        .collect(Collectors.toMap(JobParameterDTO::getParameterName,
                                dto -> new JobParameter<>(dto.getParameterValue(), String.class), (first, second) -> first));
            }

            @Override
            protected String getJobName() {
                return JOB_NAME;
            }
        };
    }

    @Bean
    protected Step accrualReportS3ExportStep() {
        return new StepBuilder(JOB_NAME, jobRepository).tasklet(accrualReportS3ExportTasklet, transactionManager).build();
    }

    @Bean
    public Job accrualReportS3ExportJob() {
        return new JobBuilder(JOB_NAME, jobRepository).start(accrualReportS3ExportStep()).incrementer(new RunIdIncrementer()).build();
    }
}
