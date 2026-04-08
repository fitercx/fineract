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
package com.crediblex.fineract.infrastructure.s3.config;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "crediblex.s3.bucket-name")
@Getter
public class S3Config {

    @Value("${crediblex.s3.bucket-name}")
    private String bucketName;

    @Value("${crediblex.s3.region:us-east-1}")
    private String region;

    @Value("${crediblex.s3.access-key-id:}")
    private String accessKeyId;

    @Value("${crediblex.s3.secret-access-key:}")
    private String secretAccessKey;

    @Value("${crediblex.s3.presigned-url-expiration-minutes:15}")
    private Long presignedUrlExpirationMinutes;

    @Value("${crediblex.s3.max-file-size-bytes:52428800}")
    private Long maxFileSizeBytes; // Default 50MB

    @Bean
    public S3Client s3Client() {
        return S3Client.builder().region(Region.of(region)).credentialsProvider(getCredentialsProvider()).build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder().region(Region.of(region)).credentialsProvider(getCredentialsProvider()).build();
    }

    private AwsCredentialsProvider getCredentialsProvider() {
        // Use static credentials if provided, otherwise fall back to default credential chain
        // (supports IAM roles, environment variables, EC2 instance profiles, etc.)
        if (StringUtils.isNotBlank(accessKeyId) && StringUtils.isNotBlank(secretAccessKey)) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        return DefaultCredentialsProvider.create();
    }
}
