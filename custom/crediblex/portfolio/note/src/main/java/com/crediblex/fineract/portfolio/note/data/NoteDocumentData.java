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
package com.crediblex.fineract.portfolio.note.data;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for document information attached to a note.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Document information attached to a note")
public class NoteDocumentData {

    @Schema(description = "Document ID", example = "1")
    private Long id;

    @Schema(description = "Display name for the document", example = "Contract Agreement")
    private String name;

    @Schema(description = "Original file name", example = "contract.pdf")
    private String fileName;

    @Schema(description = "File size in bytes", example = "102400")
    private Long size;

    @Schema(description = "MIME content type", example = "application/pdf")
    private String type;

    @Schema(description = "Document description", example = "Signed contract from client")
    private String description;

    @Schema(description = "Storage location (S3 key)", example = "uploads/uuid/contract.pdf")
    private String location;

    @Schema(description = "Display order within the note", example = "1")
    private Integer displayOrder;

    @Schema(description = "Presigned URL to download/view the document", example = "https://bucket.s3.amazonaws.com/uploads/uuid/contract.pdf?X-Amz-Algorithm=...")
    private String presignedUrl;

    @Schema(description = "Presigned URL expiration time in seconds from now", example = "3600")
    private Long presignedUrlExpiresInSeconds;
}
