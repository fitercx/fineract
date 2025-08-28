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
package com.crediblex.fineract.portfolio.loc.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditActionRequest;
import org.junit.jupiter.api.Test;

public class LineOfCreditApiResourceIntegrationTest {

    @Test
    public void testLineOfCreditRequestSerialization() {
        // Test the exact request format from the API client
        LineOfCreditRequest request = new LineOfCreditRequest(
            1L,                           // clientId
            "clientlient payable",        // name
            "payable",                    // productType
            "3,000,000",                  // maximumAmount (with commas)
            "29 August 2025",             // startDate
            "29 October 2025",            // endDate
            "dd MMMM yyyy",               // dateFormat (correct format for "29 August 2025")
            "en"                          // locale
        );

        // Verify the request can be serialized to JSON
        String json = request.toJson();
        assertThat(json).isNotNull();
        assertThat(json).contains("clientlient payable");
        assertThat(json).contains("3,000,000");
        assertThat(json).contains("29 August 2025");
        assertThat(json).contains("29 October 2025");
        assertThat(json).contains("dd MMMM yyyy");
        assertThat(json).contains("en");
    }

    @Test
    public void testLineOfCreditRequestWithDifferentFormats() {
        // Test with different date formats
        LineOfCreditRequest request1 = new LineOfCreditRequest(
            1L, "Test Credit Line", "payable", "3000000",
            "2025-08-29", "2025-10-29", "yyyy-MM-dd", "en"
        );

        LineOfCreditRequest request2 = new LineOfCreditRequest(
            1L, "Test Credit Line", "payable", "3,000,000",
            "29 August 2025", "29 October 2025", "dd MMMM yyyy", "en"
        );

        // Both should serialize successfully
        assertThat(request1.toJson()).isNotNull();
        assertThat(request2.toJson()).isNotNull();

        System.out.println("Request 1 JSON: " + request1.toJson());
        System.out.println("Request 2 JSON: " + request2.toJson());
    }

    @Test
    public void testCorrectDateFormatForApiClient() {
        // This test demonstrates the CORRECT format that the API client should send
        // The API client was sending "dateFormat": "yyyy MMMM yyyy" which is INVALID
        // It should send "dateFormat": "dd MMMM yyyy" instead
        
        LineOfCreditRequest correctRequest = new LineOfCreditRequest(
            1L,                           // clientId
            "clientlient payable",        // name
            "payable",                    // productType
            "3,000,000",                  // maximumAmount (with commas)
            "29 August 2025",             // startDate
            "29 October 2025",            // endDate
            "dd MMMM yyyy",               // CORRECT dateFormat (NOT "yyyy MMMM yyyy")
            "en"                          // locale
        );

        String json = correctRequest.toJson();
        
        // Verify the correct format is used
        assertThat(json).contains("\"dateFormat\":\"dd MMMM yyyy\"");
        assertThat(json).contains("\"startDate\":\"29 August 2025\"");
        assertThat(json).contains("\"endDate\":\"29 October 2025\"");
    }

    @Test
    public void testIncorrectDateFormatThatCausesError() {
        // This test shows the INCORRECT format that was causing the error
        // The API client was sending this, which causes validation failure
        
        LineOfCreditRequest incorrectRequest = new LineOfCreditRequest(
            1L,                           // clientId
            "clientlient payable",        // name
            "payable",                    // productType
            "3,000,000",                  // maximumAmount
            "29 August 2025",             // startDate
            "29 October 2025",            // endDate
            "yyyy MMMM yyyy",             // INCORRECT dateFormat (this causes the error)
            "en"                          // locale
        );

        String json = incorrectRequest.toJson();

        
        // This format will cause validation error because "yyyy MMMM yyyy" is not a valid date pattern
        // for the date "29 August 2025" - it should be "dd MMMM yyyy"
        assertThat(json).contains("\"dateFormat\":\"yyyy MMMM yyyy\"");
    }

    @Test
    public void testDefaultLineOfCreditActionRequest() {
        // Test that the default request object works correctly
        LineOfCreditActionRequest defaultRequest = new LineOfCreditActionRequest("yyyy-MM-dd", "en");
        String json = defaultRequest.toJson();
        
        assertThat(json).isNotNull();
        assertThat(json).contains("\"dateFormat\":\"yyyy-MM-dd\"");
        assertThat(json).contains("\"locale\":\"en\"");
        
        System.out.println("Default action request JSON: " + json);
    }
}
