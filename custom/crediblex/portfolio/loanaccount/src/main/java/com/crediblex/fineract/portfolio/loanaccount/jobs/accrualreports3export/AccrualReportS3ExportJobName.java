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

/**
 * The enum name is the Spring Batch job name; the human-readable name must equal job.name in the database (changelog
 * 0076-add-accrual-report-s3-export-job.xml).
 */
public enum AccrualReportS3ExportJobName {

    ACCRUAL_REPORT_S3_EXPORT_JOB("Accrual Report S3 Export");

    /**
     * Job parameter carrying the exit status of the Add Periodic Accrual Transactions run that triggered the export.
     */
    public static final String ACCRUAL_STATUS_PARAMETER = "accrualStatus";

    private final String humanReadableName;

    AccrualReportS3ExportJobName(final String humanReadableName) {
        this.humanReadableName = humanReadableName;
    }

    @Override
    public String toString() {
        return humanReadableName;
    }
}
