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
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for bulk loan disbursement under a Line of Credit. Contains a list of loan IDs to disburse along with
 * common disbursement parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for bulk disbursement of multiple loans under a Line of Credit")
public class BulkLoanDisbursementRequest implements Serializable {

    @Schema(description = "List of individual loan disbursement requests", required = true)
    private List<SingleLoanDisbursementRequest> loans;

    @Schema(description = "Actual disbursement date for all loans (if not specified per loan)", example = "2024-01-15")
    private LocalDate actualDisbursementDate;

    @Schema(description = "Date format pattern", example = "yyyy-MM-dd")
    private String dateFormat;

    @Schema(description = "Locale for formatting", example = "en")
    private String locale;

    @Schema(description = "Payment type ID for the disbursement", example = "1")
    private Long paymentTypeId;

    @Schema(description = "Whether to auto-withdraw from savings after disbursement")
    private Boolean autoWithdrawFromSavings;

    @Schema(description = "Payment type ID for withdrawal transaction (required if autoWithdrawFromSavings is true)")
    private Long withdrawalPaymentTypeId;

    @Schema(description = "Note to be added to all disbursement transactions")
    private String note;

    /**
     * Individual loan disbursement request within a bulk operation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual loan disbursement request")
    public static class SingleLoanDisbursementRequest implements Serializable {

        @Schema(description = "Loan ID to disburse", required = true, example = "1")
        private Long loanId;

        @Schema(description = "Override actual disbursement date for this specific loan", example = "2024-01-15")
        private LocalDate actualDisbursementDate;

        @Schema(description = "Transaction amount (optional, defaults to approved amount)", example = "10000.00")
        private BigDecimal transactionAmount;

        @Schema(description = "Whether to disburse in invoice currency (for LOC loans)", example = "false")
        private Boolean disburseInInvoiceCurrency;

        @Schema(description = "Override withdrawal amount for this loan")
        private BigDecimal withdrawalAmount;

        @Schema(description = "External transaction ID")
        private String externalId;

        @Schema(description = "Note specific to this loan disbursement")
        private String note;
    }
}
