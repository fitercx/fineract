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
import com.crediblex.fineract.portfolio.loc.data.ProductType;
import org.junit.jupiter.api.Test;

public class LineOfCreditApiResourceIntegrationTest {

    @Test
    public void testLineOfCreditRequestSerialization() {
        // Test the exact request format from the API client
        LineOfCreditRequest request = new LineOfCreditRequest();
        request.setClientId(1L);
        request.setName("clientlient payable");
        request.setProductType("PAYABLE");
        request.setMaximumAmount("3,000,000");
        request.setStartDate("29 August 2025");
        request.setEndDate("29 October 2025");
        request.setDateFormat("dd MMMM yyyy");
        request.setLocale("en");

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
        LineOfCreditRequest request1 = new LineOfCreditRequest();
        request1.setClientId(1L);
        request1.setName("Test Credit Line");
        request1.setProductType("PAYABLE");
        request1.setMaximumAmount("3000000");
        request1.setStartDate("2025-08-29");
        request1.setEndDate("2025-10-29");
        request1.setDateFormat("yyyy-MM-dd");
        request1.setLocale("en");

        LineOfCreditRequest request2 = new LineOfCreditRequest();
        request2.setClientId(1L);
        request2.setName("Test Credit Line");
        request2.setProductType("PAYABLE");
        request2.setMaximumAmount("3,000,000");
        request2.setStartDate("29 August 2025");
        request2.setEndDate("29 October 2025");
        request2.setDateFormat("dd MMMM yyyy");
        request2.setLocale("en");

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
        
        LineOfCreditRequest correctRequest = new LineOfCreditRequest();
        correctRequest.setClientId(1L);
        correctRequest.setName("clientlient payable");
        correctRequest.setProductType("PAYABLE");
        correctRequest.setMaximumAmount("3,000,000");
        correctRequest.setStartDate("29 August 2025");
        correctRequest.setEndDate("29 October 2025");
        correctRequest.setDateFormat("dd MMMM yyyy");
        correctRequest.setLocale("en");

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
        
        LineOfCreditRequest incorrectRequest = new LineOfCreditRequest();
        incorrectRequest.setClientId(1L);
        incorrectRequest.setName("clientlient payable");
        incorrectRequest.setProductType("PAYABLE");
        incorrectRequest.setMaximumAmount("3,000,000");
        incorrectRequest.setStartDate("29 August 2025");
        incorrectRequest.setEndDate("29 October 2025");
        incorrectRequest.setDateFormat("yyyy MMMM yyyy");
        incorrectRequest.setLocale("en");

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
