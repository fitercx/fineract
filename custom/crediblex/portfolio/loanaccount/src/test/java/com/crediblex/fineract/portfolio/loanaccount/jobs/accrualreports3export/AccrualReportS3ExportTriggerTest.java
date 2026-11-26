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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetail;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetailRepository;
import org.apache.fineract.infrastructure.jobs.service.JobRegisterService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.job.AbstractJob;

class AccrualReportS3ExportTriggerTest {

    private static final String EXPORT_JOB_NAME = "Accrual Report S3 Export";

    private final AbstractJob accrualJob = mock(AbstractJob.class);
    private final ScheduledJobDetailRepository repository = mock(ScheduledJobDetailRepository.class);
    private final JobRegisterService jobRegisterService = mock(JobRegisterService.class);
    private final AccrualReportS3ExportTrigger trigger = new AccrualReportS3ExportTrigger(accrualJob, repository, jobRegisterService);

    @Test
    void registersItselfOnTheAccrualJob() {
        trigger.register();

        verify(accrualJob).registerJobExecutionListener(trigger);
    }

    @Test
    void startsExportWithAccrualOutcome() {
        final ScheduledJobDetail exportJob = mock(ScheduledJobDetail.class);
        when(exportJob.getId()).thenReturn(42L);
        when(repository.findByJobName(EXPORT_JOB_NAME)).thenReturn(exportJob);

        trigger.afterJob(execution(BatchStatus.COMPLETED));
        trigger.afterJob(execution(BatchStatus.FAILED));

        verify(jobRegisterService).executeJobWithParameters(42L,
                "{\"jobParameters\":[{\"parameterName\":\"accrualStatus\",\"parameterValue\":\"SUCCESS\"}]}");
        verify(jobRegisterService).executeJobWithParameters(42L,
                "{\"jobParameters\":[{\"parameterName\":\"accrualStatus\",\"parameterValue\":\"FAILED\"}]}");
    }

    @Test
    void neverFailsTheAccrualJob() {
        when(repository.findByJobName(EXPORT_JOB_NAME)).thenReturn(null);
        trigger.afterJob(execution(BatchStatus.COMPLETED));
        verify(jobRegisterService, never()).executeJobWithParameters(anyLong(), anyString());

        final ScheduledJobDetail exportJob = mock(ScheduledJobDetail.class);
        when(exportJob.getId()).thenReturn(42L);
        when(repository.findByJobName(EXPORT_JOB_NAME)).thenReturn(exportJob);
        doThrow(new IllegalStateException("job is disabled")).when(jobRegisterService).executeJobWithParameters(any(), any());
        assertDoesNotThrow(() -> trigger.afterJob(execution(BatchStatus.COMPLETED)));
    }

    @Test
    void humanReadableNameMatchesLiquibaseJobRow() {
        assertEquals(EXPORT_JOB_NAME, AccrualReportS3ExportJobName.ACCRUAL_REPORT_S3_EXPORT_JOB.toString());
    }

    private static JobExecution execution(final BatchStatus status) {
        final JobExecution execution = new JobExecution(1L);
        execution.setStatus(status);
        return execution;
    }
}
