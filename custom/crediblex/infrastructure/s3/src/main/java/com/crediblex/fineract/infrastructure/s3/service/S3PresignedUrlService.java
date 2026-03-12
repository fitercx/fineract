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

import com.crediblex.fineract.infrastructure.s3.data.PresignedUrlRequestData;
import com.crediblex.fineract.infrastructure.s3.data.PresignedUrlResponseData;

/**
 * Service interface for generating S3 presigned URLs.
 */
public interface S3PresignedUrlService {

    /**
     * Generates presigned PUT URLs for multiple files in batch.
     *
     * @param request
     *            the request containing file metadata for each file
     * @return response containing presigned URL results for each file
     */
    PresignedUrlResponseData generatePresignedUrls(PresignedUrlRequestData request);
}
