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

import static org.assertj.core.api.Assertions.assertThat;
import com.crediblex.fineract.test.factory.CustomLoanProductsRequestFactory;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.fineract.client.models.ChargeData;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.GetLoanProductsResponse;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdTransactions;
import org.apache.fineract.client.models.GetTaxesGroupResponse;
import org.apache.fineract.client.models.PostChargesResponse;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdResponse;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.models.PostLoansRequestChargeData;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.client.models.PostSavingsAccountsResponse;
import org.apache.fineract.client.models.PostTaxesComponentsRequest;
import org.apache.fineract.client.models.PostTaxesComponentsResponse;
import org.apache.fineract.client.models.PostTaxesGroupRequest;
import org.apache.fineract.client.models.PostTaxesGroupResponse;
import org.apache.fineract.client.models.PostTaxesGroupTaxComponents;
import org.apache.fineract.client.models.TableData;
import org.apache.fineract.client.services.ChargesApi;
import org.apache.fineract.client.services.LoanProductsApi;
import org.apache.fineract.client.services.LoansApi;
import org.apache.fineract.client.services.TaxComponentsApi;
import org.apache.fineract.client.services.TaxGroupApi;
import org.apache.fineract.test.data.ChargeCalculationType;
import org.apache.fineract.test.data.ChargeTimeType;
import org.apache.fineract.test.stepdef.AbstractStepDef;
import org.apache.fineract.test.support.TestContextKey;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit2.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom step definitions for loan accounts.
 */
@Slf4j
public class CustomLoanAccountStepDef extends AbstractStepDef {

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final String DEFAULT_LOCALE = "en";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);
    
    @Autowired
    private LoanProductsApi loanProductsApi;
    
    @Autowired
    private LoansApi loansApi;
    
    @Autowired
    private ChargesApi chargesApi;
    
    @Autowired
    private TaxGroupApi taxGroupApi;

    @Autowired
    private TaxComponentsApi taxComponentsApi;
    
    private Long eurLoanProductId = null;
    private Long disbursementChargeId = null;
    private Long vatTaxGroupId = null;

    @Given("A EUR loan product exists")
    public void ensureEURLoanProductExists() throws IOException {
        if (eurLoanProductId == null) {
            String productName = "EUR Loan Product";
            String shortName = "EURL";
            String currencyCode = "EUR";
            
            // First, check if product already exists
            eurLoanProductId = findExistingLoanProduct(productName, shortName);
            
            if (eurLoanProductId == null) {
                // Product doesn't exist, create it
                log.info("Creating EUR loan product as it doesn't exist");
                PostLoanProductsRequest request = CustomLoanProductsRequestFactory.createLoanProductRequest(productName, shortName, currencyCode);
                Response<PostLoanProductsResponse> response = loanProductsApi.createLoanProduct(request).execute();
                
                if (response.isSuccessful()) {
                    eurLoanProductId = response.body().getResourceId();
                    log.info("Created EUR loan product with ID: {}", eurLoanProductId);
                } else {
                    throw new RuntimeException("Failed to create EUR loan product: " + response.errorBody().string());
                }
            } else {
                log.info("Using existing EUR loan product with ID: {}", eurLoanProductId);
            }
            
            // Store in context for use by other steps
            testContext().set(TestContextKey.DEFAULT_LOAN_PRODUCT_CREATE_RESPONSE_LP1, eurLoanProductId);
        }
    }
    
    @Given("A disbursement charge {string} of type {int} with amount {int} and VAT {int} percent exists")
    public void ensureDisbursementChargeWithVATExists(String chargeName, int type, double chargeAmount, int vatPercent) throws IOException {
        // First ensure VAT tax group exists
        ensureVATTaxGroupExists(vatPercent);
        
        // Check if charge already exists
        disbursementChargeId = findExistingCharge(chargeName);
        
        if (disbursementChargeId == null) {
            log.info("Creating disbursement charge '{}' with amount {} and VAT {}%", chargeName, chargeAmount, vatPercent);
            
            ChargeRequest chargeRequest = new ChargeRequest()
                .name(chargeName)
                .chargeAppliesTo(1)
                .chargeTimeType(ChargeTimeType.DISBURSEMENT.value)
                .chargeCalculationType(ChargeCalculationType.PERCENTAGE_AMOUNT.ordinal()) // Flat
                .chargePaymentMode(0) // Regular
                .amount(chargeAmount)
                .active(true)
                .currencyCode("EUR")
                .locale(DEFAULT_LOCALE)
                    .taxGroupId(vatTaxGroupId);
            
            Response<PostChargesResponse> response = chargesApi.createCharge(chargeRequest).execute();
            
            if (response.isSuccessful()) {
                disbursementChargeId = response.body().getResourceId();
                log.info("Created disbursement charge with ID: {}", disbursementChargeId);
            } else {
                throw new RuntimeException("Failed to create disbursement charge: " + response.errorBody().string());
            }
        } else {
            log.info("Using existing disbursement charge with ID: {}", disbursementChargeId);
        }
        
        // Store in context
        testContext().set(TestContextKey.CHARGE_FOR_LOAN_DISBURSEMENT_CHARGE_CREATE_RESPONSE, disbursementChargeId);
    }
    
    @When("Client creates a new EUR loan with {string} submitted on date and principal {int} linked to savings account")
    public void clientCreatesNewEURLoan(String submittedOnDate, int principal) throws IOException {
        Response<PostClientsResponse> clientResponse = testContext().get(TestContextKey.CLIENT_CREATE_RESPONSE);
        long clientId = clientResponse.body().getClientId();
        
        PostLoansRequest loanRequest = new PostLoansRequest()
            .clientId(clientId)
            .productId(eurLoanProductId)
            .principal(new BigDecimal(principal))
            .loanTermFrequency(12)
            .loanTermFrequencyType(2) // Months
            .numberOfRepayments(12)
            .repaymentEvery(1)
            .repaymentFrequencyType(2) // Months
            .interestRatePerPeriod(new BigDecimal("10"))
            .interestType(0) // Declining Balance
            .interestCalculationPeriodType(1) // Same as repayment period
            .amortizationType(1) // Equal installments
            .expectedDisbursementDate(submittedOnDate)
            .submittedOnDate(submittedOnDate)
            .dateFormat(DATE_FORMAT)
            .locale(DEFAULT_LOCALE)
            .loanType("individual")
            .transactionProcessingStrategyCode("mifos-standard-strategy")
                .linkAccountId(((Response<PostSavingsAccountsResponse>)testContext().get(TestContextKey.EUR_SAVINGS_ACCOUNT_CREATE_RESPONSE))
                        .body().getResourceId())
                .datatables(Set.of(new TableData().registeredTableName("dt_loan_remitter_dp_info")
                        .data(createLoanRemitterInfo())));;
        
        Response<PostLoansResponse> loanResponse = loansApi.calculateLoanScheduleOrSubmitLoanApplication(loanRequest, "").execute();
        
        if (!loanResponse.isSuccessful()) {
            throw new RuntimeException("Failed to create loan: " + loanResponse.errorBody().string());
        }
        
        testContext().set(TestContextKey.LOAN_CREATE_RESPONSE, loanResponse);
        log.info("Created loan with ID: {}", loanResponse.body().getLoanId());
    }
    
    @When("Client creates a new EUR loan with {string} submitted on date, principal {int} and disbursement charge")
    public void clientCreatesNewEURLoanWithCharge(String submittedOnDate, int principal) throws IOException {
        Response<PostClientsResponse> clientResponse = testContext().get(TestContextKey.CLIENT_CREATE_RESPONSE);
        long clientId = clientResponse.body().getClientId();
        
        PostLoansRequest loanRequest = new PostLoansRequest()
            .clientId(clientId)
            .productId(eurLoanProductId)
            .principal(new BigDecimal(principal))
            .loanTermFrequency(12)
            .loanTermFrequencyType(2) // Months
            .numberOfRepayments(12)
            .repaymentEvery(1)
            .repaymentFrequencyType(2) // Months
            .interestRatePerPeriod(new BigDecimal("10"))
            .interestType(0) // Declining Balance
            .interestCalculationPeriodType(1) // Same as repayment period
            .amortizationType(1) // Equal installments
            .expectedDisbursementDate(submittedOnDate)
            .submittedOnDate(submittedOnDate)
            .dateFormat(DATE_FORMAT)
            .locale(DEFAULT_LOCALE)
            .loanType("individual")
            .transactionProcessingStrategyCode("mifos-standard-strategy")
                .linkAccountId(((Response<PostSavingsAccountsResponse>)testContext().get(TestContextKey.EUR_SAVINGS_ACCOUNT_CREATE_RESPONSE))
                        .body().getResourceId())
            .charges(List.of(new PostLoansRequestChargeData()
                .chargeId(disbursementChargeId)
                .amount(new BigDecimal(10))))
                .datatables(Set.of(new TableData().registeredTableName("dt_loan_remitter_dp_info")
                        .data(createLoanRemitterInfo())));

        log.info("Request data {}", loanRequest);
        Response<PostLoansResponse> loanResponse = loansApi.calculateLoanScheduleOrSubmitLoanApplication(loanRequest, "").execute();
        
        if (!loanResponse.isSuccessful()) {
            throw new RuntimeException("Failed to create loan with charge: " + loanResponse.errorBody().string());
        }
        
        testContext().set(TestContextKey.LOAN_CREATE_RESPONSE, loanResponse);
        log.info("Created loan with charge, ID: {}", loanResponse.body().getLoanId());
    }

    private static Map<String, Object> createLoanRemitterInfo() {
        Map<String, Object> remitterInfo = new HashMap<>();
        remitterInfo.put("locale", "en");
        remitterInfo.put("remitter_name", RandomStringUtils.insecure().nextAscii(10));
        remitterInfo.put("dp_name",RandomStringUtils.insecure().nextAscii(10));

        return remitterInfo;
    }
    
    @And("Admin approves the loan on {string} date and principal amount of {int}")
    public void adminApprovesLoan(String approvalDate, int principal) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();

        log.info("Loan details: {}", loanResponse.body());
        
        PostLoansLoanIdRequest approveRequest = new PostLoansLoanIdRequest()
            .approvedOnDate(approvalDate)
            .approvedLoanAmount(new BigDecimal(principal))
             .expectedDisbursementDate(approvalDate)
            .dateFormat(DATE_FORMAT)
            .locale(DEFAULT_LOCALE);
        
        Response<PostLoansLoanIdResponse> approveResponse = loansApi.stateTransitions(loanId, approveRequest, "approve").execute();
        
        if (!approveResponse.isSuccessful()) {
            throw new RuntimeException("Failed to approve loan: " + approveResponse.errorBody().string());
        }
        
        testContext().set(TestContextKey.LOAN_APPROVAL_RESPONSE, approveResponse);
        log.info("Approved loan ID: {}", loanId);
    }
    
    @And("Admin disburses the loan on {string} date and principal of {int} to savings account")
    public void adminDisbursesLoanToSavings(String disbursalDate,int principal) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Response<PostSavingsAccountsResponse> savingsResponse = testContext().get(TestContextKey.EUR_SAVINGS_ACCOUNT_CREATE_RESPONSE);
        
        Long loanId = loanResponse.body().getLoanId();
        Long savingsAccountId = savingsResponse.body().getSavingsId();
        
        PostLoansLoanIdRequest disburseRequest = new PostLoansLoanIdRequest()
            .actualDisbursementDate(disbursalDate)
            .transactionAmount(new BigDecimal(principal))
            .dateFormat(DATE_FORMAT)
            .locale(DEFAULT_LOCALE);
        
        Response<PostLoansLoanIdResponse> disburseResponse = loansApi.stateTransitions(loanId, disburseRequest, "disbursetosavings").execute();
        
        if (!disburseResponse.isSuccessful()) {
            throw new RuntimeException("Failed to disburse loan: " + disburseResponse.errorBody().string());
        }
        
        testContext().set(TestContextKey.LOAN_DISBURSE_RESPONSE, disburseResponse);
        log.info("Disbursed loan ID: {} to savings account ID: {}", loanId, savingsAccountId);
    }
    
    @Then("Loan has a {string} transaction with amount {double} on {string}")
    public void verifyLoanTransaction(String transactionType, Double expectedAmount, String transactionDate) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetailsResponse = loansApi.retrieveLoan(loanId, false, "transactions", "", "").execute();
        
        if (!loanDetailsResponse.isSuccessful()) {
            throw new RuntimeException("Failed to retrieve loan details: " + loanDetailsResponse.errorBody().string());
        }
        
        List<GetLoansLoanIdTransactions> transactions = loanDetailsResponse.body().getTransactions();
        LocalDate expectedDate = LocalDate.parse(transactionDate, FORMATTER);

        assert transactions != null;
        GetLoansLoanIdTransactions response = transactions.stream()
            .filter(t -> t.getType().getCode().equals(transactionType))
            .filter(t -> t.getDate().isEqual(expectedDate))
            .filter(t -> t.getAmount().compareTo(expectedAmount) == 0)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                String.format("Transaction not found: type=%s, amount=%f, date=%s", transactionType, expectedAmount, transactionDate)));

        assert response.getReversedOnDate() == null;

    }
    
    @Then("Loan has exactly {int} transactions on {string}")
    public void verifyLoanTransactionCount(int expectedCount, String transactionDate) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        assert loanResponse.body() != null;
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetailsResponse = loansApi.retrieveLoan(loanId, false, "transactions", "", "").execute();
        
        if (!loanDetailsResponse.isSuccessful()) {
            throw new RuntimeException("Failed to retrieve loan details: " + loanDetailsResponse.errorBody().string());
        }

        assert loanDetailsResponse.body() != null;
        List<GetLoansLoanIdTransactions> transactions = loanDetailsResponse.body().getTransactions();
        LocalDate expectedDate = LocalDate.parse(transactionDate, FORMATTER);
        
        long transactionCount = transactions.stream()
            .filter(t -> t.getDate().isEqual(expectedDate))
            .count();
        
        assertThat(transactionCount)
            .as("Expected %d transactions on %s but found %d", expectedCount, transactionDate, transactionCount)
            .isEqualTo(expectedCount);
        
        log.info("Verified {} transactions on date {}", transactionCount, transactionDate);
    }
    
    private Long findExistingLoanProduct(String productName, String shortName) throws IOException {
        log.info("Checking for existing loan product with name '{}' or shortname '{}'", productName, shortName);
        
        Response<List<GetLoanProductsResponse>> response = loanProductsApi.retrieveAllLoanProducts().execute();
        
        if (!response.isSuccessful()) {
            log.error("Failed to retrieve loan products: {}", response.errorBody().string());
            return null;
        }
        
        List<GetLoanProductsResponse> products = response.body();
        if (products == null || products.isEmpty()) {
            log.info("No existing loan products found");
            return null;
        }
        
        for (GetLoanProductsResponse product : products) {
            if ((product.getName() != null && product.getName().equalsIgnoreCase(productName)) ||
                (product.getShortName() != null && product.getShortName().equalsIgnoreCase(shortName))) {
                log.info("Found existing loan product: {} (ID: {})", product.getName(), product.getId());
                return product.getId();
            }
        }
        
        log.info("No existing loan product found with name '{}' or shortname '{}'", productName, shortName);
        return null;
    }
    
    private Long findExistingCharge(String chargeName) throws IOException {
        log.info("Checking for existing charge with name '{}'", chargeName);
        
        Response<List<ChargeData>> response = chargesApi.retrieveAllCharges().execute();
        
        if (!response.isSuccessful()) {
            log.error("Failed to retrieve charges: {}", response.errorBody().string());
            return null;
        }
        
        List<ChargeData> charges = response.body();
        if (charges == null || charges.isEmpty()) {
            log.info("No existing charges found");
            return null;
        }
        
        for (ChargeData charge : charges) {
            if (charge.getName() != null && charge.getName().equalsIgnoreCase(chargeName)) {
                log.info("Found existing charge: {} (ID: {})", charge.getName(), charge.getId());
                return charge.getId();
            }
        }
        
        log.info("No existing charge found with name '{}'", chargeName);
        return null;
    }
    
    private void ensureVATTaxGroupExists(int vatPercent) throws IOException {
        String taxGroupName = "VAT " + vatPercent + "%";
        
        // Check if tax group already exists
        Response<List<GetTaxesGroupResponse>> response = taxGroupApi.retrieveAllTaxGroups().execute();
        
        if (response.isSuccessful() && response.body() != null) {
            for (GetTaxesGroupResponse taxGroup : response.body()) {
                if (taxGroup.getName() != null && taxGroup.getName().equalsIgnoreCase(taxGroupName)) {
                    vatTaxGroupId = taxGroup.getId();
                    log.info("Using existing VAT tax group with ID: {}", vatTaxGroupId);
                    return;
                }
            }
        }
        
        // Create new tax group
        log.info("Creating VAT tax group with {}% rate", vatPercent);

        PostTaxesComponentsRequest componentRequest = new PostTaxesComponentsRequest()
            .name(taxGroupName)
            .locale(DEFAULT_LOCALE)
                .dateFormat(DATE_FORMAT)
            .percentage((float) vatPercent)
                .startDate("01 January 2020");

        Response<PostTaxesComponentsResponse> componentResponse = taxComponentsApi.createTaxComponent(componentRequest).execute();

        if(!componentResponse.isSuccessful()){
            throw new RuntimeException("Failed to create VAT tax component: " + componentResponse.errorBody().string());
        }
        
        PostTaxesGroupRequest taxGroupRequest = new PostTaxesGroupRequest()
            .name(taxGroupName)
            .locale(DEFAULT_LOCALE)
            .dateFormat(DATE_FORMAT)
            .taxComponents(Set.of(
                new PostTaxesGroupTaxComponents()
                    .taxComponentId(componentResponse.body().getResourceId())
                        .startDate("01 January 2020"))
            );
        
        Response<PostTaxesGroupResponse> createResponse = taxGroupApi.createTaxGroup(taxGroupRequest).execute();
        
        if (createResponse.isSuccessful()) {
            vatTaxGroupId = createResponse.body().getResourceId();
            log.info("Created VAT tax group with ID: {}", vatTaxGroupId);
        } else {
            throw new RuntimeException("Failed to create VAT tax group: " + createResponse.errorBody().string());
        }
    }
}
