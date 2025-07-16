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
package com.crediblex.fineract.test.config;

import io.cucumber.java.BeforeAll;
import io.cucumber.java.Before;
import io.cucumber.java.AfterAll;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomInitializingHook {

    @BeforeAll(order = 0) // Run before base hook
    public static void disableBaseInitialization() {
        // Force disable base initialization
        System.setProperty("INITIALIZATION_ENABLED", "false");
        System.setProperty("fineract-test.initialization.enabled", "false");
        log.info("Custom test initialization - base initialization disabled");
    }
    
    @Before(order = 0) // Run before base hook
    public static void beforeScenario() {
        log.info("Starting custom test scenario - no product pre-initialization");
    }
    
    @AfterAll(order = 0) // Run before base hook
    public static void afterAll() {
        log.info("Custom test suite completed");
    }
}
