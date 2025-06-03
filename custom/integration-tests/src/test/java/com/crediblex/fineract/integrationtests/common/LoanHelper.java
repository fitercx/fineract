
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
 * Helper class for Loan-related API operations
 */
public class LoanHelper extends ApiTestBase {

    private static final String LOANS_URL = "/loans";
    private static final String LOAN_PRODUCTS_URL = "/loanproducts";

    /**
     * Create a loan application
     */
    public static Response createLoanApplication(Map<String, Object> loanData) {
        return getRequestSpec().body(loanData).when().post(LOANS_URL).then().extract().response();
    }

    /**
     * Create a basic loan application
     */
    public static Response createBasicLoan(Long clientId, Long productId, BigDecimal principal) {
        Map<String, Object> loanData = new HashMap<>();
        loanData.put("clientId", clientId);
        loanData.put("productId", productId);
        loanData.put("principal", principal);
        loanData.put("loanTermFrequency", 12);
        loanData.put("loanTermFrequencyType", 2); // Months
        loanData.put("numberOfRepayments", 12);
        loanData.put("repaymentEvery", 1);
        loanData.put("repaymentFrequencyType", 2); // Months
        loanData.put("interestRatePerPeriod", 2);
        loanData.put("interestType", 0); // Declining Balance
        loanData.put("interestCalculationPeriodType", 1); // Same as repayment period
        loanData.put("transactionProcessingStrategyId", 1);
        loanData.put("dateFormat", "dd MMMM yyyy");
        loanData.put("locale", "en");
        loanData.put("expectedDisbursementDate", "01 January 2024");
        loanData.put("submittedOnDate", "01 January 2024");

        return createLoanApplication(loanData);
    }

    /**
     * Approve a loan
     */
    public static Response approveLoan(Long loanId, String approvalDate) {
        Map<String, Object> approvalData = new HashMap<>();
        approvalData.put("approvedOnDate", approvalDate);
        approvalData.put("dateFormat", "dd MMMM yyyy");
        approvalData.put("locale", "en");

        return getRequestSpec().body(approvalData).when().post(LOANS_URL + "/" + loanId + "?command=approve").then().extract().response();
    }

    /**
     * Disburse a loan
     */
    public static Response disburseLoan(Long loanId, String disbursalDate, BigDecimal amount) {
        Map<String, Object> disbursalData = new HashMap<>();
        disbursalData.put("actualDisbursementDate", disbursalDate);
        disbursalData.put("transactionAmount", amount);
        disbursalData.put("dateFormat", "dd MMMM yyyy");
        disbursalData.put("locale", "en");

        return getRequestSpec().body(disbursalData).when().post(LOANS_URL + "/" + loanId + "?command=disburse").then().extract().response();
    }

    /**
     * Get loan details
     */
    public static Response getLoan(Long loanId) {
        return getRequestSpec().when().get(LOANS_URL + "/" + loanId).then().extract().response();
    }

    /**
     * Get loan with associations
     */
    public static Response getLoanWithAssociations(Long loanId, String... associations) {
        String associationsParam = String.join(",", associations);
        return getRequestSpec().queryParam("associations", associationsParam).when().get(LOANS_URL + "/" + loanId).then().extract()
                .response();
    }

    /**
     * Extract loan ID from create response
     */
    public static Long extractLoanId(Response createResponse) {
        return createResponse.jsonPath().getLong("loanId");
    }

    /**
     * Get loan products
     */
    public static Response getLoanProducts() {
        return getRequestSpec().when().get(LOAN_PRODUCTS_URL).then().extract().response();
    }

    /**
     * Get first available loan product ID
     */
    public static Long getFirstLoanProductId() {
        Response response = getLoanProducts();
        if (response.getStatusCode() == 200) {
            return response.jsonPath().getLong("[0].id");
        }
        return null;
    }

    /**
     * Create a loan with late fees
     */
    public static Response createLoanWithLateFees(Long clientId, Long productId) {
        // First create and disburse a loan
        Response loanResponse = createBasicLoan(clientId, productId, new BigDecimal("10000"));
        if (loanResponse.getStatusCode() != 200) {
            return loanResponse;
        }

        Long loanId = extractLoanId(loanResponse);

        // Approve the loan
        approveLoan(loanId, "01 January 2024");

        // Disburse the loan
        disburseLoan(loanId, "01 January 2024", new BigDecimal("10000"));

        // Return the loan details
        return getLoanWithAssociations(loanId, "charges", "repaymentSchedule");
    }
}
