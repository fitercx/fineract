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

package com.crediblex.fineract.integrationtests.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Utility class for test data generation and common test operations
 */
public class TestUtils {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    /**
     * Generate a random string with prefix
     */
    public static String generateRandomString(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generate a random email
     */
    public static String generateRandomEmail() {
        return "test_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    /**
     * Generate a random phone number
     */
    public static String generateRandomPhoneNumber() {
        return "+1" + System.currentTimeMillis() % 10000000000L;
    }

    /**
     * Format date for Fineract API
     */
    public static String formatDate(LocalDate date) {
        return date.format(DATE_FORMAT);
    }

    /**
     * Get current date formatted for Fineract API
     */
    public static String getCurrentDate() {
        return formatDate(LocalDate.now());
    }

    /**
     * Get date N days from now
     */
    public static String getDateAfterDays(int days) {
        return formatDate(LocalDate.now().plusDays(days));
    }

    /**
     * Get date N days before now
     */
    public static String getDateBeforeDays(int days) {
        return formatDate(LocalDate.now().minusDays(days));
    }

    /**
     * Sleep for specified milliseconds (useful for async operations)
     */
    public static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
