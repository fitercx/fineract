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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredXOverdueLoanData {

    private Long loanId;
    private Long clientId;
    private String accountNo;
    private String borrowerName;
    private Long loanOfficerId;
    private String loanOfficerName;
    private Long productId;
    private String productName;
    private String invoiceNumber;
    private String currencyCode;
    private String disbursementDate;
    private BigDecimal loanAmount;
    private Boolean isOverdue;
    /** Whole-loan remaining balance (from the loan's {@code *_outstanding_derived} columns); includes fees. */
    private CredXOverdueAmountBreakdown outstanding;
    /** Past-due installments' amount (includes fees); all components are zero when the loan is not overdue. */
    private CredXOverdueAmountBreakdown overdue;
    private BigDecimal excessAmount;
    private Integer maxDpd;
    private List<CredXOverdueInstallmentData> overdueInstallments;
}
