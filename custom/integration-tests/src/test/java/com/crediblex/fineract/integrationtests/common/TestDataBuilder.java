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
import java.util.ArrayList;
import java.util.List;

/**
 * Builder class for setting up test data scenarios
 */
public class TestDataBuilder {

    private Long officeId;
    private final List<Long> createdClientIds = new ArrayList<>();
    private final List<Long> createdLoanIds = new ArrayList<>();
    private final List<Long> createdSavingsIds = new ArrayList<>();

    /**
     * Initialize with default office
     */
    public TestDataBuilder() {
        this.officeId = OfficeHelper.getHeadOfficeId();
    }

    /**
     * Set office ID
     */
    public TestDataBuilder withOffice(Long officeId) {
        this.officeId = officeId;
        return this;
    }

    /**
     * Create a client with loans and savings
     */
    public ClientData createClientWithAccounts(String firstname, String lastname, int numberOfLoans, int numberOfSavings) {
        // Create client
        Response clientResponse = ClientHelper.createBasicClient(firstname, lastname, officeId);
        Long clientId = ClientHelper.extractClientId(clientResponse);
        createdClientIds.add(clientId);

        ClientData clientData = new ClientData(clientId);

        // Create loans
        Long loanProductId = LoanHelper.getFirstLoanProductId();
        for (int i = 0; i < numberOfLoans; i++) {
            BigDecimal principal = new BigDecimal(10000 + (i * 5000));
            Response loanResponse = LoanHelper.createBasicLoan(clientId, loanProductId, principal);
            if (loanResponse.getStatusCode() == 200) {
                Long loanId = LoanHelper.extractLoanId(loanResponse);
                createdLoanIds.add(loanId);
                clientData.addLoanId(loanId);

                // Approve and disburse loan
                LoanHelper.approveLoan(loanId, TestUtils.getCurrentDate());
                LoanHelper.disburseLoan(loanId, TestUtils.getCurrentDate(), principal);
            }
        }

        // Create savings accounts
        Long savingsProductId = SavingsHelper.getFirstSavingsProductId();
        for (int i = 0; i < numberOfSavings; i++) {
            Response savingsResponse = SavingsHelper.createBasicSavingsAccount(clientId, savingsProductId);
            if (savingsResponse.getStatusCode() == 200) {
                Long savingsId = SavingsHelper.extractSavingsId(savingsResponse);
                createdSavingsIds.add(savingsId);
                clientData.addSavingsId(savingsId);

                // Approve and activate savings account
                SavingsHelper.approveSavingsAccount(savingsId, TestUtils.getCurrentDate());
                SavingsHelper.activateSavingsAccount(savingsId, TestUtils.getCurrentDate());

                // Make initial deposit
                BigDecimal depositAmount = new BigDecimal(1000 + (i * 500));
                SavingsHelper.deposit(savingsId, depositAmount, TestUtils.getCurrentDate());
            }
        }

        return clientData;
    }

    /**
     * Create a client with no accounts
     */
    public ClientData createClientWithNoAccounts(String firstname, String lastname) {
        Response clientResponse = ClientHelper.createBasicClient(firstname, lastname, officeId);
        Long clientId = ClientHelper.extractClientId(clientResponse);
        createdClientIds.add(clientId);

        return new ClientData(clientId);
    }

    /**
     * Create a client with loans that have late fees
     */
    public ClientData createClientWithLoansWithLateFees(String firstname, String lastname) {
        Response clientResponse = ClientHelper.createBasicClient(firstname, lastname, officeId);
        Long clientId = ClientHelper.extractClientId(clientResponse);
        createdClientIds.add(clientId);

        ClientData clientData = new ClientData(clientId);

        // Create loan with late fees
        Long loanProductId = LoanHelper.getFirstLoanProductId();
        Response loanResponse = LoanHelper.createLoanWithLateFees(clientId, loanProductId);
        if (loanResponse.getStatusCode() == 200) {
            Long loanId = loanResponse.jsonPath().getLong("id");
            createdLoanIds.add(loanId);
            clientData.addLoanId(loanId);
        }

        return clientData;
    }

    /**
     * Clean up all created test data
     */
    public void cleanup() {
        // Note: In a real implementation, you would delete the created entities
        // For now, we'll just clear the lists
        createdClientIds.clear();
        createdLoanIds.clear();
        createdSavingsIds.clear();
    }

    /**
     * Data class to hold client information
     */
    public static class ClientData {

        private final Long clientId;
        private final List<Long> loanIds = new ArrayList<>();
        private final List<Long> savingsIds = new ArrayList<>();

        public ClientData(Long clientId) {
            this.clientId = clientId;
        }

        public Long getClientId() {
            return clientId;
        }

        public List<Long> getLoanIds() {
            return new ArrayList<>(loanIds);
        }

        public List<Long> getSavingsIds() {
            return new ArrayList<>(savingsIds);
        }

        public void addLoanId(Long loanId) {
            loanIds.add(loanId);
        }

        public void addSavingsId(Long savingsId) {
            savingsIds.add(savingsId);
        }
    }
}
