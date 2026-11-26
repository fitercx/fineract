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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportData;
import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportRowData;
import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportXlsxWriter;
import com.crediblex.fineract.portfolio.loanaccount.service.LoanMonthlyAccrualReportReadPlatformService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.PlatformTransactionManager;

class AccrualReportS3ExportTaskletTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 11, 24);
    private static final LocalDate CLOSED_FROM = LocalDate.of(2026, 11, 23);
    private static final String MANIFEST_KEY = "accrual-reports/_runs/24-11-2026/manifest.json";

    private final AccrualReportExportLoanSelector loanSelector = mock(AccrualReportExportLoanSelector.class);
    private final AccrualReportExportWatermarkService watermarkService = mock(AccrualReportExportWatermarkService.class);
    private final LoanMonthlyAccrualReportReadPlatformService readService = mock(LoanMonthlyAccrualReportReadPlatformService.class);
    private final AccrualReportXlsxWriter xlsxWriter = mock(AccrualReportXlsxWriter.class);
    private final AccrualReportS3Uploader uploader = mock(AccrualReportS3Uploader.class);
    private final AccrualReportS3Properties properties = new AccrualReportS3Properties("cx-data-uat", "accrual-reports", "me-central-1", "",
            "");
    private final AccrualReportS3ExportTasklet tasklet = new AccrualReportS3ExportTasklet(loanSelector, watermarkService, readService,
            xlsxWriter, uploader, properties, mock(PlatformTransactionManager.class));

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
        when(uploader.isConfigured()).thenReturn(true);
        when(watermarkService.closedFromExclusive(BUSINESS_DATE)).thenReturn(CLOSED_FROM);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void skipsWithoutTouchingLoansWhenBucketIsNotConfigured() {
        when(uploader.isConfigured()).thenReturn(false);

        assertEquals(RepeatStatus.FINISHED, tasklet.execute(null, chunkContext(Map.of())));

        verifyNoInteractions(loanSelector, readService, watermarkService);
        verify(uploader, never()).putManifest(anyString(), anyString());
    }

    @Test
    void uploadsEachLoanSkipsLoansWithoutScheduleWritesManifestAndAdvancesWatermark() {
        when(loanSelector.selectLoanIds(CLOSED_FROM, BUSINESS_DATE)).thenReturn(List.of(1L, 2L));
        final AccrualReportData loan1 = report(1L, List.of(new AccrualReportRowData(1, LocalDate.of(2026, 11, 30), "100.00", "0.00",
                "10.00", "")));
        when(readService.retrieveAccrualReport(1L, BUSINESS_DATE)).thenReturn(loan1);
        when(readService.retrieveAccrualReport(2L, BUSINESS_DATE)).thenReturn(report(2L, List.of()));
        when(xlsxWriter.write(loan1)).thenReturn(new byte[] { 9 });

        assertEquals(RepeatStatus.FINISHED,
                tasklet.execute(null, chunkContext(Map.of(AccrualReportS3ExportJobName.ACCRUAL_STATUS_PARAMETER, "SUCCESS"))));

        verify(uploader).putXlsx("accrual-reports/1/24-11-2026/Accrual-Report-1-2026-11-24.xlsx", new byte[] { 9 });
        verify(uploader, never()).putXlsx(eq("accrual-reports/2/24-11-2026/Accrual-Report-2-2026-11-24.xlsx"), any());
        final JsonObject manifest = manifest();
        assertEquals("SUCCESS", manifest.get("status").getAsString());
        assertEquals("SUCCESS", manifest.get("accrualJobStatus").getAsString());
        assertEquals("2026-11-24", manifest.get("businessDate").getAsString());
        assertEquals(2, manifest.get("loansSelected").getAsInt());
        assertEquals(1, manifest.get("filesUploaded").getAsInt());
        assertEquals(1, manifest.get("loansSkipped").getAsInt());
        assertEquals(2L, manifest.getAsJsonArray("skippedLoanIds").get(0).getAsLong());
        verify(watermarkService).markSuccess(BUSINESS_DATE, CLOSED_FROM);
    }

    @Test
    void failedLoanDoesNotStopOthersButFailsJobAndKeepsWatermark() {
        when(loanSelector.selectLoanIds(CLOSED_FROM, BUSINESS_DATE)).thenReturn(List.of(1L, 2L));
        when(readService.retrieveAccrualReport(1L, BUSINESS_DATE)).thenThrow(new IllegalStateException("schedule broken"));
        final AccrualReportData loan2 = report(2L, List.of(new AccrualReportRowData(1, LocalDate.of(2026, 11, 30), "100.00", "0.00",
                "10.00", "")));
        when(readService.retrieveAccrualReport(2L, BUSINESS_DATE)).thenReturn(loan2);
        when(xlsxWriter.write(loan2)).thenReturn(new byte[] { 7 });

        final IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> tasklet.execute(null, chunkContext(Map.of())));

        assertEquals("Accrual Report S3 Export failed for 1 of 2 loans; see " + MANIFEST_KEY, failure.getMessage());
        verify(uploader).putXlsx("accrual-reports/2/24-11-2026/Accrual-Report-2-2026-11-24.xlsx", new byte[] { 7 });
        final JsonObject manifest = manifest();
        assertEquals("FAILED", manifest.get("status").getAsString());
        assertEquals(AccrualReportS3ExportTasklet.MANUAL_RUN_ACCRUAL_STATUS, manifest.get("accrualJobStatus").getAsString());
        final JsonObject failedLoan = manifest.getAsJsonArray("failedLoans").get(0).getAsJsonObject();
        assertEquals(1L, failedLoan.get("loanId").getAsLong());
        assertEquals("schedule broken", failedLoan.get("error").getAsString());
        verify(watermarkService, never()).markSuccess(any(), any());
    }

    private JsonObject manifest() {
        final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(uploader).putManifest(eq(MANIFEST_KEY), json.capture());
        return JsonParser.parseString(json.getValue()).getAsJsonObject();
    }

    private static AccrualReportData report(final Long loanId, final List<AccrualReportRowData> rows) {
        return new AccrualReportData(loanId, BUSINESS_DATE, true, rows, "10.00", "0.00", "10.00");
    }

    private static ChunkContext chunkContext(final Map<String, Object> jobParameters) {
        final StepContext stepContext = mock(StepContext.class);
        when(stepContext.getJobParameters()).thenReturn(jobParameters);
        final ChunkContext chunkContext = mock(ChunkContext.class);
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        return chunkContext;
    }
}
