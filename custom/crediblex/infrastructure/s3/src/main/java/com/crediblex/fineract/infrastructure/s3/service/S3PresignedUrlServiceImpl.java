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
import com.crediblex.fineract.infrastructure.s3.data.FileMetadataRequest;
import com.crediblex.fineract.infrastructure.s3.data.PresignedUrlItemResponse;
import com.crediblex.fineract.infrastructure.s3.data.PresignedUrlRequestData;
import com.crediblex.fineract.infrastructure.s3.data.PresignedUrlResponseData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3PresignedUrlServiceImpl implements S3PresignedUrlService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain", "text/csv",
            "application/zip", "application/x-zip-compressed"
    );

    private static final String UPLOADS_PREFIX = "uploads";

    private final S3Presigner s3Presigner;
    private final S3Config s3Config;

    @Override
    public PresignedUrlResponseData generatePresignedUrls(PresignedUrlRequestData request) {
        List<PresignedUrlItemResponse> responses = new ArrayList<>();

        if (request.getFiles() == null || request.getFiles().isEmpty()) {
            return PresignedUrlResponseData.builder()
                    .urls(responses)
                    .build();
        }

        for (FileMetadataRequest fileMetadata : request.getFiles()) {
            PresignedUrlItemResponse response = generatePresignedUrlForFile(fileMetadata);
            responses.add(response);
        }

        return PresignedUrlResponseData.builder()
                .urls(responses)
                .build();
    }

    private PresignedUrlItemResponse generatePresignedUrlForFile(FileMetadataRequest fileMetadata) {
        String correlationId = fileMetadata.getUploadCorrelationId();
        String fileName = fileMetadata.getFileName();

        try {
            // Validate file metadata
            String validationError = validateFileMetadata(fileMetadata);
            if (validationError != null) {
                return buildErrorResponse(correlationId, fileName, validationError);
            }

            // Generate unique object key
            String objectKey = generateObjectKey(fileMetadata);

            // Create presigned URL
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(objectKey)
                    .contentType(fileMetadata.getContentType())
                    .contentLength(fileMetadata.getFileSize())
                    .build();

            Long expirationMinutes = s3Config.getPresignedUrlExpirationMinutes();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

            log.debug("Generated presigned URL for file: {} with key: {}", fileName, objectKey);

            return PresignedUrlItemResponse.builder()
                    .uploadCorrelationId(correlationId)
                    .fileName(fileName)
                    .presignedUrl(presignedRequest.url().toString())
                    .objectKey(objectKey)
                    .expiresInSeconds(expirationMinutes * 60)
                    .success(true)
                    .errorMessage(null)
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate presigned URL for file: {} with correlationId: {}", fileName, correlationId, e);
            return buildErrorResponse(correlationId, fileName, "Failed to generate presigned URL: " + e.getMessage());
        }
    }

    private String validateFileMetadata(FileMetadataRequest fileMetadata) {
        if (fileMetadata.getUploadCorrelationId() == null || fileMetadata.getUploadCorrelationId().isBlank()) {
            return "Upload correlation ID is required";
        }

        if (fileMetadata.getFileName() == null || fileMetadata.getFileName().isBlank()) {
            return "File name is required";
        }

        if (fileMetadata.getContentType() == null || fileMetadata.getContentType().isBlank()) {
            return "Content type is required";
        }

        if (!ALLOWED_CONTENT_TYPES.contains(fileMetadata.getContentType().toLowerCase())) {
            return "Invalid content type: " + fileMetadata.getContentType();
        }

        if (fileMetadata.getFileSize() == null || fileMetadata.getFileSize() <= 0) {
            return "File size must be greater than 0";
        }

        return null; // No validation errors
    }

    private String generateObjectKey(FileMetadataRequest fileMetadata) {
        String uniqueId = UUID.randomUUID().toString();
        String sanitizedFileName = sanitizeFileName(fileMetadata.getFileName());
        return String.format("%s/%s/%s", UPLOADS_PREFIX, uniqueId, sanitizedFileName);
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        // Remove any path separators and potentially dangerous characters
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private PresignedUrlItemResponse buildErrorResponse(String correlationId, String fileName, String errorMessage) {
        return PresignedUrlItemResponse.builder()
                .uploadCorrelationId(correlationId)
                .fileName(fileName)
                .presignedUrl(null)
                .objectKey(null)
                .expiresInSeconds(null)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
