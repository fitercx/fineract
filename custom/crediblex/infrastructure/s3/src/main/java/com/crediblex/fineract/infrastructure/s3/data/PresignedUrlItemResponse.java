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
package com.crediblex.fineract.infrastructure.s3.data;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Single presigned URL response item")
public class PresignedUrlItemResponse {

    @Schema(description = "Same ID from request for frontend matching", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uploadCorrelationId;

    @Schema(description = "Original file name from request", example = "document.pdf")
    private String fileName;

    @Schema(description = "Presigned URL for uploading the file", example = "https://bucket.s3.amazonaws.com/uploads/...")
    private String presignedUrl;

    @Schema(description = "The S3 key where file will be stored", example = "uploads/550e8400-e29b-41d4-a716-446655440000/document.pdf")
    private String objectKey;

    @Schema(description = "URL expiration time in seconds", example = "900")
    private Long expiresInSeconds;

    @Schema(description = "Whether presigned URL generation was successful")
    private boolean success;

    @Schema(description = "Error message if generation failed", example = "Invalid content type")
    private String errorMessage;
}
