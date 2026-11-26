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

import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportData;
import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportXlsxWriter;
import com.crediblex.fineract.portfolio.loanaccount.service.LoanMonthlyAccrualReportReadPlatformService;
import com.google.gson.GsonBuilder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Uploads each exported loan's Accrual Report XLSX (identical to the frontend download) to
 * {@code <prefix>/<loanId>/<dd-MM-yyyy>/} and a run manifest to {@code <prefix>/_runs/<dd-MM-yyyy>/manifest.json}.
 * Started by Add Periodic Accrual Transactions when it finishes, so accruals are posted first.
 */
@Slf4j
@Component
public class AccrualReportS3ExportTasklet implements Tasklet {

    static final String MANUAL_RUN_ACCRUAL_STATUS = "NOT_TRIGGERED_BY_ACCRUAL_JOB";
    private static final ZoneId UAE = ZoneId.of("Asia/Dubai");

    private final AccrualReportExportLoanSelector loanSelector;
    private final AccrualReportExportWatermarkService watermarkService;
    private final LoanMonthlyAccrualReportReadPlatformService accrualReportReadService;
    private final AccrualReportXlsxWriter xlsxWriter;
    private final AccrualReportS3Uploader uploader;
    private final AccrualReportS3Properties properties;
    private final TransactionTemplate perLoanTransaction;

    public AccrualReportS3ExportTasklet(final AccrualReportExportLoanSelector loanSelector,
            final AccrualReportExportWatermarkService watermarkService,
            final LoanMonthlyAccrualReportReadPlatformService accrualReportReadService, final AccrualReportXlsxWriter xlsxWriter,
            final AccrualReportS3Uploader uploader, final AccrualReportS3Properties properties,
            final PlatformTransactionManager transactionManager) {
        this.loanSelector = loanSelector;
        this.watermarkService = watermarkService;
        this.accrualReportReadService = accrualReportReadService;
        this.xlsxWriter = xlsxWriter;
        this.uploader = uploader;
        this.properties = properties;
        // A failed loan must not mark the step's own transaction rollback-only.
        this.perLoanTransaction = new TransactionTemplate(transactionManager);
        this.perLoanTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.perLoanTransaction.setReadOnly(true);
    }

    @Override
    public RepeatStatus execute(final StepContribution contribution, final ChunkContext chunkContext) {
        if (!uploader.isConfigured()) {
            log.warn("Accrual Report S3 Export skipped: ACCRUAL_REPORT_S3_BUCKET_NAME is not set");
            return RepeatStatus.FINISHED;
        }
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        final String accrualStatus = accrualStatus(chunkContext);
        final OffsetDateTime startedAt = OffsetDateTime.now(UAE);
        final LocalDate closedFromExclusive = watermarkService.closedFromExclusive(businessDate);
        final List<Long> loanIds = loanSelector.selectLoanIds(closedFromExclusive, businessDate);
        log.info("Accrual Report S3 Export started: business date {}, accrual job status {}, {} loans (closed after {})", businessDate,
                accrualStatus, loanIds.size(), closedFromExclusive);

        int uploaded = 0;
        final List<Long> skippedLoanIds = new ArrayList<>();
        final List<Map<String, Object>> failedLoans = new ArrayList<>();
        for (Long loanId : loanIds) {
            try {
                final AccrualReportData report = perLoanTransaction
                        .execute(status -> accrualReportReadService.retrieveAccrualReport(loanId, businessDate));
                if (report == null || report.rows().isEmpty()) {
                    skippedLoanIds.add(loanId);
                    continue;
                }
                uploader.putXlsx(properties.loanReportKey(loanId, businessDate), xlsxWriter.write(report));
                uploaded++;
            } catch (RuntimeException e) {
                log.error("Accrual Report S3 Export failed for loan {}", loanId, e);
                final Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("loanId", loanId);
                failure.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                failedLoans.add(failure);
            }
        }

        final boolean success = failedLoans.isEmpty();
        final Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("businessDate", businessDate.toString());
        manifest.put("startedAt", startedAt.toString());
        manifest.put("finishedAt", OffsetDateTime.now(UAE).toString());
        manifest.put("status", success ? "SUCCESS" : "FAILED");
        manifest.put("accrualJobStatus", accrualStatus);
        manifest.put("bucket", properties.getBucketName());
        manifest.put("prefix", properties.getPrefix());
        manifest.put("closedLoansAfter", closedFromExclusive.toString());
        manifest.put("loansSelected", loanIds.size());
        manifest.put("filesUploaded", uploaded);
        manifest.put("loansSkipped", skippedLoanIds.size());
        manifest.put("loansFailed", failedLoans.size());
        manifest.put("skippedLoanIds", skippedLoanIds);
        manifest.put("failedLoans", failedLoans);
        uploader.putManifest(properties.manifestKey(businessDate), new GsonBuilder().setPrettyPrinting().create().toJson(manifest));

        log.info("Accrual Report S3 Export finished: {} uploaded, {} skipped (no schedule), {} failed", uploaded, skippedLoanIds.size(),
                failedLoans.size());
        if (!success) {
            throw new IllegalStateException("Accrual Report S3 Export failed for " + failedLoans.size() + " of " + loanIds.size()
                    + " loans; see " + properties.manifestKey(businessDate));
        }
        watermarkService.markSuccess(businessDate, closedFromExclusive);
        return RepeatStatus.FINISHED;
    }

    private static String accrualStatus(final ChunkContext chunkContext) {
        final Object value = chunkContext.getStepContext().getJobParameters().get(AccrualReportS3ExportJobName.ACCRUAL_STATUS_PARAMETER);
        return value != null ? value.toString() : MANUAL_RUN_ACCRUAL_STATUS;
    }
}
