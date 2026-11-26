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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class AccrualReportS3UploaderTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 11, 24);

    private static AccrualReportS3Properties properties(final String bucket, final String prefix) {
        return new AccrualReportS3Properties(bucket, prefix, "me-central-1", "", "");
    }

    @Test
    void keysUseLoanFolderThenDayFolderUnderPrefix() {
        final AccrualReportS3Properties properties = properties("cx-data-uat", "/accrual-reports/");

        assertEquals("accrual-reports/21499/24-11-2026/Accrual-Report-21499-2026-11-24.xlsx",
                properties.loanReportKey(21499L, BUSINESS_DATE));
        assertEquals("accrual-reports/_runs/24-11-2026/manifest.json", properties.manifestKey(BUSINESS_DATE));
    }

    @Test
    void blankPrefixWritesAtBucketRoot() {
        final AccrualReportS3Properties properties = properties("cx-data-uat", " ");

        assertEquals("21499/24-11-2026/Accrual-Report-21499-2026-11-24.xlsx", properties.loanReportKey(21499L, BUSINESS_DATE));
    }

    @Test
    void blankBucketIsNotConfiguredAndCreatesNoClient() {
        final AccrualReportS3Uploader uploader = new AccrualReportS3Uploader(properties("  ", "accrual-reports"));

        assertFalse(uploader.isConfigured());
        assertThrows(IllegalStateException.class, () -> uploader.putManifest("k", "{}"));
    }

    @Test
    void putsXlsxAndManifestToConfiguredBucket() {
        final S3Client s3Client = mock(S3Client.class);
        final AccrualReportS3Uploader uploader = new AccrualReportS3Uploader(properties("cx-data-uat", "accrual-reports"), s3Client);
        assertTrue(uploader.isConfigured());

        uploader.putXlsx("accrual-reports/1/24-11-2026/a.xlsx", new byte[] { 1, 2, 3 });

        final ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertEquals("cx-data-uat", request.getValue().bucket());
        assertEquals("accrual-reports/1/24-11-2026/a.xlsx", request.getValue().key());
        assertEquals(AccrualReportS3Uploader.XLSX_CONTENT_TYPE, request.getValue().contentType());
        assertEquals(3L, request.getValue().contentLength());
    }
}
