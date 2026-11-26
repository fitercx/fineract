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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetail;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetailRepository;
import org.apache.fineract.infrastructure.jobs.service.JobRegisterService;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Starts Accrual Report S3 Export whenever Add Periodic Accrual Transactions finishes, whether it completed or failed;
 * the accrual outcome is passed on as the accrualStatus job parameter and recorded in the export manifest.
 */
@Slf4j
@Component
public class AccrualReportS3ExportTrigger implements JobExecutionListener {

    private final Job addPeriodicAccrualEntriesJob;
    private final ScheduledJobDetailRepository scheduledJobDetailRepository;
    private final JobRegisterService jobRegisterService;

    public AccrualReportS3ExportTrigger(@Qualifier("addPeriodicAccrualEntriesJob") final Job addPeriodicAccrualEntriesJob,
            final ScheduledJobDetailRepository scheduledJobDetailRepository, @Lazy final JobRegisterService jobRegisterService) {
        this.addPeriodicAccrualEntriesJob = addPeriodicAccrualEntriesJob;
        this.scheduledJobDetailRepository = scheduledJobDetailRepository;
        this.jobRegisterService = jobRegisterService;
    }

    @PostConstruct
    void register() {
        if (addPeriodicAccrualEntriesJob instanceof AbstractJob job) {
            job.registerJobExecutionListener(this);
        } else {
            log.error("Accrual Report S3 Export will not be triggered: addPeriodicAccrualEntriesJob is a {}",
                    addPeriodicAccrualEntriesJob.getClass().getName());
        }
    }

    @Override
    public void afterJob(final JobExecution jobExecution) {
        final String accrualStatus = jobExecution.getStatus() == BatchStatus.COMPLETED ? "SUCCESS" : jobExecution.getStatus().name();
        try {
            final ScheduledJobDetail exportJob = scheduledJobDetailRepository
                    .findByJobName(AccrualReportS3ExportJobName.ACCRUAL_REPORT_S3_EXPORT_JOB.toString());
            if (exportJob == null) {
                log.error("Accrual Report S3 Export not triggered: job '{}' not found",
                        AccrualReportS3ExportJobName.ACCRUAL_REPORT_S3_EXPORT_JOB);
                return;
            }
            jobRegisterService.executeJobWithParameters(exportJob.getId(), jobParametersJson(accrualStatus));
            log.info("Accrual Report S3 Export triggered after Add Periodic Accrual Transactions ({})", accrualStatus);
        } catch (RuntimeException e) {
            log.error("Accrual Report S3 Export could not be triggered after Add Periodic Accrual Transactions ({})", accrualStatus, e);
        }
    }

    static String jobParametersJson(final String accrualStatus) {
        final JsonObject parameter = new JsonObject();
        parameter.addProperty("parameterName", AccrualReportS3ExportJobName.ACCRUAL_STATUS_PARAMETER);
        parameter.addProperty("parameterValue", accrualStatus);
        final JsonArray parameters = new JsonArray();
        parameters.add(parameter);
        final JsonObject body = new JsonObject();
        body.add("jobParameters", parameters);
        return body.toString();
    }
}
