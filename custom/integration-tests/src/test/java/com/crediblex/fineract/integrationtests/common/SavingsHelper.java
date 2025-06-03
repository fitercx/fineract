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

import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for Savings-related API operations
 */
public class SavingsHelper extends ApiTestBase {

    private static final String SAVINGS_URL = "/savingsaccounts";
    private static final String SAVINGS_PRODUCTS_URL = "/savingsproducts";

    /**
     * Create a savings account
     */
    public static Response createSavingsAccount(Map<String, Object> savingsData) {
        return getRequestSpec().body(savingsData).when().post(SAVINGS_URL).then().extract().response();
    }

    /**
     * Create a basic savings account
     */
    public static Response createBasicSavingsAccount(Long clientId, Long productId) {
        Map<String, Object> savingsData = new HashMap<>();
        savingsData.put("clientId", clientId);
        savingsData.put("productId", productId);
        savingsData.put("dateFormat", "dd MMMM yyyy");
        savingsData.put("locale", "en");
        savingsData.put("submittedOnDate", "01 January 2024");

        return createSavingsAccount(savingsData);
    }

    /**
     * Approve a savings account
     */
    public static Response approveSavingsAccount(Long savingsId, String approvalDate) {
        Map<String, Object> approvalData = new HashMap<>();
        approvalData.put("approvedOnDate", approvalDate);
        approvalData.put("dateFormat", "dd MMMM yyyy");
        approvalData.put("locale", "en");

        return getRequestSpec().body(approvalData).when().post(SAVINGS_URL + "/" + savingsId + "?command=approve").then().extract()
                .response();
    }

    /**
     * Activate a savings account
     */
    public static Response activateSavingsAccount(Long savingsId, String activationDate) {
        Map<String, Object> activationData = new HashMap<>();
        activationData.put("activatedOnDate", activationDate);
        activationData.put("dateFormat", "dd MMMM yyyy");
        activationData.put("locale", "en");

        return getRequestSpec().body(activationData).when().post(SAVINGS_URL + "/" + savingsId + "?command=activate").then().extract()
                .response();
    }

    /**
     * Make a deposit
     */
    public static Response deposit(Long savingsId, BigDecimal amount, String transactionDate) {
        Map<String, Object> depositData = new HashMap<>();
        depositData.put("transactionAmount", amount);
        depositData.put("transactionDate", transactionDate);
        depositData.put("dateFormat", "dd MMMM yyyy");
        depositData.put("locale", "en");

        return getRequestSpec().body(depositData).when().post(SAVINGS_URL + "/" + savingsId + "/transactions?command=deposit").then()
                .extract().response();
    }

    /**
     * Get savings account details
     */
    public static Response getSavingsAccount(Long savingsId) {
        return getRequestSpec().when().get(SAVINGS_URL + "/" + savingsId).then().extract().response();
    }

    /**
     * Extract savings account ID from create response
     */
    public static Long extractSavingsId(Response createResponse) {
        return createResponse.jsonPath().getLong("savingsId");
    }

    /**
     * Get savings products
     */
    public static Response getSavingsProducts() {
        return getRequestSpec().when().get(SAVINGS_PRODUCTS_URL).then().extract().response();
    }

    /**
     * Get first available savings product ID
     */
    public static Long getFirstSavingsProductId() {
        Response response = getSavingsProducts();
        if (response.getStatusCode() == 200) {
            return response.jsonPath().getLong("[0].id");
        }
        return null;
    }
}
