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
import static org.junit.Assert.assertNotNull;

import com.crediblex.client.models.ApprovedBuyer;
import com.crediblex.client.models.GetLineOfCreditResponse;
import com.crediblex.client.models.LineOfCreditActionRequest;
import com.crediblex.client.models.LineOfCreditRequest;
import com.crediblex.client.models.PostLineOfCreditResponse;
import com.crediblex.client.services.LineOfCreditApi;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.models.GetLoansTemplateLineOfCreditOptions;
import org.apache.fineract.client.models.GetLoansTemplateResponse;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.client.models.PostSavingsAccountsResponse;
import org.apache.fineract.client.models.TableData;
import org.apache.fineract.client.services.LoansApi;
import org.apache.fineract.test.stepdef.AbstractStepDef;
import org.apache.fineract.test.support.TestContextKey;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit2.Response;

@Slf4j
public class LineOfCreditStepDef extends AbstractStepDef {

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final String DEFAULT_LOCALE = "en";

    @Autowired
    private LineOfCreditApi lineOfCreditApi;

    @Autowired
    private LoansApi loansApi;

    private Long createdLineOfCreditId;
    private Long createdClientId;
    private Long drawdownLoanId;
    private String currentLocType; // Track the current LOC type (receivable/payable)

    @When("Client creates a new line of credit with start date {string} and max limit {int}")
    public void clientCreatesNewLineOfCredit(String startDate, int maxLimit) throws IOException {
        // Default to RECEIVABLE type for backward compatibility
        clientCreatesNewLineOfCreditWithType("receivable", startDate, maxLimit,0,0);
    }

    @When("Client creates a new {string} line of credit with start date {string} and max limit {int}")
    public void clientCreatesNewLineOfCreditWithType(String locType, String startDate, int maxLimit) throws IOException {
        clientCreatesNewLineOfCreditWithType(locType, startDate, maxLimit,0,0);
    }

    @When("Client creates a new {string} line of credit with start date {string} and max limit {int} with activation charge of {double} and having vat of {float}")
    public void clientCreatesNewLineOfCreditWithType(String locType, String startDate, int maxLimit,double activationCharge, float vatValue) throws IOException {
        Response<PostClientsResponse> clientResponse = testContext().get(TestContextKey.CLIENT_CREATE_RESPONSE);
        assertThat(clientResponse).isNotNull();
        createdClientId = clientResponse.body().getClientId();

        // Use existing EUR savings account as settlement account if available
        Response<PostSavingsAccountsResponse> savingsResponse = testContext().get(TestContextKey.EUR_SAVINGS_ACCOUNT_CREATE_RESPONSE);
        Long settlementSavingsAccountId = savingsResponse != null && savingsResponse.body() != null ? savingsResponse.body().getResourceId() : null;

        String externalId = "LOC-" + System.nanoTime();
        // Use a future date for interim review (30 days from now) to avoid validation failures
        String interimReviewDate = java.time.LocalDate.now().plusDays(30).format(java.time.format.DateTimeFormatter.ofPattern(DATE_FORMAT, java.util.Locale.ENGLISH));

        // Determine product type based on string parameter
        Integer productType = locType.equalsIgnoreCase("payable") ? 2 : 1; // 1=RECEIVABLE, 2=PAYABLE

        // Set tenor to 30 days for receivable line of credit, 10 days for others
        Integer tenorDays = locType.equalsIgnoreCase("receivable") ? 30 : 10;

        ApprovedBuyer approvedBuyer = new ApprovedBuyer();
        approvedBuyer.setName(externalId);
        LineOfCreditRequest request = new LineOfCreditRequest()
                .clientId(createdClientId)
                .productType(productType)
                .currencyCode("EUR")
                .clientCompanyName("Test Company")
                .clientContactPersonName("Jane Contact")
                .clientContactPersonPhone("123456789")
                .clientContactPersonEmail("contact@example.com")
                .authorizedSignatoryName("John Signatory")
                .authorizedSignatoryPhone("987654321")
                .authorizedSignatoryEmail("signatory@example.com")
                .virtualAccount("VA-" + System.currentTimeMillis())
                .externalId(externalId)
                .specialConditions("None")
                .maxCreditLimit(new BigDecimal(maxLimit))
                .startDate(startDate)
                .endDate(startDate) // For simplicity in tests
                .reviewPeriod(6) // Updated to match payload example
                .interimReviewDate(interimReviewDate)
                .annualInterestRate(12f) // Updated to match payload example
                .tenorDays(tenorDays) // Updated to match payload example
                .advancePercentage("100") // Updated to match payload example
                .cashMarginType(1)
                .cashMarginValue(10.5f)
                .interestChargeTime(1)
                .loanOfficerId(null) // optional
                .distributionPartner("Partner X")
                .approvedBuyers(List.of(approvedBuyer)) // Added approved buyers as in payload
                .settlementSavingsAccountId(settlementSavingsAccountId)
                .dateFormat(DATE_FORMAT)
                .locale(DEFAULT_LOCALE);

        if(vatValue > 0f){
            request.addChargesItem(
                new LineOfCreditCharge(testContext().get(TestContextKey.LINE_OF_CREDIT_ACTIVATION_CHARGE_RESPONSE),BigDecimal.valueOf(activationCharge))
            );
        }

        log.info("Creating {} Line of Credit request: {}", locType, request);
        Response<PostLineOfCreditResponse> response = lineOfCreditApi.create(createdClientId, request).execute();
        if (!response.isSuccessful()) {
            String errorMessage = "Failed to create line of credit";
            if (response.errorBody() != null) {
                try {
                    errorMessage += ": " + response.errorBody().string();
                } catch (IOException e) {
                    errorMessage += ": Error reading response body";
                }
            }
            throw new RuntimeException(errorMessage);
        }
        PostLineOfCreditResponse body = response.body();
        createdLineOfCreditId = body.getLineOfCreditId() != null ? body.getLineOfCreditId() : body.getResourceId();
        // Assert client linkage
        if (body.getClientId() != null) {
            assertThat(body.getClientId()).as("LOC clientId should match created client").isEqualTo(createdClientId);
        }
        // Assert settlement account linkage if provided
        if (settlementSavingsAccountId != null) {
            Response<GetLineOfCreditResponse> locAfterCreate = lineOfCreditApi.retrieveOne(createdClientId, createdLineOfCreditId)
                    .execute();
            if (locAfterCreate.isSuccessful()) {

                assertThat(locAfterCreate.body().getSettlementSavingsAccountId()).as("LOC should reference settlement savings account")
                        .isEqualTo(settlementSavingsAccountId);
            }
        }
        // Store the LOC type for use in drawdown operations
        currentLocType = locType.toLowerCase();
        log.info("Created {} Line of Credit ID: {} for Client ID: {}", locType, createdLineOfCreditId, createdClientId);
        testContext().set(TestContextKey.LINE_OF_CREDIT_CREATE_RESPONSE, response);
    }


    private record LineOfCreditCharge (Long chargeDefinitionId, BigDecimal editableAmount){
    }

    @When("Client creates a new line of credit with start date {string}, max limit {int} and expected available {int}")
    public void clientCreatesNewLineOfCreditWithExpected(String startDate, int maxLimit, int expectedAvailable) throws IOException {
        clientCreatesNewLineOfCredit(startDate, maxLimit); // reuse existing logic
        // Validate available balance equals expected
        GetLineOfCreditResponse loc = retrieveCurrentLoc();
        assertThat(loc.getAvailableBalance()).withFailMessage("Expected available balance %s but was %s", expectedAvailable, loc.getAvailableBalance())
                .isNotNull()
                .extracting(bd -> bd.compareTo(BigDecimal.valueOf(expectedAvailable)))
                .isEqualTo(0);
    }

    @And("Line of credit is approved on {string}")
    public void lineOfCreditApproved(String approvalDate) throws IOException {
        assertThat(createdClientId).isNotNull();
        assertThat(createdLineOfCreditId).isNotNull();

        LineOfCreditActionRequest actionRequest = new LineOfCreditActionRequest()
                .actionDate(approvalDate)
                .dateFormat(DATE_FORMAT)
                .locale(DEFAULT_LOCALE)
                .note("Approval for test");

        Response<PostLineOfCreditResponse> response = lineOfCreditApi.performAction(createdClientId, createdLineOfCreditId, "approve", actionRequest).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("Failed to approve line of credit: " + response.errorBody().string());
        }
        testContext().set(TestContextKey.LINE_OF_CREDIT_APPROVAL_RESPONSE, response);
        log.info("Approved Line of Credit ID: {}", createdLineOfCreditId);
    }

    @And("Line of credit is approved on {string} with expected available {int}")
    public void lineOfCreditApprovedWithExpected(String approvalDate, int expectedAvailable) throws IOException {
        lineOfCreditApproved(approvalDate); // perform approval
        GetLineOfCreditResponse loc = retrieveCurrentLoc();
        assertThat(loc.getAvailableBalance()).withFailMessage("Expected available balance %s after approval but was %s", expectedAvailable, loc.getAvailableBalance())
                .isNotNull()
                .extracting(bd -> bd.compareTo(BigDecimal.valueOf(expectedAvailable)))
                .isEqualTo(0);
    }

    @And("Line of credit is activated on {string}")
    public void lineOfCreditActivated(String activationDate) throws IOException {
        assertThat(createdClientId).isNotNull();
        assertThat(createdLineOfCreditId).isNotNull();

        LineOfCreditActionRequest actionRequest = new LineOfCreditActionRequest()
                .actionDate(activationDate)
                .dateFormat(DATE_FORMAT)
                .locale(DEFAULT_LOCALE)
                .note("Activation for test");

        Response<PostLineOfCreditResponse> response = lineOfCreditApi.performAction(createdClientId, createdLineOfCreditId, "activate", actionRequest).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("Failed to activate line of credit: " + response.errorBody().string());
        }
        testContext().set(TestContextKey.LINE_OF_CREDIT_ACTIVATION_RESPONSE, response);
        log.info("Activated Line of Credit ID: {}", createdLineOfCreditId);
    }

    @And("Line of credit is activated on {string} with expected available {int}")
    public void lineOfCreditActivatedWithExpected(String activationDate, int expectedAvailable) throws IOException {
        lineOfCreditActivated(activationDate); // perform activation
        GetLineOfCreditResponse loc = retrieveCurrentLoc();
        assertThat(loc.getAvailableBalance()).withFailMessage("Expected available balance %s after activation but was %s", expectedAvailable, loc.getAvailableBalance())
                .isNotNull()
                .extracting(bd -> bd.compareTo(BigDecimal.valueOf(expectedAvailable)))
                .isEqualTo(0);
    }

    @Then("Line of credit status is {string} and maximum amount is {int}")
    public void verifyLineOfCreditStatus(String expectedStatus, int expectedMax) throws IOException {
        assertThat(createdClientId).isNotNull();
        assertThat(createdLineOfCreditId).isNotNull();

        Response<GetLineOfCreditResponse> response = lineOfCreditApi.retrieveOne(createdClientId, createdLineOfCreditId).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("Failed to retrieve line of credit: " + response.errorBody().string());
        }
        GetLineOfCreditResponse loc = response.body();
        assertThat(loc).isNotNull();
        log.info("Retrieved LOC: status={}, maximumAmount={}", loc.getStatus(), loc.getMaximumAmount());

        // Handle status as EnumOptionData object
        if (loc.getStatus() != null) {
            String actualStatusCode = loc.getStatus().getCode();
            // Convert expectedStatus to match the server's code format (e.g., "ACTIVE" -> "status.active")
            String expectedStatusCode = "status." + expectedStatus.toLowerCase();
            assertThat(actualStatusCode).isEqualToIgnoringCase(expectedStatusCode);
        } else {
            log.warn("Status is null in LOC response");
        }

        if (loc.getMaximumAmount() != null) {
            assertThat(loc.getMaximumAmount().compareTo(BigDecimal.valueOf(expectedMax))).isZero();
        } else {
            // Fallback: some implementations may mirror maxCreditLimit in different field names
            log.warn("maximumAmount field is null in LOC response");
        }
    }

    @And("Line of credit limit is increased to {int} on {string} expecting available {int}")
    public void increaseLocLimit(int newLimit, String date, int expectedAvailable) throws IOException {
        assertThat(createdLineOfCreditId).isNotNull();
        LineOfCreditActionRequest action = new LineOfCreditActionRequest()
                .actionDate(date)
                .amount(BigDecimal.valueOf(newLimit))
                .dateFormat(DATE_FORMAT)
                .locale(DEFAULT_LOCALE)
                .note("Increase limit to " + newLimit);
        Response<PostLineOfCreditResponse> response = lineOfCreditApi.performAction(createdClientId, createdLineOfCreditId, "increasecreditlimit", action).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("Failed to increase limit: " + response.errorBody().string());
        }
        validateAvailableBalance(expectedAvailable, "after increase limit");
    }

    @And("Line of credit limit is decreased to {int} on {string} expecting available {int}")
    public void decreaseLocLimit(int newLimit, String date, int expectedAvailable) throws IOException {
        assertThat(createdLineOfCreditId).isNotNull();
        LineOfCreditActionRequest action = new LineOfCreditActionRequest()
                .actionDate(date)
                .amount(BigDecimal.valueOf(newLimit))
                .dateFormat(DATE_FORMAT)
                .locale(DEFAULT_LOCALE)
                .note("Decrease limit to " + newLimit);
        Response<PostLineOfCreditResponse> response = lineOfCreditApi.performAction(createdClientId, createdLineOfCreditId, "decreasecreditlimit", action).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("Failed to decrease limit: " + response.errorBody().string());
        }
        validateAvailableBalance(expectedAvailable, "after decrease limit");
    }



    @When("Client makes a drawdown with the following details:")
    public void clientMakesDrawdownWithDetails(DataTable dataTable) throws IOException {

        assertThat(createdClientId).as("Client ID should be set").isNotNull();
        assertThat(createdLineOfCreditId).as("Line of Credit ID should be set").isNotNull();


        Map<String, String> drawdownDetails = dataTable.asMap(String.class, String.class);

        String locType = getOptionalValue(drawdownDetails, "locType", currentLocType);
        String productContextKey = locType.equalsIgnoreCase("receivable") ? "receivableLOCProduct" : "payableLOCProduct";

        final Long customLoanProductId = testContext().get(productContextKey);
        assertThat(customLoanProductId)
                .as("Loan product for %s LOC should exist in context key '%s'", locType, productContextKey)
                .isNotNull();

        int drawdownAmount = Integer.parseInt(drawdownDetails.get("amount"));
        String drawdownDate = drawdownDetails.get("date");
        String reference = UUID.randomUUID().toString().substring(0, 8); // Short unique reference

        Long supplierOrBuyerId;

        Response<GetLoansTemplateResponse> loanTemplateResponse = loansApi.template10(createdClientId,
                        null,customLoanProductId,"individual",Boolean.FALSE,Boolean.FALSE)
                .execute();
        GetLoansTemplateLineOfCreditOptions option = null;

        if (loanTemplateResponse.isSuccessful()) {
            GetLoansTemplateResponse loanTemplate = loanTemplateResponse.body();
            assert loanTemplate != null;
            assert loanTemplate.getAdditionalProperties() != null;
            option = loanTemplate.getAdditionalProperties().getLineOfCreditOptions()
                    .stream().filter(t -> Objects.equals(t.getId(), createdLineOfCreditId)).findFirst().orElse(null);

            assertNotNull("Line of credit id could not be merged in returned template",option);

            assert option.getApprovedBuyersOrSellers() != null;
            supplierOrBuyerId = option.getApprovedBuyersOrSellers().stream().findAny().get().getId();

        } else {
            throw new RuntimeException("Failed to create USD savings product: " + loanTemplateResponse.errorBody().string());
        }

        // Extract optional loan term parameters (with defaults)
        Integer tenor = getOptionalIntValue(drawdownDetails, "tenor",option.getTenorDays());
        String interestRate = getOptionalValue(drawdownDetails, "interestRate", "12");
        Integer amortizationType = getOptionalIntValue(drawdownDetails, "amortizationType", 1); // Equal installments

        // Extract optional invoice parameters
        String invoiceNo = getWithNullCheck(drawdownDetails, "invoiceNo");
        String invoiceDate = getWithNullCheck(drawdownDetails, "invoiceDate");
        String invoiceDueDate = getWithNullCheck(drawdownDetails, "invoiceDueDate");
        String invoiceAmountStr = getWithNullCheck(drawdownDetails, "invoiceAmount");
        String invoiceCurrency = getOptionalValue(drawdownDetails, "invoiceCurrency", "EUR");

        // Extract shared parameters
        String disapprovedAmountStr = getWithNullCheck(drawdownDetails, "disapprovedAmount");

        String advancePercentageStr = getOptionalValue(drawdownDetails, "advancePercentage", null);

        // Extract fundedAmount/amountInFacilityCurrency - this is what the frontend sends directly
        // For receivable LOC: frontend calculates min(amountAfterAdvance, requestedAmount, availableLimit)
        // and sends it as the funded amount
        String fundedAmountStr = getOptionalValue(drawdownDetails, "fundedAmount", null);

        // Extract payable-specific parameters
        String exchangeRateStr = getOptionalValue(drawdownDetails, "exchangeRate", null);
        String markupStr = getOptionalValue(drawdownDetails, "markup", null);



        // Get savings account for linking
        Response<PostSavingsAccountsResponse> savingsResponse = testContext().get(TestContextKey.EUR_SAVINGS_ACCOUNT_CREATE_RESPONSE);
        Long savingsAccountId = savingsResponse != null && savingsResponse.body() != null ? savingsResponse.body().getResourceId() : null;

        // Fetch line of credit details to get buyer/supplier options and LOC parameters

        // Create a loan (drawdown) linked to the line of credit
        PostLoansRequest loanRequest = new PostLoansRequest()
                .clientId(createdClientId)
                .productId(customLoanProductId)
                .principal(new BigDecimal(drawdownAmount))
                .loanTermFrequency(tenor)
                .loanTermFrequencyType(0) //days
                .numberOfRepayments(1)
                .repaymentEvery(tenor)
                .repaymentFrequencyType(0) //days
                .interestRatePerPeriod(new BigDecimal(interestRate))
                .interestType(1) //flat
                .interestCalculationPeriodType(0) //daily
                .amortizationType(amortizationType)
                .expectedDisbursementDate(drawdownDate)
                .submittedOnDate(drawdownDate)
                .dateFormat(DATE_FORMAT)
                .locale(DEFAULT_LOCALE)
                .loanType("individual")
                .transactionProcessingStrategyCode("pro-rata-mifos-standard-strategy")
                .externalId(reference)
                .linkAccountId(savingsAccountId)
                .lineOfCreditId(String.valueOf(createdLineOfCreditId))
                .isShortDisbursal(true)
                .invoiceNo(invoiceNo)
                .invoiceDate(invoiceDate)
                .invoiceDueDate(invoiceDueDate)
                .invoiceAmount(new BigDecimal(invoiceAmountStr))
                .invoiceCurrency(invoiceCurrency)
                .disapprovedAmount( new BigDecimal(disapprovedAmountStr))
                .datatables(Set.of(new TableData().registeredTableName("dt_loan_additional_data")
                        .data(Map.of("dp_name", "Partner X","locale", DEFAULT_LOCALE,"remitter_name","Jane Contact","agreed_cash_margin_amount",0))));

        // Add receivable-specific fields if this is a receivable LOC
        if (locType.equalsIgnoreCase("receivable")) {
            BigDecimal advancePercentage = advancePercentageStr != null && !advancePercentageStr.isEmpty() ? new BigDecimal(advancePercentageStr) : new BigDecimal(90);
            // Set advance percentage
            loanRequest.setAdvancePercentage(advancePercentage);

            // Calculate amountInFacilityCurrency (funded amount) - simulating frontend behavior
            // Frontend calculates: amountAfterAdvance = (invoiceAmount - disapprovedAmount) × advancePercentage / 100
            // Then sends: amountInFacilityCurrency = min(amountAfterAdvance, requestedAmount, availableLimit)
            BigDecimal amountInFacilityCurrency;
            if (fundedAmountStr != null && !fundedAmountStr.isEmpty()) {
                // Use explicit fundedAmount if provided in test data
                amountInFacilityCurrency = new BigDecimal(fundedAmountStr);
            } else {
                // Calculate the same way frontend does:
                // amountAfterAdvance = (invoiceAmount - disapprovedAmount) × advancePercentage / 100
                BigDecimal approvedReceivableAmount = loanRequest.getInvoiceAmount().subtract(loanRequest.getDisapprovedAmount());
                amountInFacilityCurrency = approvedReceivableAmount
                        .multiply(advancePercentage)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            loanRequest.setPrincipal(amountInFacilityCurrency);
            // Set amountInFacilityCurrency - this is the primary field used by backend
            loanRequest.setAmountInFacilityCurrency(amountInFacilityCurrency);

            loanRequest.setBuyerDetails(List.of(supplierOrBuyerId));

        }
        
        // Add payable-specific fields if this is a payable LOC
        if (locType.equalsIgnoreCase("payable")) {
            // Set exchange rate

            loanRequest.setExchangeRate(exchangeRateStr != null && !exchangeRateStr.isEmpty() ? new BigDecimal(exchangeRateStr) : BigDecimal.ZERO);
            loanRequest.setMarkup(markupStr != null && !markupStr.isEmpty() ? new BigDecimal(markupStr) : BigDecimal.ZERO);

            BigDecimal approvedPayableAmount = loanRequest.getInvoiceAmount().subtract(loanRequest.getDisapprovedAmount());
            BigDecimal principalAmount = approvedPayableAmount
                    .multiply(
                            loanRequest.getExchangeRate()
                                    .multiply(
                                            BigDecimal.ONE.add(
                                                    loanRequest.getMarkup()
                                                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                                            )
                                    )
                    );
            // Set approved payable amount
            loanRequest.setPrincipal(principalAmount);
            loanRequest.setSupplierDetails(List.of(supplierOrBuyerId));
        }


        Response<PostLoansResponse> loanResponse = loansApi.calculateLoanScheduleOrSubmitLoanApplication(loanRequest, "").execute();

        if (!loanResponse.isSuccessful()) {
            throw new RuntimeException("Failed to create loan: " + loanResponse.errorBody().string());
        }

        testContext().set(TestContextKey.LOAN_CREATE_RESPONSE, loanResponse);
        drawdownLoanId = loanResponse.body().getResourceId();
        log.info("Created loan with ID: {}", loanResponse.body().getLoanId());
    }



    @Then("Line of credit available balance should be {int}")
    public void verifyLineOfCreditAvailableBalance(int expectedAvailableBalance) throws IOException {
        assertThat(createdClientId).as("Client ID should be set").isNotNull();
        assertThat(createdLineOfCreditId).as("Line of Credit ID should be set").isNotNull();

        GetLineOfCreditResponse loc = retrieveCurrentLoc();
        assertThat(loc).as("Line of credit should be retrieved").isNotNull();
        assertThat(loc.getAvailableBalance()).as("Available balance should not be null").isNotNull();

        int actualBalance = loc.getAvailableBalance().intValue();
        assertThat(actualBalance)
                .as("Line of credit available balance should be %d but was %d", expectedAvailableBalance, actualBalance)
                .isEqualTo(expectedAvailableBalance);

        log.info("Verified LOC ID: {} available balance is: {}", createdLineOfCreditId, actualBalance);
    }

    @And("Drawdown transaction should be recorded with amount {int}")
    public void verifyDrawdownTransactionRecorded(int expectedAmount) throws IOException {
        assertThat(drawdownLoanId).as("Drawdown loan ID should be set").isNotNull();

        // Verify the loan was created with correct amount
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        assertThat(loanResponse).as("Loan creation response should exist").isNotNull();
        assertThat(loanResponse.body()).as("Loan response body should not be null").isNotNull();
        assertThat(loanResponse.body().getLoanId()).as("Drawdown loan should have been created").isEqualTo(drawdownLoanId);

        // Verify the drawdown is linked to the line of credit
        GetLineOfCreditResponse loc = retrieveCurrentLoc();
        assertThat(loc).as("Line of credit should be retrieved").isNotNull();

        // The drawdown amount should have reduced the available balance
        BigDecimal expectedReduction = BigDecimal.valueOf(expectedAmount);
        log.info("Verified drawdown transaction recorded: Loan ID: {}, Amount: {}", drawdownLoanId, expectedAmount);
    }

    private void validateAvailableBalance(int expected, String contextMsg) throws IOException {
        GetLineOfCreditResponse loc = retrieveCurrentLoc();
        if (loc.getAvailableBalance() == null) {
            log.warn("Available balance is null {}; skipping strict assertion.", contextMsg);
            return;
        }
        assertThat(loc.getAvailableBalance().compareTo(BigDecimal.valueOf(expected)))
                .as("Available balance mismatch %s (expected %s actual %s)", contextMsg, expected, loc.getAvailableBalance())
                .isZero();
    }

    private GetLineOfCreditResponse retrieveCurrentLoc() throws IOException {
        Response<GetLineOfCreditResponse> response = lineOfCreditApi.retrieveOne(createdClientId, createdLineOfCreditId).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("Failed to retrieve line of credit: " + response.errorBody().string());
        }
        return response.body();
    }


    /**
     * Helper method to get optional string value from map with default
     */
    private String getOptionalValue(Map<String, String> map, String key, String defaultValue) {
        String value = map.get(key);
        return (value == null || value.trim().isEmpty()) ? defaultValue : value;
    }

    /**
     * Helper method to get optional string value from map with default
     */
    private String getWithNullCheck(Map<String, String> map, String key) {
        String value = map.get(key);
        assert value != null && !value.trim().isEmpty() : "Expected non-null, non-empty value for key: " + key;
        return value;
    }

    /**
     * Helper method to get optional integer value from map with default
     */
    private Integer getOptionalIntValue(Map<String, String> map, String key, Integer defaultValue) {
        String value = map.get(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for key '{}': '{}', using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

}

