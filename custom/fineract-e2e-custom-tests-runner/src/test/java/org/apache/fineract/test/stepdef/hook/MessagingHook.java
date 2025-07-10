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
package org.apache.fineract.test.stepdef.hook;

import io.cucumber.java.Before;
import lombok.extern.slf4j.Slf4j;

/**
 * This class overrides the base MessagingHook to prevent dependency issues
 * The package name must match exactly to override the base class
 */
@Slf4j
@SuppressWarnings({ "HideUtilityClassConstructor" })
public class MessagingHook {

    @Before
    public void emptyEventStore() {
        log.info("Custom MessagingHook.emptyEventStore() - Skipping event store reset");
        // Do nothing - we don't want to use event store in custom tests
    }
}
