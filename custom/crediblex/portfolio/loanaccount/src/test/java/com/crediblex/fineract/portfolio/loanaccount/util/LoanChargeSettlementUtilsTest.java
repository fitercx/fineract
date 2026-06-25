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
package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Set;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.junit.jupiter.api.Test;

class LoanChargeSettlementUtilsTest {

    @Test
    void hasNoPayableChargesRemainingWhenWaivedZeroTaxChargeHasStaleTaxWaivedFlag() {
        final Loan loan = mock(Loan.class);
        final LoanCharge charge = mock(LoanCharge.class);

        when(loan.getCharges()).thenReturn(Set.of(charge));
        when(charge.isActive()).thenReturn(true);
        when(charge.amount()).thenReturn(new BigDecimal("1949.100000"));
        when(charge.isPaid()).thenReturn(false);
        when(charge.isWaived()).thenReturn(false);
        when(charge.amountOutstanding()).thenReturn(BigDecimal.ZERO);
        when(charge.getTaxAmountOutstanding()).thenReturn(BigDecimal.ZERO);

        assertThat(LoanChargeSettlementUtils.hasNoPayableChargesRemaining(loan)).isTrue();
    }

    @Test
    void hasPayableChargesRemainingWhenAnyActiveChargeHasOutstandingBalance() {
        final Loan loan = mock(Loan.class);
        final LoanCharge charge = mock(LoanCharge.class);

        when(loan.getCharges()).thenReturn(Set.of(charge));
        when(charge.isActive()).thenReturn(true);
        when(charge.amount()).thenReturn(new BigDecimal("100.00"));
        when(charge.isPaid()).thenReturn(false);
        when(charge.isWaived()).thenReturn(false);
        when(charge.amountOutstanding()).thenReturn(new BigDecimal("10.00"));
        when(charge.getTaxAmountOutstanding()).thenReturn(BigDecimal.ZERO);

        assertThat(LoanChargeSettlementUtils.hasNoPayableChargesRemaining(loan)).isFalse();
    }
}
