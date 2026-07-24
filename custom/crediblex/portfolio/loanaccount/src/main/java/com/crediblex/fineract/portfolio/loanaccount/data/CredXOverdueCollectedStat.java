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
 * Amount collected against overdue installments over one time window, with its component split and the number of
 * contributing transactions. {@code total = principal + interest + fees + lpi}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredXOverdueCollectedStat {

    /** principal + interest + fees + lpi collected. */
    private BigDecimal total;
    private BigDecimal principal;
    private BigDecimal interest;
    private BigDecimal fees;
    /** Late-payment interest (penalty) collected. */
    private BigDecimal lpi;
    /** Number of qualifying transactions in the window. */
    private Long count;

    /** A stat with every amount and the count set to zero. */
    public static CredXOverdueCollectedStat zero() {
        return CredXOverdueCollectedStat.builder().total(BigDecimal.ZERO).principal(BigDecimal.ZERO).interest(BigDecimal.ZERO)
                .fees(BigDecimal.ZERO).lpi(BigDecimal.ZERO).count(0L).build();
    }
}
