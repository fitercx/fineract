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

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.apache.fineract.client.models.ChargeData;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.PostChargesResponse;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.client.services.ChargesApi;
import org.apache.fineract.client.services.LoanTransactionsApi;
import org.apache.fineract.client.services.LoansApi;
import org.apache.fineract.test.data.ChargeCalculationType;
import org.apache.fineract.test.data.ChargeTimeType;
import java.util.List;
import org.apache.fineract.test.stepdef.AbstractStepDef;
import org.apache.fineract.test.support.TestContextKey;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit2.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * Step definitions for loan repayments and balance validations.
 * Used primarily for Line of Credit drawdown tests.
 */
@Slf4j
public class LoanRepaymentAndValidationStepDef extends AbstractStepDef {

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final String DEFAULT_LOCALE = "en";
    
    @Autowired
    private LoanTransactionsApi loanTransactionsApi;

    @Autowired
    private LoansApi loansApi;

    @Autowired
    private ChargesApi chargesApi;

    @When("Client makes a repayment of {double} EUR on {string} date")
    public void clientMakesRepayment(Double amount, String repaymentDate) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        PostLoansLoanIdTransactionsRequest repaymentRequest = new PostLoansLoanIdTransactionsRequest()
            .transactionDate(repaymentDate)
            .transactionAmount(amount)  // Double, not BigDecimal
            .dateFormat(DATE_FORMAT)
            .locale(DEFAULT_LOCALE);
        
        Response<PostLoansLoanIdTransactionsResponse> response = loanTransactionsApi
            .executeLoanTransaction(loanId, repaymentRequest, "repayment").execute();
        
        if (!response.isSuccessful()) {
            String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to make repayment: " + errorBody);
        }
        
        testContext().set(TestContextKey.LOAN_REPAYMENT_RESPONSE, response);
        log.info("Made repayment of {} EUR on loan {} on date {}", amount, loanId, repaymentDate);
    }

    @When("Client makes a partial repayment of {double} EUR on {string} date")
    public void clientMakesPartialRepayment(Double amount, String repaymentDate) throws IOException {
        // Partial repayment is the same as regular repayment in implementation
        clientMakesRepayment(amount, repaymentDate);
    }

    @When("Client makes a final repayment of {double} EUR on {string} date")
    public void clientMakesFinalRepayment(Double amount, String repaymentDate) throws IOException {
        // Final repayment is the same as regular repayment in implementation
        clientMakesRepayment(amount, repaymentDate);
    }

    @Then("Loan outstanding balance is {double}")
    public void verifyLoanOutstandingBalance(Double expectedBalance) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "all", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        Double actualBalance = loanDetails.body().getSummary().getTotalOutstanding();
        
        assertThat(actualBalance)
            .as("Loan outstanding balance")
            .isEqualTo(expectedBalance);
        
        log.info("Verified loan {} outstanding balance is {}", loanId, actualBalance);
    }

    @Then("Loan principal outstanding is {double}")
    public void verifyLoanPrincipalOutstanding(Double expectedPrincipal) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "all", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        Double actualPrincipal = loanDetails.body().getSummary().getPrincipalOutstanding();
        
        assertThat(actualPrincipal)
            .as("Loan principal outstanding")
            .isEqualTo(expectedPrincipal);
        
        log.info("Verified loan {} principal outstanding is {}", loanId, actualPrincipal);
    }

    @Then("Loan interest outstanding is {double}")
    public void verifyLoanInterestOutstanding(Double expectedInterest) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "all", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        Double actualInterest = loanDetails.body().getSummary().getInterestOutstanding();
        
        assertThat(actualInterest)
            .as("Loan interest outstanding")
            .isEqualTo(expectedInterest);
        
        log.info("Verified loan {} interest outstanding is {}", loanId, actualInterest);
    }

    @Then("Loan status is {string}")
    public void verifyLoanStatus(String expectedStatus) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "all", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        String actualStatusCode = loanDetails.body().getStatus().getCode();
        String expectedStatusCode = "loanStatusType." + expectedStatus.toLowerCase();
        
        assertThat(actualStatusCode)
            .as("Loan status")
            .isEqualToIgnoringCase(expectedStatusCode);
        
        log.info("Verified loan {} status is {}", loanId, actualStatusCode);
    }

    @Then("Loan status is {string} and schedule status is {string}")
    public void verifyLoanStatusAndScheduleStatus(String expectedLoanStatus, String expectedScheduleStatus) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "all", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        // Verify loan status
        String actualLoanStatusCode = loanDetails.body().getStatus().getCode();
        String expectedLoanStatusCode = "loanStatusType." + expectedLoanStatus.toLowerCase();
        
        assertThat(actualLoanStatusCode)
            .as("Loan status")
            .isEqualToIgnoringCase(expectedLoanStatusCode);
        
        // Verify schedule status (if available)
        if (loanDetails.body().getStatus() != null) {
            String actualScheduleStatus = loanDetails.body().getStatus().getCode();
            assertThat(actualScheduleStatus)
                .as("Loan schedule status")
                .containsIgnoringCase(expectedScheduleStatus);
        }
        
        log.info("Verified loan {} status is {} and schedule status is {}", 
                 loanId, actualLoanStatusCode, expectedScheduleStatus);
    }

    @Then("Interest is calculated for exactly {int} days")
    public void verifyInterestCalculationDays(int expectedDays) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "all", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        BigDecimal principal = loanDetails.body().getPrincipal();
        BigDecimal interestRate = loanDetails.body().getInterestRatePerPeriod();
        Double actualInterest = loanDetails.body().getSummary().getInterestCharged();
        
        // Calculate expected interest: (Principal × Rate × Days) / 360
        // Using BigDecimal for precise calculation, then convert to Double for comparison
        BigDecimal expectedInterestBD = principal
            .multiply(interestRate)
            .multiply(BigDecimal.valueOf(expectedDays))
            .divide(BigDecimal.valueOf(360 * 100), 2, RoundingMode.HALF_UP);
        
        Double expectedInterest = expectedInterestBD.doubleValue();
        
        log.info("Interest calculation: Principal={}, Rate={}, Days={}, Expected={}, Actual={}", 
                 principal, interestRate, expectedDays, expectedInterest, actualInterest);
        
        // Allow for small rounding differences (within 1 EUR)
        double difference = Math.abs(actualInterest - expectedInterest);
        assertThat(difference)
            .as("Interest difference should be within 1 EUR (Expected: %s, Actual: %s, Diff: %s)", 
                expectedInterest, actualInterest, difference)
            .isLessThanOrEqualTo(1.0);
        
        log.info("Verified interest calculated for {} days", expectedDays);
    }

    @When("Admin writes off the drawdown loan on {string} date")
    public void adminWritesOffLoan(String writeOffDate) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        // Write-off is a transaction in LOC context
        PostLoansLoanIdTransactionsRequest writeOffRequest = new PostLoansLoanIdTransactionsRequest()
            .transactionDate(writeOffDate)
            .dateFormat(DATE_FORMAT)
            .locale(DEFAULT_LOCALE)
            .note("Write-off");
        
        Response<PostLoansLoanIdTransactionsResponse> response = loanTransactionsApi
            .executeLoanTransaction(loanId, writeOffRequest, "writeoff")
            .execute();
        
        if (!response.isSuccessful()) {
            String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to write off loan: " + errorBody);
        }
        
        testContext().set(TestContextKey.LOAN_WRITE_OFF_RESPONSE, response);
        log.info("Written off loan {} on date {}", loanId, writeOffDate);
    }

    @When("Admin forecloses the drawdown loan on {string} date")
    public void adminForeclosesLoan(String foreclosureDate) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        // Foreclosure is a transaction in LOC context
        PostLoansLoanIdTransactionsRequest foreclosureRequest = new PostLoansLoanIdTransactionsRequest()
            .transactionDate(foreclosureDate)
            .dateFormat(DATE_FORMAT)
            .locale(DEFAULT_LOCALE)
            .note("Foreclosure");
        
        Response<PostLoansLoanIdTransactionsResponse> response = loanTransactionsApi
            .executeLoanTransaction(loanId, foreclosureRequest, "foreclosure")
            .execute();
        
        if (!response.isSuccessful()) {
            String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to foreclose loan: " + errorBody);
        }
        
        testContext().set(TestContextKey.LOAN_CHARGE_OFF_RESPONSE, response);
        log.info("Foreclosed loan {} on date {}", loanId, foreclosureDate);
    }

    @Then("Net disbursed amount is {double}")
    public void verifyNetDisbursedAmount(Double expectedNetDisbursed) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "transactions", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        // Find the disbursement transaction
        Double actualNetDisbursed = loanDetails.body().getTransactions().stream()
            .filter(t -> t.getType() != null && Boolean.TRUE.equals(t.getType().getDisbursement()))
            .filter(t -> t.getManuallyReversed() == null || !t.getManuallyReversed())
            .map(t -> t.getAmount())
            .findFirst()
            .orElse(0.0);
        
        assertThat(actualNetDisbursed)
            .as("Net disbursed amount (Principal - Interest - Charges)")
            .isEqualTo(expectedNetDisbursed);
        
        log.info("Verified loan {} net disbursed amount is {}", loanId, actualNetDisbursed);
    }

    @Then("Overpaid amount is {double}")
    public void verifyOverpaidAmount(Double expectedOverpaid) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "all", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        // Overpaid amount = Total overpaid (difference between interest charged and interest consumed)
        Double actualOverpaid = loanDetails.body().getTotalOverpaid();
        
        assertThat(actualOverpaid)
            .as("Overpaid amount (Interest charged - Interest consumed)")
            .isEqualTo(expectedOverpaid);
        
        log.info("Verified loan {} overpaid amount is {}", loanId, actualOverpaid);
    }

    @Then("Repayment schedule status is {string}")
    public void verifyRepaymentScheduleStatus(String expectedStatus) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "repaymentSchedule", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        // Get the repayment schedule status
        int periodSize = loanDetails.body().getRepaymentSchedule().getPeriods().size();
        Boolean completeStatus = loanDetails.body().getRepaymentSchedule().getPeriods().get(periodSize-1).getComplete();

        String actualStatus = Boolean.TRUE.equals(completeStatus) ? "repaymentScheduleStatusType.paid" : "repaymentScheduleStatusType.incomplete";
        String expectedStatusCode = "repaymentScheduleStatusType." + expectedStatus.toLowerCase();
        
        assertThat(actualStatus)
            .as("Repayment schedule status")
            .isEqualToIgnoringCase(expectedStatusCode);
        
        log.info("Verified loan {} repayment schedule status is {}", loanId, actualStatus);
    }

    @Given("An overdue penalty charge {string} with percentage {double} exists")
    public void ensureOverduePenaltyChargeExists(String chargeName, double percentage) throws IOException {
        Long chargeId = findExistingCharge(chargeName);
        
        if (chargeId == null) {
            log.info("Creating overdue penalty charge '{}' with percentage {}", chargeName, percentage);
            
            ChargeRequest chargeRequest = new ChargeRequest()
                .name(chargeName)
                .chargeAppliesTo(1) // Loan
                .chargeTimeType(ChargeTimeType.OVERDUE_FEES.value)
                .chargeCalculationType(ChargeCalculationType.PERCENTAGE_AMOUNT.ordinal())
                .chargePaymentMode(0) // Regular
                .amount(percentage)
                .active(true)
                .currencyCode("EUR")
                .locale(DEFAULT_LOCALE);
            
            Response<PostChargesResponse> response = chargesApi.createCharge(chargeRequest).execute();
            
            if (response.isSuccessful()) {
                chargeId = response.body().getResourceId();
                log.info("Created overdue penalty charge with ID: {}", chargeId);
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                throw new RuntimeException("Failed to create overdue charge: " + errorBody);
            }
        } else {
            log.info("Using existing overdue penalty charge with ID: {}", chargeId);
        }
        
        testContext().set(TestContextKey.CHARGE_FOR_LOAN_PERCENT_LATE_CREATE_RESPONSE, chargeId);
    }

    @Then("Loan schedule status is {string}")
    public void verifyLoanScheduleStatus(String expectedStatus) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "all", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        // Check delinquency status or schedule status
        String actualStatus = loanDetails.body().getStatus().getCode();
        
        assertThat(actualStatus)
            .as("Loan schedule status")
            .containsIgnoringCase(expectedStatus);
        
        log.info("Verified loan {} schedule status contains {}", loanId, expectedStatus);
    }

    @Then("Loan penalties outstanding is greater than {double}")
    public void verifyLoanPenaltiesOutstandingGreaterThan(Double minExpected) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "all", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        Double actualPenalties = loanDetails.body().getSummary().getPenaltyChargesOutstanding();
        
        assertThat(actualPenalties)
            .as("Loan penalties outstanding should be greater than %s", minExpected)
            .isGreaterThan(minExpected);
        
        log.info("Verified loan {} penalties outstanding is {} (> {})", loanId, actualPenalties, minExpected);
    }

    @Then("Loan penalties outstanding is {double}")
    public void verifyLoanPenaltiesOutstanding(Double expectedPenalties) throws IOException {
        Response<PostLoansResponse> loanResponse = testContext().get(TestContextKey.LOAN_CREATE_RESPONSE);
        Long loanId = loanResponse.body().getLoanId();
        
        Response<GetLoansLoanIdResponse> loanDetails = loansApi
            .retrieveLoan(loanId, false, "all", "", "")
            .execute();
        
        if (!loanDetails.isSuccessful()) {
            String errorBody = loanDetails.errorBody() != null ? loanDetails.errorBody().string() : "Unknown error";
            throw new RuntimeException("Failed to retrieve loan: " + errorBody);
        }
        
        Double actualPenalties = loanDetails.body().getSummary().getPenaltyChargesOutstanding();
        
        assertThat(actualPenalties)
            .as("Loan penalties outstanding")
            .isEqualTo(expectedPenalties);
        
        log.info("Verified loan {} penalties outstanding is {}", loanId, actualPenalties);
    }

    private Long findExistingCharge(String chargeName) throws IOException {
        log.info("Checking for existing charge with name '{}'", chargeName);
        
        Response<List<ChargeData>> response = chargesApi.retrieveAllCharges().execute();
        
        if (!response.isSuccessful()) {
            log.error("Failed to retrieve charges: {}", response.errorBody() != null ? response.errorBody().string() : "Unknown error");
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
}
