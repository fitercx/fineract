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

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Stores scheduled Settlements Report Excel files in S3 and returns a download URL.
 */
public interface SettlementsReportS3StorageService {

    /**
     * Upload Excel bytes and return a time-limited download URL.
     *
     * @param excelBytes
     *            xlsx content
     * @param reportTime
     *            report timestamp used in the object key (typically UAE local time)
     * @return presigned GET URL, or empty if S3 is unavailable / upload failed
     */
    Optional<String> uploadAndGetDownloadUrl(byte[] excelBytes, ZonedDateTime reportTime);
}
