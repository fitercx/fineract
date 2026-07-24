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
package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the CrediblEX "overdue amounts collected" list ({@code GET /loans/crediblex/overdue/collected}): a single
 * non-reversed repayment/recovery transaction that both reduced a past-due installment and collected some LPI
 * (penalty). The amounts are the transaction-level portions. Callers (loan service / portal) aggregate these rows
 * loan-wise, client-wise, and over date windows (e.g. last 7 / 30 days) by filtering on {@code transactionDate}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredXOverdueCollectedData {

    private Long clientId;
    private String clientAccountNo;
    private String clientExternalId;
    private String clientName;
    private Long loanId;
    private String loanAccountNo;
    /** Loan status id at query time (e.g. 300 active, 600 closed-obligations-met, 601 closed-written-off). */
    private Integer loanStatusId;
    private String closedOnDate;
    private Long transactionId;
    private String transactionDate;
    /** Whole transaction amount ({@code m_loan_transaction.amount}). */
    private BigDecimal totalPaid;
    private BigDecimal principalPaid;
    private BigDecimal interestPaid;
    private BigDecimal feesPaid;
    /** Late-payment interest (penalty) collected in this transaction. */
    private BigDecimal lpiPaid;
    /** Largest number of days a paid installment was past due at the time of this payment. */
    private Integer maxDaysOverdueAtPayment;
}
