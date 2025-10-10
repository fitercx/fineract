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
package com.crediblex.fineract.test.factory;

import org.apache.fineract.client.models.PostLoanProductsRequest;

/**
 * Factory for creating loan product requests
 */
public class CustomLoanProductsRequestFactory {

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final String DEFAULT_LOCALE = "en";

    private CustomLoanProductsRequestFactory() {}

    public static PostLoanProductsRequest createLoanProductRequest(String name, String shortName, String currencyCode) {
        return new PostLoanProductsRequest()
            .name(name)
            .shortName(shortName)
            .description("Loan product for " + currencyCode)
            .currencyCode(currencyCode)
            .digitsAfterDecimal(2)
            .inMultiplesOf(1)
            .principal(10000d)
            .minPrincipal(1000d)
            .maxPrincipal(1000000d)
            .numberOfRepayments(12)
            .minNumberOfRepayments(1)
            .maxNumberOfRepayments(60)
            .repaymentEvery(1)
            .repaymentFrequencyType(2L) // Monthly
            .interestRatePerPeriod(10d)
            .minInterestRatePerPeriod(0d)
            .maxInterestRatePerPeriod(100d)
            .interestRateFrequencyType(2) // Per month
            .interestType(0) // Declining Balance
            .interestCalculationPeriodType(1) // Same as repayment period
            .amortizationType(1) // Equal installments
            .transactionProcessingStrategyCode("mifos-standard-strategy")
            .accountingRule(1) // None
            .includeInBorrowerCycle(false)
            .locale(DEFAULT_LOCALE)
            .dateFormat(DATE_FORMAT)
            .daysInMonthType(1) // Actual
            .daysInYearType(1) // Actual
            .isInterestRecalculationEnabled(false)
            .allowVariableInstallments(false);
    }

    public static PostLoanProductsRequest createFactorRateLoanProductRequest(String name, String shortName, String currencyCode, Double factorRate) {
        return new PostLoanProductsRequest()
            .name(name)
            .shortName(shortName)
            .description("Factor rate loan product for " + currencyCode + " with factor rate " + factorRate)
            .currencyCode(currencyCode)
            .digitsAfterDecimal(2)
            .inMultiplesOf(1)
            .principal(10000d)
            .minPrincipal(1000d)
            .maxPrincipal(1000000d)
            .numberOfRepayments(12)
            .minNumberOfRepayments(1)
            .maxNumberOfRepayments(60)
            .repaymentEvery(1)
            .repaymentFrequencyType(2L) // Monthly
            .interestRatePerPeriod(10d)
            .minInterestRatePerPeriod(0d)
            .maxInterestRatePerPeriod(100d)
            .interestRateFrequencyType(2) // Per month
            .interestType(0) // Declining Balance
            .interestCalculationPeriodType(1) // Same as repayment period
            .amortizationType(1) // Equal installments
            .transactionProcessingStrategyCode("mifos-standard-strategy")
            .accountingRule(1) // None
            .includeInBorrowerCycle(false)
            .locale(DEFAULT_LOCALE)
            .dateFormat(DATE_FORMAT)
            .daysInMonthType(1) // Actual
            .daysInYearType(1) // Actual
            .isInterestRecalculationEnabled(false)
            .allowVariableInstallments(false);
            // Note: In a real implementation, you would add factor rate specific fields here
            // For now, this creates a standard loan product that can be used for testing
    }
}
