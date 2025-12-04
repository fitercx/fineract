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
package com.crediblex.fineract.test.stepdef;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.util.List;
import org.apache.fineract.client.models.GetSavingsProductsResponse;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.models.PostSavingsAccountsRequest;
import org.apache.fineract.client.models.PostSavingsAccountsResponse;
import org.apache.fineract.client.models.PostSavingsProductsRequest;
import org.apache.fineract.client.models.PostSavingsProductsResponse;
import org.apache.fineract.client.models.SavingsAccountData;
import org.apache.fineract.client.services.GeneralLedgerAccountApi;
import org.apache.fineract.client.services.SavingsAccountApi;
import org.apache.fineract.client.services.SavingsProductApi;
import org.apache.fineract.test.factory.SavingsAccountRequestFactory;
import org.apache.fineract.test.stepdef.AbstractStepDef;
import org.apache.fineract.test.support.TestContextKey;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit2.Response;

import static org.apache.fineract.test.factory.SavingsAccountRequestFactory.DATE_FORMAT;
import static org.apache.fineract.test.factory.SavingsAccountRequestFactory.DEFAULT_CLIENT_ID;
import static org.apache.fineract.test.factory.SavingsAccountRequestFactory.DEFAULT_SUBMITTED_ON_DATE;
import static org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.GLA_NAME_6;
import static org.apache.fineract.test.support.TestContextKey.DEFAULT_SAVINGS_PRODUCT_CREATE_RESPONSE_EUR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import lombok.extern.slf4j.Slf4j;
import static org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.GLA_NAME_1;
import static org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.GLA_NAME_10;
import static org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.GLA_NAME_11;
import static org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.GLA_NAME_12;
import static org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.GLA_NAME_14;
import static org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.GLA_NAME_17;
import static org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.GLA_NAME_19;
import static org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.GLA_NAME_2;
import static org.apache.fineract.test.initializer.global.GLGlobalInitializerStep.GLA_NAME_20;
import org.apache.fineract.client.models.GetGLAccountsResponse;

/**
 * Custom step definitions for savings accounts.
 * Only overrides the steps that need to use dynamically created products.
 * All other steps are handled by the base SavingsAccountStepDef.
 */
@Slf4j
public class CustomSavingsAccountStepDef extends AbstractStepDef {

    private static final String DEFAULT_LOCALE = "en";
    
    @Autowired
    private SavingsProductApi savingsProductApi;
    
    @Autowired
    private SavingsAccountApi savingsAccountApi;

    @Autowired
    private GeneralLedgerAccountApi glAccountApi;

    private Long eurSavingsProductId = null;
    private Long usdSavingsProductId = null;
    private Long cashBasedSavingsProductId = null;

    @Given("A Custom EUR savings product exists")
    public void ensureEURSavingsProductExists() throws IOException {
        if (eurSavingsProductId == null) {
            String productName = "EUR Savings";
            String shortName = "EURS";
            String currencyCode = "EUR";
            
            // First, check if product already exists
            eurSavingsProductId = findExistingSavingsProduct(productName, shortName);
            
            if (eurSavingsProductId == null) {
                // Product doesn't exist, create it
                log.info("Creating EUR savings product as it doesn't exist");
                PostSavingsProductsRequest request = createSavingsProductRequest(productName, shortName, currencyCode);
                Response<PostSavingsProductsResponse> response = savingsProductApi.create13(request).execute();
                
                if (response.isSuccessful()) {
                    eurSavingsProductId = response.body().getResourceId();
                    log.info("Created EUR savings product with ID: {}", eurSavingsProductId);
                    // Store in test context for use by our custom factory
                    testContext().set(DEFAULT_SAVINGS_PRODUCT_CREATE_RESPONSE_EUR, response);
                } else {
                    throw new RuntimeException("Failed to create EUR savings product: " + response.errorBody().string());
                }
            } else {
                log.info("Using existing EUR savings product with ID: {}", eurSavingsProductId);
                // Create a mock response for the test context
                PostSavingsProductsResponse mockResponse = new PostSavingsProductsResponse();
                mockResponse.setResourceId(eurSavingsProductId);
                Response<PostSavingsProductsResponse> mockRetrofitResponse = Response.success(mockResponse);
                testContext().set(DEFAULT_SAVINGS_PRODUCT_CREATE_RESPONSE_EUR, mockRetrofitResponse);
            }
        }
    }
    
    @Given("A Custom USD savings product exists")
    public void ensureUSDSavingsProductExists() throws IOException {
        if (usdSavingsProductId == null) {
            String productName = "USD Savings";
            String shortName = "USDS";
            String currencyCode = "USD";
            
            // First, check if product already exists
            usdSavingsProductId = findExistingSavingsProduct(productName, shortName);
            
            if (usdSavingsProductId == null) {
                // Product doesn't exist, create it
                log.info("Creating USD savings product as it doesn't exist");
                PostSavingsProductsRequest request = createSavingsProductRequest(productName, shortName, currencyCode);
                Response<PostSavingsProductsResponse> response = savingsProductApi.create13(request).execute();
                
                if (response.isSuccessful()) {
                    usdSavingsProductId = response.body().getResourceId();
                    log.info("Created USD savings product with ID: {}", usdSavingsProductId);
                    // Store in test context for use by our custom factory
                    testContext().set(TestContextKey.DEFAULT_SAVINGS_PRODUCT_CREATE_RESPONSE_USD, response);
                } else {
                    throw new RuntimeException("Failed to create USD savings product: " + response.errorBody().string());
                }
            } else {
                log.info("Using existing USD savings product with ID: {}", usdSavingsProductId);
                // Create a mock response for the test context
                PostSavingsProductsResponse mockResponse = new PostSavingsProductsResponse();
                mockResponse.setResourceId(usdSavingsProductId);
                Response<PostSavingsProductsResponse> mockRetrofitResponse = Response.success(mockResponse);
                testContext().set(TestContextKey.DEFAULT_SAVINGS_PRODUCT_CREATE_RESPONSE_USD, mockRetrofitResponse);
            }
        }
    }

    
    /**
     * Find an existing savings product by name or short name
     */
    private Long findExistingSavingsProduct(String productName, String shortName) throws IOException {
        log.info("Checking for existing savings product with name '{}' or shortname '{}'", productName, shortName);
        
        Response<List<GetSavingsProductsResponse>> response = savingsProductApi.retrieveAll34().execute();
        
        if (!response.isSuccessful()) {
            log.error("Failed to retrieve savings products: {}", response.errorBody().string());
            return null;
        }
        
        List<GetSavingsProductsResponse> products = response.body();
        if (products == null || products.isEmpty()) {
            log.info("No existing savings products found");
            return null;
        }
        
        for (GetSavingsProductsResponse product : products) {
            if ((product.getName() != null && product.getName().equalsIgnoreCase(productName)) ||
                (product.getShortName() != null && product.getShortName().equalsIgnoreCase(shortName))) {
                log.info("Found existing savings product: {} (ID: {})", product.getName(), product.getId());
                return product.getId() == null ? null: Long.valueOf(product.getId());
            }
        }
        
        log.info("No existing savings product found with name '{}' or shortname '{}'", productName, shortName);
        return null;
    }

    @Given("A cash-based EUR savings product exists")
    public void ensureCashBasedEURSavingsProductExists() throws IOException {
        if (cashBasedSavingsProductId == null) {
            String productName = "Cash Based EUR Savings Product";
            String shortName = "CBSP";
            String currencyCode = "EUR";

            cashBasedSavingsProductId = findExistingSavingsProduct(productName, shortName);

            if (cashBasedSavingsProductId == null) {
                log.info("Creating cash based EUR savings product as it doesn't exist");

                Response<List<GetGLAccountsResponse>> glAccountsResponse = glAccountApi.retrieveAllAccounts(null, null, null, null, null, null).execute();
                if (!glAccountsResponse.isSuccessful()) {
                    throw new RuntimeException("Failed to retrieve GL accounts: " + glAccountsResponse.errorBody().string());
                }
                List<GetGLAccountsResponse> glAccounts = glAccountsResponse.body();

                Long savingsReferenceAccountId = glAccounts.stream().filter(acc -> GLA_NAME_1.equals(acc.getName())).findFirst().orElseThrow(() -> new RuntimeException("GL Account not found: " + GLA_NAME_1)).getId();
                Long savingsControlAccountId = glAccounts.stream().filter(acc -> GLA_NAME_17.equals(acc.getName())).findFirst().orElseThrow(() -> new RuntimeException("GL Account not found: " + GLA_NAME_17)).getId();
                Long interestOnSavingsAccountId = glAccounts.stream().filter(acc -> GLA_NAME_19.equals(acc.getName())).findFirst().orElseThrow(() -> new RuntimeException("GL Account not found: " + GLA_NAME_19)).getId();
                Long incomeFromFeeAccountId = glAccounts.stream().filter(acc -> GLA_NAME_10.equals(acc.getName())).findFirst().orElseThrow(() -> new RuntimeException("GL Account not found: " + GLA_NAME_10)).getId();
                Long incomeFromPenaltyAccountId = glAccounts.stream().filter(acc -> GLA_NAME_11.equals(acc.getName())).findFirst().orElseThrow(() -> new RuntimeException("GL Account not found: " + GLA_NAME_11)).getId();
                Long transfersInSuspenseAccountId = glAccounts.stream().filter(acc -> GLA_NAME_6.equals(acc.getName())).findFirst().orElseThrow(() -> new RuntimeException("GL Account not found: " + GLA_NAME_14)).getId();
                Long overdraftPortfolioControlId = glAccounts.stream().filter(acc -> GLA_NAME_2.equals(acc.getName())).findFirst().orElseThrow(() -> new RuntimeException("GL Account not found: " + GLA_NAME_2)).getId();
                Long incomeFromInterestId = glAccounts.stream().filter(acc -> GLA_NAME_20.equals(acc.getName())).findFirst().orElseThrow(() -> new RuntimeException("GL Account not found: " + GLA_NAME_20)).getId();
                Long writeOffAccountId = glAccounts.stream().filter(acc -> GLA_NAME_12.equals(acc.getName())).findFirst().orElseThrow(() -> new RuntimeException("GL Account not found: " + GLA_NAME_12)).getId();

                PostSavingsProductsRequest request = createCashBasedSavingsProductRequest(productName, shortName, currencyCode,
                        savingsReferenceAccountId, savingsControlAccountId, interestOnSavingsAccountId, incomeFromFeeAccountId,
                        incomeFromPenaltyAccountId, transfersInSuspenseAccountId, overdraftPortfolioControlId, incomeFromInterestId, writeOffAccountId);

                Response<PostSavingsProductsResponse> response = savingsProductApi.create13(request).execute();

                if (response.isSuccessful()) {
                    cashBasedSavingsProductId = response.body().getResourceId();
                    log.info("Created cash based EUR savings product with ID: {}", cashBasedSavingsProductId);
                } else {
                    throw new RuntimeException("Failed to create cash based EUR savings product: " + response.errorBody().string());
                }
            } else {
                log.info("Using existing cash based EUR savings product with ID: {}", cashBasedSavingsProductId);
            }
            testContext().set(DEFAULT_SAVINGS_PRODUCT_CREATE_RESPONSE_EUR, cashBasedSavingsProductId);

        }
    }

    private PostSavingsProductsRequest createSavingsProductRequest(String name, String shortName, String currencyCode) {
        return new PostSavingsProductsRequest()
            .name(name)
            .shortName(shortName)
            .description("Savings product for " + currencyCode)
            .currencyCode(currencyCode)
            .digitsAfterDecimal(2)
            .interestCompoundingPeriodType(1) // Monthly
            .interestPostingPeriodType(4) // Monthly
            .interestCalculationType(1) // Daily Balance
            .interestCalculationDaysInYearType(365)
                .nominalAnnualInterestRate(0d)
            .accountingRule(1) // None
            .locale(DEFAULT_LOCALE);
    }

    private PostSavingsProductsRequest createCashBasedSavingsProductRequest(String name, String shortName, String currencyCode,
            Long savingsReferenceAccountId, Long savingsControlAccountId, Long interestOnSavingsAccountId, Long incomeFromFeeAccountId,
            Long incomeFromPenaltyAccountId, Long transfersInSuspenseAccountId, Long overdraftPortfolioControlId, Long incomeFromInterestId, Long writeOffAccountId) {
        return new PostSavingsProductsRequest()
            .name(name)
            .shortName(shortName)
            .description("Cash-based savings product for " + currencyCode)
            .currencyCode(currencyCode)
            .digitsAfterDecimal(2)
            .interestCompoundingPeriodType(1) // Monthly
            .interestPostingPeriodType(4) // Monthly
            .interestCalculationType(1) // Daily Balance
            .interestCalculationDaysInYearType(365)
            .nominalAnnualInterestRate(0d)
            .accountingRule(2) // Cash-based
            .savingsReferenceAccountId(savingsReferenceAccountId)
            .savingsControlAccountId(savingsControlAccountId)
            .interestOnSavingsAccountId(interestOnSavingsAccountId)
            .incomeFromFeeAccountId(incomeFromFeeAccountId)
            .incomeFromPenaltyAccountId(incomeFromPenaltyAccountId)
            .transfersInSuspenseAccountId(transfersInSuspenseAccountId)
            .overdraftPortfolioControlId(overdraftPortfolioControlId)
            .incomeFromInterestId(incomeFromInterestId)
            .writeOffAccountId(writeOffAccountId)
            .locale(DEFAULT_LOCALE);
    }

    @Then("the savings account balance is {double}")
    public void verifySavingsAccountBalance(double expectedBalance) throws IOException {
        Response<PostSavingsAccountsResponse> savingsAccountResponse = testContext()
                .get(TestContextKey.EUR_SAVINGS_ACCOUNT_CREATE_RESPONSE);
        long savingsAccountId = savingsAccountResponse.body().getSavingsId();
        Response<SavingsAccountData> response = savingsAccountApi.retrieveOne25(savingsAccountId, false, null, null).execute();

        if (!response.isSuccessful()) {
            throw new RuntimeException("Failed to retrieve savings account: " + response.errorBody().string());
        }

        SavingsAccountData savingsAccount = response.body();
        double actualBalance = savingsAccount.getSummary().getAccountBalance().doubleValue();

        assertEquals(expectedBalance, actualBalance, 0.001, "Savings account balance does not match");
    }

    @And("Client creates a new EUR savings account with {string} submitted on date with accounting")
    public void createSavingsAccountEUR(String submittedOnDate) throws IOException {
        Response<PostClientsResponse> clientResponse = testContext().get(TestContextKey.CLIENT_CREATE_RESPONSE);
        long clientId = clientResponse.body().getClientId();

        PostSavingsAccountsRequest createSavingsAccountRequest =  new PostSavingsAccountsRequest()
                .dateFormat(DATE_FORMAT)//
                .productId(testContext().get(DEFAULT_SAVINGS_PRODUCT_CREATE_RESPONSE_EUR))//
                .locale(DEFAULT_LOCALE)
                .clientId(clientId).submittedOnDate(submittedOnDate);

        Response<PostSavingsAccountsResponse> createSavingsAccountResponse = savingsAccountApi
                .submitApplication2(createSavingsAccountRequest).execute();
        testContext().set(TestContextKey.EUR_SAVINGS_ACCOUNT_CREATE_RESPONSE, createSavingsAccountResponse);
    }


}
