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
package com.crediblex.fineract.infrastructure.s3.api;

import com.crediblex.fineract.infrastructure.s3.data.PresignedUrlRequestData;
import com.crediblex.fineract.infrastructure.s3.data.PresignedUrlResponseData;
import com.crediblex.fineract.infrastructure.s3.service.S3PresignedUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

@Path("/v1/s3/presigned-urls")
@Component
@RequiredArgsConstructor
@Slf4j
@Tag(name = "S3 Presigned URLs", description = "API for generating S3 presigned URLs for file uploads")
public class S3PresignedUrlApiResource {

    private final PlatformSecurityContext securityContext;
    private final S3PresignedUrlService s3PresignedUrlService;

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(
            summary = "Generate presigned URLs for file uploads",
            description = "Generates presigned PUT URLs for multiple files in batch. " +
                    "Each file metadata in the request will receive a corresponding presigned URL in the response. " +
                    "Individual failures do not affect other files in the batch."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Presigned URLs generated successfully",
                    content = @Content(schema = @Schema(implementation = PresignedUrlResponseData.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public PresignedUrlResponseData generatePresignedUrls(
            @Parameter(
                    description = "Request containing file metadata for presigned URL generation",
                    required = true
            ) final PresignedUrlRequestData request) {

        // Ensure user is authenticated
        this.securityContext.authenticatedUser();

        log.info("Generating presigned URLs for {} files", 
                request.getFiles() != null ? request.getFiles().size() : 0);

        return s3PresignedUrlService.generatePresignedUrls(request);
    }
}
