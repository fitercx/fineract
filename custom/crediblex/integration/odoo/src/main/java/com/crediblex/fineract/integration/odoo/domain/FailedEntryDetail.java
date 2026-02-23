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
package com.crediblex.fineract.integration.odoo.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO to hold details of a failed journal entry for Slack notification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedEntryDetail {

    /**
     * The journal entry ID that failed
     */
    private Long journalEntryId;

    /**
     * Associated loan ID
     */
    private Long loanId;

    /**
     * Business event type (DISBURSEMENT, REPAYMENT, ACCRUAL, etc.)
     */
    private String businessEventType;

    /**
     * GL Account code
     */
    private String glAccountCode;

    /**
     * GL Account name
     */
    private String glAccountName;

    /**
     * Transaction amount
     */
    private BigDecimal amount;

    /**
     * Transaction date
     */
    private LocalDate transactionDate;

    /**
     * Error message describing the failure
     */
    private String errorMessage;

    /**
     * Returns a truncated error message for display
     *
     * @param maxLength Maximum length of the error message
     * @return Truncated error message
     */
    public String getTruncatedErrorMessage(int maxLength) {
        if (errorMessage == null) {
            return "Unknown error";
        }
        if (errorMessage.length() <= maxLength) {
            return errorMessage;
        }
        return errorMessage.substring(0, maxLength - 3) + "...";
    }
}
