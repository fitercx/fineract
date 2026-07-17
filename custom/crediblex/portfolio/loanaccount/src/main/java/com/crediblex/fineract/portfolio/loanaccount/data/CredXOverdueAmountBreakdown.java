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
 * A monetary amount with its component split, used across the CrediblEX overdue endpoints for both whole-loan
 * "outstanding" and past-due "overdue" figures. The identity {@code total = principal + interest + fees + lpi} always
 * holds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredXOverdueAmountBreakdown {

    /** principal + interest + fees + lpi. */
    private BigDecimal total;
    private BigDecimal principal;
    private BigDecimal interest;
    private BigDecimal fees;
    /** Late-payment interest (penalty charges). */
    private BigDecimal lpi;

    /** A breakdown with every component set to zero (used for non-overdue loans). */
    public static CredXOverdueAmountBreakdown zero() {
        return CredXOverdueAmountBreakdown.builder().total(BigDecimal.ZERO).principal(BigDecimal.ZERO).interest(BigDecimal.ZERO)
                .fees(BigDecimal.ZERO).lpi(BigDecimal.ZERO).build();
    }

    /** Component-wise sum, returning a new breakdown (null operands treated as zero). */
    public CredXOverdueAmountBreakdown add(final CredXOverdueAmountBreakdown other) {
        if (other == null) {
            return this;
        }
        return CredXOverdueAmountBreakdown.builder().total(nz(total).add(nz(other.total))).principal(nz(principal).add(nz(other.principal)))
                .interest(nz(interest).add(nz(other.interest))).fees(nz(fees).add(nz(other.fees))).lpi(nz(lpi).add(nz(other.lpi))).build();
    }

    private static BigDecimal nz(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
