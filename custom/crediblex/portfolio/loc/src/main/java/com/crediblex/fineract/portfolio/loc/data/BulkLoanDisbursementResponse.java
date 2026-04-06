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

package com.crediblex.fineract.portfolio.loc.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for bulk loan disbursement operation. Contains summary of the bulk operation and individual results
 * for each loan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from bulk loan disbursement operation")
public class BulkLoanDisbursementResponse implements Serializable {

    @Schema(description = "Line of Credit ID for which disbursement was processed")
    private Long lineOfCreditId;

    @Schema(description = "Total number of loans in the request")
    private Integer totalRequested;

    @Schema(description = "Number of loans successfully disbursed")
    private Integer totalSuccessful;

    @Schema(description = "Number of loans that failed to disburse")
    private Integer totalFailed;

    @Schema(description = "Total amount disbursed across all successful loans")
    private BigDecimal totalAmountDisbursed;

    @Schema(description = "Overall status: COMPLETE, PARTIAL, or FAILED")
    private BulkDisbursementStatus status;

    @Schema(description = "List of individual loan disbursement results")
    private List<SingleLoanDisbursementResult> loanResults;

    /**
     * Status of the bulk disbursement operation.
     */
    public enum BulkDisbursementStatus {
        /** All loans were disbursed successfully */
        COMPLETE,
        /** Some loans were disbursed successfully, some failed */
        PARTIAL,
        /** All loans failed to disburse */
        FAILED
    }

    /**
     * Result for a single loan disbursement within the bulk operation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Result of individual loan disbursement")
    public static class SingleLoanDisbursementResult implements Serializable {

        @Schema(description = "Loan ID")
        private Long loanId;

        @Schema(description = "Client ID associated with the loan")
        private Long clientId;

        @Schema(description = "Whether disbursement was successful")
        private boolean success;

        @Schema(description = "Amount disbursed (if successful)")
        private BigDecimal amountDisbursed;

        @Schema(description = "Net amount disbursed (after fees/charges)")
        private BigDecimal netAmountDisbursed;

        @Schema(description = "Transaction ID of the disbursement (if successful)")
        private Long transactionId;

        @Schema(description = "Resource ID returned by the command processing")
        private Long resourceId;

        @Schema(description = "Amount withdrawn from savings (if autoWithdrawFromSavings was true)")
        private BigDecimal withdrawalAmount;

        @Schema(description = "Error code if disbursement failed")
        private String errorCode;

        @Schema(description = "Error message if disbursement failed")
        private String errorMessage;

        @Schema(description = "Loan account number")
        private String loanAccountNo;

        @Schema(description = "Invoice number (for LOC drawdowns)")
        private String invoiceNo;

        /**
         * Creates a successful result.
         */
        public static SingleLoanDisbursementResult success(Long loanId, Long clientId, Long resourceId, BigDecimal amountDisbursed,
                BigDecimal netAmountDisbursed, Long transactionId, String loanAccountNo, String invoiceNo) {
            return SingleLoanDisbursementResult.builder().loanId(loanId).clientId(clientId).success(true).resourceId(resourceId)
                    .amountDisbursed(amountDisbursed).netAmountDisbursed(netAmountDisbursed).transactionId(transactionId)
                    .loanAccountNo(loanAccountNo).invoiceNo(invoiceNo).build();
        }

        /**
         * Creates a failed result.
         */
        public static SingleLoanDisbursementResult failure(Long loanId, Long clientId, String errorCode, String errorMessage,
                String loanAccountNo, String invoiceNo) {
            return SingleLoanDisbursementResult.builder().loanId(loanId).clientId(clientId).success(false).errorCode(errorCode)
                    .errorMessage(errorMessage).loanAccountNo(loanAccountNo).invoiceNo(invoiceNo).build();
        }
    }
}
