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
 * Portfolio-level aggregates for the full CrediblEX overdue-loan population, returned by
 * {@code GET /loans/crediblex/overdue/summary}. The aggregates use the exact same overdue-loan definition and per-loan
 * field semantics as the list endpoint ({@code GET /loans/crediblex/overdue}); summary is always for the entire overdue
 * portfolio and is never affected by search or list filters.
 *
 * <p>
 * Identity that always holds: {@code totalOutstanding - totalOverdue - totalLpiOverdue = totalPrincipalOutstanding}.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredXOverdueLoansSummaryData {

    /** ISO currency code for display. V1 assumes a single-currency tenant (effectively AED). */
    private String currencyCode;
    /** Count of overdue loans in the full portfolio (same population as the list endpoint's totalFilteredRecords with no search). */
    private Long totalLoans;
    /** Sum of (principal + interest + LPI) outstanding across overdue installments of all qualifying loans. */
    private BigDecimal totalOutstanding;
    /** Sum of interest outstanding only (excludes principal and LPI). */
    private BigDecimal totalOverdue;
    /** Sum of LPI (penalty) outstanding. */
    private BigDecimal totalLpiOverdue;
    /** Sum of principal outstanding. */
    private BigDecimal totalPrincipalOutstanding;
}
