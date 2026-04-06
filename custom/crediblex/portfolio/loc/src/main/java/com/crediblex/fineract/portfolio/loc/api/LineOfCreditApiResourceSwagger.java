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

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;

public class LineOfCreditApiResourceSwagger {

    @Schema(description = "GetLineOfCreditsResponse")
    public static final class GetLineOfCreditsResponse {

        private GetLineOfCreditsResponse() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "000000001")
        public String accountNumber;
        @Schema(example = "1")
        public Long clientId;
        @Schema(example = "John Doe")
        public String clientName;
        @Schema(example = "RECEIVABLE")
        public String productType;
        @Schema(example = "100000.00")
        public BigDecimal maximumAmount;
        @Schema(example = "75000.00")
        public BigDecimal availableBalance;
        @Schema(example = "25000.00")
        public BigDecimal consumedAmount;
        public GetLineOfCreditStatusOptions status;
        @Schema(example = "2024-01-01")
        public LocalDate startDate;
        @Schema(example = "2024-12-31")
        public LocalDate endDate;
        @Schema(example = "USD")
        public String currency;
        @Schema(example = "12.5")
        public BigDecimal annualInterestRate;

        public Collection<GetLineOfCreditLoansResponse> loans;
    }

    @Schema(description = "GetLineOfCreditLoansResponse")
    public static final class GetLineOfCreditLoansResponse {

        private GetLineOfCreditLoansResponse() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "000000001")
        public String accountNo;
        @Schema(example = "Individual Loan")
        public String productName;
        @Schema(example = "50000.00")
        public BigDecimal principalAmount;
        @Schema(example = "5000.00")
        public BigDecimal principalOutstanding;
        public GetLineOfCreditStatusOptions status;
    }

    @Schema(description = "GetLineOfCreditTemplateResponse")
    public static final class GetLineOfCreditTemplateResponse {

        private GetLineOfCreditTemplateResponse() {}

        public Collection<GetLineOfCreditProductTypeOptions> productTypeOptions;
        public Collection<GetLineOfCreditStatusOptions> statusOptions;
        public Collection<GetLineOfCreditCashMarginTypeOptions> cashMarginTypeOptions;
        public Collection<GetLineOfCreditInterestChargeTimeOptions> interestChargeTimeOptions;
        public Collection<GetLineOfCreditReviewPeriodsOptions> reviewPeriodsOptions;
        public Collection<GetLineOfCreditLoanOfficersOptions> loanOfficers;
    }

    @Schema(description = "GetLineOfCreditProductTypeOptions")
    public static final class GetLineOfCreditProductTypeOptions {

        private GetLineOfCreditProductTypeOptions() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "TRADE_FINANCE")
        public String code;
        @Schema(example = "Trade Finance")
        public String value;
    }

    @Schema(description = "GetLineOfCreditStatusOptions")
    public static final class GetLineOfCreditStatusOptions {

        private GetLineOfCreditStatusOptions() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "PENDING")
        public String code;
        @Schema(example = "Pending")
        public String value;
    }

    @Schema(description = "GetLineOfCreditCashMarginTypeOptions")
    public static final class GetLineOfCreditCashMarginTypeOptions {

        private GetLineOfCreditCashMarginTypeOptions() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "PERCENTAGE")
        public String code;
        @Schema(example = "Percentage")
        public String value;
    }

    @Schema(description = "GetLineOfCreditInterestChargeTimeOptions")
    public static final class GetLineOfCreditInterestChargeTimeOptions {

        private GetLineOfCreditInterestChargeTimeOptions() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "MONTHLY")
        public String code;
        @Schema(example = "Monthly")
        public String value;
    }

    @Schema(description = "GetLineOfCreditReviewPeriodsOptions")
    public static final class GetLineOfCreditReviewPeriodsOptions {

        private GetLineOfCreditReviewPeriodsOptions() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "30")
        public String code;
        @Schema(example = "30 Days")
        public String value;
    }

    @Schema(description = "GetLineOfCreditRateTypeOptions")
    public static final class GetLineOfCreditRateTypeOptions {

        private GetLineOfCreditRateTypeOptions() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "FIXED")
        public String code;
        @Schema(example = "Fixed")
        public String value;
    }

    @Schema(description = "GetLineOfCreditLoanOfficersOptions")
    public static final class GetLineOfCreditLoanOfficersOptions {

        private GetLineOfCreditLoanOfficersOptions() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "John")
        public String firstname;
        @Schema(example = "Smith")
        public String lastname;
        @Schema(example = "John Smith")
        public String displayName;
    }

    @Schema(description = "GetLineOfCreditResponse")
    public static final class GetLineOfCreditResponse {

        private GetLineOfCreditResponse() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "000000001")
        public String accountNumber;
        @Schema(example = "1")
        public Long clientId;
        @Schema(example = "RECEIVABLE")
        public String productType;
        @Schema(example = "100000.00")
        public BigDecimal maximumAmount;
        @Schema(example = "75000.00")
        public BigDecimal availableBalance;
        @Schema(example = "25000.00")
        public BigDecimal consumedAmount;
        public GetLineOfCreditStatusOptions status;
        @Schema(example = "2024-01-01")
        public LocalDate startDate;
        @Schema(example = "2024-12-31")
        public LocalDate endDate;
        @Schema(example = "100000.00")
        public BigDecimal approvedCreditFacilityAmount;
        @Schema(example = "EXT001")
        public String externalId;
        @Schema(example = "USD")
        public String currency;
        @Schema(example = "80.0")
        public BigDecimal advancePercentage;
        @Schema(example = "365")
        public Integer tenorDays;
        @Schema(example = "Buyer1,Buyer2")
        public String approvedBuyers;
        @Schema(description = "List of approved buyers or suppliers for this LOC")
        public Collection<GetLineOfCreditApprovedBuyerOrSeller> approvedBuyersOrSellers;
        @Schema(example = "PERCENTAGE")
        public String cashMarginType;
        @Schema(example = "10.0")
        public BigDecimal cashMarginValue;
        @Schema(example = "2024-06-01")
        public LocalDate interimReviewDate;
        @Schema(example = "FIXED")
        public String rateType;
        @Schema(example = "MONTHLY")
        public String interestChargeTime;
        @Schema(example = "12.5")
        public BigDecimal annualInterestRate;
        @Schema(example = "ABC Company Ltd")
        public String clientCompanyName;
        @Schema(example = "John Doe")
        public String clientContactPersonName;
        @Schema(example = "+1234567890")
        public String clientContactPersonPhone;
        @Schema(example = "john@abc.com")
        public String clientContactPersonEmail;
        @Schema(example = "Jane Smith")
        public String authorizedSignatoryName;
        @Schema(example = "+1234567891")
        public String authorizedSignatoryPhone;
        @Schema(example = "jane@abc.com")
        public String authorizedSignatoryEmail;
        @Schema(example = "VA123456")
        public String va;
        @Schema(example = "Partner Bank")
        public String distributionPartner;
        @Schema(example = "Special terms apply")
        public String specialConditions;
        @Schema(example = "90")
        public Integer reviewPeriod;
        @Schema(example = "1")
        public Long loanOfficerId;
        @Schema(example = "John Smith")
        public String loanOfficerName;
        @Schema(example = "1")
        public Long settlementSavingsAccountId;
        @Schema(example = "000000001")
        public String settlementSavingsAccountNo;
        @Schema(example = "10000.00")
        public BigDecimal settlementSavingsAccountBalance;

        public Collection<GetLineOfCreditChargesResponse> charges;
        public GetLineOfCreditTimeLineResponse timeLineData;
    }

    @Schema(description = "GetLineOfCreditApprovedBuyerOrSeller")
    public static final class GetLineOfCreditApprovedBuyerOrSeller {

        private GetLineOfCreditApprovedBuyerOrSeller() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "Premium Buyer Ltd")
        public String name;
    }

    @Schema(description = "GetLineOfCreditChargesResponse")
    public static final class GetLineOfCreditChargesResponse {

        private GetLineOfCreditChargesResponse() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "Service Fee")
        public String name;
        @Schema(example = "100.00")
        public BigDecimal amount;
        @Schema(example = "FLAT")
        public String calculationType;
        @Schema(example = "ACTIVE")
        public String status;
    }

    @Schema(description = "GetLineOfCreditTimeLineResponse")
    public static final class GetLineOfCreditTimeLineResponse {

        private GetLineOfCreditTimeLineResponse() {}

        @Schema(example = "2024-01-01")
        public LocalDate submittedOnDate;
        @Schema(example = "admin")
        public String submittedByUsername;
        @Schema(example = "2024-01-02")
        public LocalDate approvedOnDate;
        @Schema(example = "manager")
        public String approvedByUsername;
        @Schema(example = "2024-01-03")
        public LocalDate activatedOnDate;
        @Schema(example = "admin")
        public String activatedByUsername;
        @Schema(example = "2024-12-31")
        public LocalDate closedOnDate;
        @Schema(example = "admin")
        public String closedByUsername;
    }

    @Schema(description = "PostLineOfCreditResponse")
    public static final class PostLineOfCreditResponse {

        private PostLineOfCreditResponse() {}

        @Schema(example = "1")
        public Long officeId;
        @Schema(example = "1")
        public Long clientId;
        @Schema(example = "1")
        public Long resourceId;
        @Schema(example = "1")
        public Long lineOfCreditId;
        public GetLineOfCreditChangesResponse changes;
    }

    @Schema(description = "GetLineOfCreditChangesResponse")
    public static final class GetLineOfCreditChangesResponse {

        private GetLineOfCreditChangesResponse() {}

        @Schema(example = "TRADE_FINANCE")
        public String productType;
        @Schema(example = "100000.00")
        public BigDecimal maximumAmount;
        @Schema(example = "USD")
        public String currency;
        @Schema(example = "12.5")
        public BigDecimal annualInterestRate;
        @Schema(example = "2024-01-01")
        public LocalDate startDate;
        @Schema(example = "2024-12-31")
        public LocalDate endDate;
    }

    @Schema(description = "PutLineOfCreditResponse")
    public static final class PutLineOfCreditResponse {

        private PutLineOfCreditResponse() {}

        @Schema(example = "1")
        public Long officeId;
        @Schema(example = "1")
        public Long clientId;
        @Schema(example = "1")
        public Long resourceId;
        @Schema(example = "1")
        public Long lineOfCreditId;
        public GetLineOfCreditChangesResponse changes;
    }

    @Schema(description = "DeleteLineOfCreditResponse")
    public static final class DeleteLineOfCreditResponse {

        private DeleteLineOfCreditResponse() {}

        @Schema(example = "1")
        public Long officeId;
        @Schema(example = "1")
        public Long clientId;
        @Schema(example = "1")
        public Long resourceId;
        @Schema(example = "1")
        public Long lineOfCreditId;
    }

    @Schema(description = "GetLineOfCreditTransactionsResponse")
    public static final class GetLineOfCreditTransactionsResponse {

        private GetLineOfCreditTransactionsResponse() {}

        public Collection<GetLineOfCreditTransactionResponse> content;
        @Schema(example = "0")
        public Integer number;
        @Schema(example = "20")
        public Integer size;
        @Schema(example = "100")
        public Long totalElements;
        @Schema(example = "5")
        public Integer totalPages;
        @Schema(example = "true")
        public Boolean first;
        @Schema(example = "false")
        public Boolean last;
    }

    @Schema(description = "GetLineOfCreditTransactionResponse")
    public static final class GetLineOfCreditTransactionResponse {

        private GetLineOfCreditTransactionResponse() {}

        @Schema(example = "1")
        public Long id;
        @Schema(example = "1")
        public Long lineOfCreditId;
        @Schema(example = "DISBURSEMENT")
        public String transactionType;
        @Schema(example = "5000.00")
        public BigDecimal amount;
        @Schema(example = "100000.00")
        public BigDecimal balanceBefore;
        @Schema(example = "95000.00")
        public BigDecimal balanceAfter;
        @Schema(example = "2024-01-15")
        public LocalDate transactionDate;
        @Schema(example = "REF001")
        public String referenceNumber;
        @Schema(example = "Loan disbursement")
        public String description;
        @Schema(example = "2024-01-15T10:30:00Z")
        public OffsetDateTime createdOn;
        @Schema(example = "admin")
        public String createdBy;
    }

    @Schema(description = "BulkLoanDisbursementRequest")
    public static final class BulkLoanDisbursementRequest {

        private BulkLoanDisbursementRequest() {}

        @Schema(description = "List of individual loan disbursement requests", required = true)
        public Collection<SingleLoanDisbursementRequest> loans;
        @Schema(description = "Actual disbursement date for all loans", example = "2024-01-15")
        public LocalDate actualDisbursementDate;
        @Schema(description = "Date format pattern", example = "yyyy-MM-dd")
        public String dateFormat;
        @Schema(description = "Locale for formatting", example = "en")
        public String locale;
        @Schema(description = "Payment type ID", example = "1")
        public Long paymentTypeId;
        @Schema(description = "Whether to auto-withdraw from savings", example = "false")
        public Boolean autoWithdrawFromSavings;
        @Schema(description = "Payment type ID for withdrawal", example = "1")
        public Long withdrawalPaymentTypeId;
        @Schema(description = "Note for all disbursements")
        public String note;
    }

    @Schema(description = "SingleLoanDisbursementRequest")
    public static final class SingleLoanDisbursementRequest {

        private SingleLoanDisbursementRequest() {}

        @Schema(description = "Loan ID to disburse", required = true, example = "1")
        public Long loanId;
        @Schema(description = "Override disbursement date for this loan", example = "2024-01-15")
        public LocalDate actualDisbursementDate;
        @Schema(description = "Transaction amount", example = "10000.00")
        public BigDecimal transactionAmount;
        @Schema(description = "Disburse in invoice currency", example = "false")
        public Boolean disburseInInvoiceCurrency;
        @Schema(description = "Override withdrawal amount", example = "9500.00")
        public BigDecimal withdrawalAmount;
        @Schema(description = "External transaction ID", example = "EXT-001")
        public String externalId;
        @Schema(description = "Note for this loan")
        public String note;
    }

    @Schema(description = "BulkLoanDisbursementResponse")
    public static final class BulkLoanDisbursementResponse {

        private BulkLoanDisbursementResponse() {}

        @Schema(description = "Line of Credit ID", example = "1")
        public Long lineOfCreditId;
        @Schema(description = "Total loans requested", example = "5")
        public Integer totalRequested;
        @Schema(description = "Total successful disbursements", example = "4")
        public Integer totalSuccessful;
        @Schema(description = "Total failed disbursements", example = "1")
        public Integer totalFailed;
        @Schema(description = "Total amount disbursed", example = "50000.00")
        public BigDecimal totalAmountDisbursed;
        @Schema(description = "Overall status: COMPLETE, PARTIAL, or FAILED", example = "PARTIAL")
        public String status;
        @Schema(description = "Individual loan disbursement results")
        public Collection<SingleLoanDisbursementResult> loanResults;
    }

    @Schema(description = "SingleLoanDisbursementResult")
    public static final class SingleLoanDisbursementResult {

        private SingleLoanDisbursementResult() {}

        @Schema(description = "Loan ID", example = "1")
        public Long loanId;
        @Schema(description = "Client ID", example = "1")
        public Long clientId;
        @Schema(description = "Whether disbursement succeeded", example = "true")
        public Boolean success;
        @Schema(description = "Amount disbursed", example = "10000.00")
        public BigDecimal amountDisbursed;
        @Schema(description = "Net amount disbursed", example = "9500.00")
        public BigDecimal netAmountDisbursed;
        @Schema(description = "Transaction ID", example = "123")
        public Long transactionId;
        @Schema(description = "Resource ID", example = "1")
        public Long resourceId;
        @Schema(description = "Withdrawal amount", example = "9500.00")
        public BigDecimal withdrawalAmount;
        @Schema(description = "Error code if failed", example = "error.msg.loan.not.approved")
        public String errorCode;
        @Schema(description = "Error message if failed")
        public String errorMessage;
        @Schema(description = "Loan account number", example = "000000001")
        public String loanAccountNo;
        @Schema(description = "Invoice number", example = "INV-001")
        public String invoiceNo;
    }
}
