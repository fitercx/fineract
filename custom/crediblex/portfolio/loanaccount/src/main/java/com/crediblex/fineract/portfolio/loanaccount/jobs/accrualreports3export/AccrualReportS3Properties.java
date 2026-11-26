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

import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportXlsxWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where the Accrual Report S3 export writes. Bucket and prefix are per environment (ACCRUAL_REPORT_S3_BUCKET_NAME,
 * ACCRUAL_REPORT_S3_PREFIX); region and credentials are shared with the crediblex.s3 settings.
 */
@Component
@Getter
public class AccrualReportS3Properties {

    private static final DateTimeFormatter FOLDER_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final String bucketName;
    private final String prefix;
    private final String region;
    private final String accessKeyId;
    private final String secretAccessKey;

    public AccrualReportS3Properties(@Value("${crediblex.accrual-report.s3.bucket-name:}") final String bucketName,
            @Value("${crediblex.accrual-report.s3.prefix:accrual-reports}") final String prefix,
            @Value("${crediblex.s3.region:us-east-1}") final String region,
            @Value("${crediblex.s3.access-key-id:}") final String accessKeyId,
            @Value("${crediblex.s3.secret-access-key:}") final String secretAccessKey) {
        this.bucketName = StringUtils.trimToEmpty(bucketName);
        this.prefix = StringUtils.strip(StringUtils.trimToEmpty(prefix), "/");
        this.region = region;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }

    public boolean isConfigured() {
        return !bucketName.isEmpty();
    }

    /** {@code <prefix>/<loanId>/<dd-MM-yyyy>/Accrual-Report-<loanId>-<yyyy-MM-dd>.xlsx} */
    public String loanReportKey(final Long loanId, final LocalDate businessDate) {
        return withPrefix(loanId + "/" + FOLDER_DATE.format(businessDate) + "/" + AccrualReportXlsxWriter.fileName(loanId, businessDate));
    }

    /** {@code <prefix>/_runs/<dd-MM-yyyy>/manifest.json} */
    public String manifestKey(final LocalDate businessDate) {
        return withPrefix("_runs/" + FOLDER_DATE.format(businessDate) + "/manifest.json");
    }

    private String withPrefix(final String key) {
        return prefix.isEmpty() ? key : prefix + "/" + key;
    }
}
