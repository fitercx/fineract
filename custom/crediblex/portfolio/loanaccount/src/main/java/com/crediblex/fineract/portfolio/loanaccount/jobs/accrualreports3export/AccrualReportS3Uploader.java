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

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Uploads Accrual Report files to the export bucket. Has its own S3 client so the export bucket can differ from the
 * crediblex.s3 documents bucket; no client is created when the export bucket is not configured.
 */
@Component
public class AccrualReportS3Uploader {

    static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String JSON_CONTENT_TYPE = "application/json";

    private final AccrualReportS3Properties properties;
    private final S3Client s3Client;

    @Autowired
    public AccrualReportS3Uploader(final AccrualReportS3Properties properties) {
        this(properties, properties.isConfigured() ? buildClient(properties) : null);
    }

    AccrualReportS3Uploader(final AccrualReportS3Properties properties, final S3Client s3Client) {
        this.properties = properties;
        this.s3Client = s3Client;
    }

    public boolean isConfigured() {
        return s3Client != null;
    }

    public void putXlsx(final String key, final byte[] bytes) {
        put(key, bytes, XLSX_CONTENT_TYPE);
    }

    public void putManifest(final String key, final String json) {
        put(key, json.getBytes(StandardCharsets.UTF_8), JSON_CONTENT_TYPE);
    }

    private void put(final String key, final byte[] bytes, final String contentType) {
        if (s3Client == null) {
            throw new IllegalStateException("Accrual Report S3 bucket is not configured (ACCRUAL_REPORT_S3_BUCKET_NAME)");
        }
        final PutObjectRequest request = PutObjectRequest.builder().bucket(properties.getBucketName()).key(key).contentType(contentType)
                .contentLength((long) bytes.length).build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
    }

    @PreDestroy
    void close() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    private static S3Client buildClient(final AccrualReportS3Properties properties) {
        return S3Client.builder().region(Region.of(properties.getRegion())).credentialsProvider(credentialsProvider(properties)).build();
    }

    private static AwsCredentialsProvider credentialsProvider(final AccrualReportS3Properties properties) {
        // Static keys when provided, otherwise the default chain (IAM role, environment, instance profile).
        if (StringUtils.isNotBlank(properties.getAccessKeyId()) && StringUtils.isNotBlank(properties.getSecretAccessKey())) {
            return StaticCredentialsProvider
                    .create(AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey()));
        }
        return DefaultCredentialsProvider.create();
    }
}
