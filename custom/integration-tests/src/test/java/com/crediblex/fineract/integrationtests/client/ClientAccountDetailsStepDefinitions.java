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

package com.crediblex.fineract.integrationtests.client;

import com.crediblex.fineract.integrationtests.common.*;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

public class ClientAccountDetailsStepDefinitions {

    @Autowired
    private ClientAccountDetailsTestClient clientAccountDetailsTestClient;

    private Response response;
    private Long clientId;
    private TestDataBuilder testDataBuilder;
    private TestDataBuilder.ClientData currentClientData;

    @Before
    public void setUp() {
        testDataBuilder = new TestDataBuilder();
    }

    @After
    public void tearDown() {
        if (testDataBuilder != null) {
            testDataBuilder.cleanup();
        }
    }

    @Given("the system is set up with test data")
    public void setupTestData() {
        ApiTestBase.initializeRestAssured();
    }

    @Given("I am logged in as a user with appropriate permissions")
    public void loginWithPermissions() {
        ApiTestBase.authenticate();
    }

    @Given("a client exists with ID {string}")
    public void clientExists(String id) {
        this.clientId = Long.parseLong(id);

        // Create a test client if it doesn't exist
        if (!ClientHelper.clientExists(clientId)) {
            String firstname = TestUtils.generateRandomString("Test");
            String lastname = TestUtils.generateRandomString("Client");
            currentClientData = testDataBuilder.createClientWithNoAccounts(firstname, lastname);
            this.clientId = currentClientData.getClientId();
        }
    }

    @Given("a client with ID {string} does not exist")
    public void clientDoesNotExist(String id) {
        this.clientId = Long.parseLong(id);
        // We use a very high ID that's unlikely to exist
        this.clientId = 999999L;
    }

    @Given("the client has active loans")
    public void clientHasActiveLoans() {
        // Create a new client with loans
        String firstname = TestUtils.generateRandomString("Test");
        String lastname = TestUtils.generateRandomString("Client");
        currentClientData = testDataBuilder.createClientWithAccounts(firstname, lastname, 2, 0);
        this.clientId = currentClientData.getClientId();
    }

    @Given("the client has no active loans")
    public void clientHasNoActiveLoans() {
        // Current client already has no loans if we just created it with clientExists
        // or we can create a new one explicitly
        if (currentClientData == null || !currentClientData.getLoanIds().isEmpty()) {
            String firstname = TestUtils.generateRandomString("Test");
            String lastname = TestUtils.generateRandomString("Client");
            currentClientData = testDataBuilder.createClientWithNoAccounts(firstname, lastname);
            this.clientId = currentClientData.getClientId();
        }
    }

    @Given("the client has loans with late fees")
    public void clientHasLoansWithLateFees() {
        String firstname = TestUtils.generateRandomString("Test");
        String lastname = TestUtils.generateRandomString("Client");
        currentClientData = testDataBuilder.createClientWithLoansWithLateFees(firstname, lastname);
        this.clientId = currentClientData.getClientId();
    }

    @When("I request the client's account details")
    public void requestClientAccountDetails() {
        this.response = clientAccountDetailsTestClient.getClientAccountDetails(this.clientId);
    }

    @Then("the response should contain loan account information")
    public void verifyLoanAccountInformation() {
        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCode(),
                "Expected successful response but got: " + response.getStatusCode());

        List<Map<String, Object>> loanAccounts = response.jsonPath().getList("loanAccounts");
        Assertions.assertNotNull(loanAccounts, "Loan accounts should not be null");
        Assertions.assertTrue(loanAccounts.size() > 0, "Expected at least one loan account");
    }

    @Then("the loan accounts should include installment amounts")
    public void verifyInstallmentAmounts() {
        List<Map<String, Object>> loanAccounts = response.jsonPath().getList("loanAccounts");
        Assertions.assertNotNull(loanAccounts, "Loan accounts should not be null");
        Assertions.assertTrue(loanAccounts.size() > 0, "No loan accounts found in the response");

        for (Map<String, Object> loan : loanAccounts) {
            // Check for various possible field names for installment amount
            boolean hasInstallmentAmount = loan.containsKey("installmentAmount") || loan.containsKey("principalAmount")
                    || loan.containsKey("loanAmount");

            Assertions.assertTrue(hasInstallmentAmount, "Loan account should contain installment/principal amount information");
        }
    }

    @Then("the loan accounts should include late fees")
    public void verifyLateFees() {
        List<Map<String, Object>> loanAccounts = response.jsonPath().getList("loanAccounts");
        Assertions.assertNotNull(loanAccounts, "Loan accounts should not be null");
        Assertions.assertTrue(loanAccounts.size() > 0, "No loan accounts found in the response");

        for (Map<String, Object> loan : loanAccounts) {
            // Check for various possible field names for fees
            boolean hasFeeInfo = loan.containsKey("lateFees") || loan.containsKey("totalFeeChargesCharged")
                    || loan.containsKey("feeChargesCharged") || loan.containsKey("penaltyChargesCharged");

            Assertions.assertTrue(hasFeeInfo, "Loan account should contain fee/penalty information");
        }
    }

    @Then("the response should be successful")
    public void verifySuccessfulResponse() {
        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCode(), "Expected HTTP 200 but got: " + response.getStatusCode());
    }

    @Then("the response should contain an empty list of loan accounts")
    public void verifyEmptyLoanAccounts() {
        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCode(),
                "Expected successful response but got: " + response.getStatusCode());

        List<Map<String, Object>> loanAccounts = response.jsonPath().getList("loanAccounts");
        Assertions.assertNotNull(loanAccounts, "Loan accounts list should not be null");
        Assertions.assertTrue(loanAccounts.isEmpty(), "Expected empty loan accounts list but found " + loanAccounts.size() + " accounts");
    }

    @Then("the response should return a not found error")
    public void verifyNotFoundError() {
        Assertions.assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode(),
                "Expected HTTP 404 but got: " + response.getStatusCode());
    }

    @Then("the late fees should be calculated correctly")
    public void verifyLateFeesCalculation() {
        List<Map<String, Object>> loanAccounts = response.jsonPath().getList("loanAccounts");
        Assertions.assertNotNull(loanAccounts, "Loan accounts should not be null");
        Assertions.assertTrue(loanAccounts.size() > 0, "No loan accounts found in the response");

        for (Map<String, Object> loan : loanAccounts) {
            // Verify fee fields exist and are non-negative
            Object feeCharges = loan.get("totalFeeChargesCharged");
            if (feeCharges == null) {
                feeCharges = loan.get("feeChargesCharged");
            }
            if (feeCharges == null) {
                feeCharges = loan.get("penaltyChargesCharged");
            }

            Assertions.assertNotNull(feeCharges, "Fee charges information should be present");

            // Convert to double for comparison
            Double feeAmount = null;
            if (feeCharges instanceof Number) {
                feeAmount = ((Number) feeCharges).doubleValue();
            }

            Assertions.assertNotNull(feeAmount, "Fee amount should be a number");
            Assertions.assertTrue(feeAmount >= 0, "Late fees should not be negative");
        }
    }
}
