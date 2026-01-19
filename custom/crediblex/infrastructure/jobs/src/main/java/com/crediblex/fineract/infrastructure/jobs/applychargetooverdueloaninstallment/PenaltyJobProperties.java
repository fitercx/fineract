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
package com.crediblex.fineract.infrastructure.jobs.applychargetooverdueloaninstallment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the penalty job.
 *
 * Properties can be configured in application.properties: custom.penalty.job.batch-size=50
 * custom.penalty.job.max-retries=3 etc.
 */
@Data
@Component
@ConfigurationProperties(prefix = "custom.penalty.job")
public class PenaltyJobProperties {

    /**
     * Number of loans to process per batch. Default: 50
     */
    private int batchSize = 50;

    /**
     * Maximum number of retry attempts for deadlock exceptions. Default: 3
     */
    private int maxRetries = 3;

    /**
     * Initial retry delay in milliseconds. Default: 100ms
     */
    private long retryInitialDelayMs = 100;

    /**
     * Maximum retry delay in milliseconds. Default: 2000ms
     */
    private long retryMaxDelayMs = 2000;

    /**
     * Exponential backoff multiplier for retry delays. Default: 2.0
     */
    private double retryMultiplier = 2.0;

    /**
     * Enable batch processing mode. Default: true
     */
    private boolean enableBatchProcessing = true;
}
