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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server-computed "overdue amounts collected" summary returned by {@code GET /loans/crediblex/overdue/collected/summary}.
 * By default it covers the entire portfolio; when {@code clientId} and/or {@code loanId} are supplied it is scoped to
 * that client/loan. Windows are computed from the tenant business date (inclusive).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredXOverdueCollectedSummaryData {

    /** ISO currency code for display. V1 assumes a single-currency tenant (effectively AED). */
    private String currencyCode;
    /** Echo of the clientId filter applied (null = whole portfolio). */
    private Long clientId;
    /** Echo of the loanId filter applied (null = not scoped to a loan). */
    private Long loanId;
    /** Tenant business date the windows were computed against (yyyy-MM-dd). */
    private String businessDate;
    /** Inclusive lower bound of the last-7-days window (yyyy-MM-dd). */
    private String last7DaysFrom;
    /** Inclusive lower bound of the last-30-days window (yyyy-MM-dd). */
    private String last30DaysFrom;
    /** Collected amounts for all-time / last 7 days / last 30 days. */
    private CredXOverdueCollectedWindows collected;
}
