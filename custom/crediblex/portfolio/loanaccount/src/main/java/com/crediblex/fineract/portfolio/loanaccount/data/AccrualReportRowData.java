/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.data;

import java.time.LocalDate;

/**
 * One month row of the loan Accrual Report. Amounts are formatted exactly as the frontend download writes them (two
 * decimals, JavaScript {@code toFixed(2)} rounding); a blank amount is the empty string.
 */
public record AccrualReportRowData(int index, LocalDate endOfMonth, String openingPrincipal, String closingPrincipal,
        String interestAccruedActual, String interestForecast) {
}
