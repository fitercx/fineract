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
package com.crediblex.fineract.infrastructure.s3.service;

import com.crediblex.fineract.infrastructure.s3.config.S3Config;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * Uploads scheduled Settlements Report Excel files using existing Crediblex S3 configuration.
 * <p>
 * Object key: {@code reports/settlements/Settlements_Report_yyyy-MM-dd_HHmmss.xlsx}
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(S3Config.class)
public class SettlementsReportS3StorageServiceImpl implements SettlementsReportS3StorageService {

    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.ENGLISH);
    /** Presigned download link validity (S3 SigV4 max is 7 days). */
    private static final Duration DOWNLOAD_LINK_TTL = Duration.ofDays(7);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Config s3Config;

    @Override
    public Optional<String> uploadAndGetDownloadUrl(final byte[] excelBytes, final ZonedDateTime reportTime) {
        if (excelBytes == null || excelBytes.length == 0) {
            log.error("Cannot upload empty settlements Excel to S3");
            return Optional.empty();
        }
        if (s3Config.getBucketName() == null || s3Config.getBucketName().isBlank()) {
            log.error("S3 bucket name is blank; cannot store settlements report");
            return Optional.empty();
        }

        final String objectKey = buildObjectKey(reportTime);
        try {
            final PutObjectRequest putRequest = PutObjectRequest.builder().bucket(s3Config.getBucketName()).key(objectKey)
                    .contentType(CONTENT_TYPE).contentLength((long) excelBytes.length).build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(excelBytes));
            log.info("Uploaded settlements report to s3://{}/{}", s3Config.getBucketName(), objectKey);

            final GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(s3Config.getBucketName()).key(objectKey).build();
            final GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder().signatureDuration(DOWNLOAD_LINK_TTL)
                    .getObjectRequest(getObjectRequest).build();
            final PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
            return Optional.of(presigned.url().toString());
        } catch (Exception e) {
            log.error("Failed to upload settlements report to S3 key {}", objectKey, e);
            return Optional.empty();
        }
    }

    static String buildObjectKey(final ZonedDateTime reportTime) {
        return "reports/settlements/Settlements_Report_" + reportTime.format(PATH_FORMATTER) + ".xlsx";
    }
}
