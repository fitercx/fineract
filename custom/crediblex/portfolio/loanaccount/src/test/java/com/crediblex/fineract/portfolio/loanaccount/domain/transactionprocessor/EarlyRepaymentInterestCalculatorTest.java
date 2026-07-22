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

package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EarlyRepaymentInterestCalculatorTest {

    @Test
    void calculateProRatedInterest_reducesForDaysUsedInPeriod() {
        // Period 23-Jun -> 23-Jul = 30 days; pay on 21-Jul = 28 days used
        final BigDecimal full = new BigDecimal("270.280000");
        final BigDecimal prorated = EarlyRepaymentInterestCalculator.calculateProRatedInterest(full, LocalDate.of(2026, 6, 23),
                LocalDate.of(2026, 7, 23), LocalDate.of(2026, 7, 21));

        final BigDecimal expected = full.multiply(BigDecimal.valueOf(28)).divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP);
        assertEquals(0, expected.compareTo(prorated));
    }

    @Test
    void calculateProRatedInterest_onDueDateReturnsFull() {
        final BigDecimal full = new BigDecimal("270.28");
        final BigDecimal result = EarlyRepaymentInterestCalculator.calculateProRatedInterest(full, LocalDate.of(2026, 6, 23),
                LocalDate.of(2026, 7, 23), LocalDate.of(2026, 7, 23));
        assertEquals(0, full.compareTo(result));
    }

    @Test
    void calculateProRatedInterest_afterDueDateReturnsFull() {
        final BigDecimal full = new BigDecimal("270.28");
        final BigDecimal result = EarlyRepaymentInterestCalculator.calculateProRatedInterest(full, LocalDate.of(2026, 6, 23),
                LocalDate.of(2026, 7, 23), LocalDate.of(2026, 7, 25));
        assertEquals(0, full.compareTo(result));
    }

    @Test
    void calculateProRatedInterest_onPeriodStartReturnsZero() {
        final BigDecimal full = new BigDecimal("270.28");
        final BigDecimal result = EarlyRepaymentInterestCalculator.calculateProRatedInterest(full, LocalDate.of(2026, 6, 23),
                LocalDate.of(2026, 7, 23), LocalDate.of(2026, 6, 23));
        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void calculateProRatedInterest_withNullArgReturnsFullUnchanged() {
        final BigDecimal full = new BigDecimal("270.28");
        assertEquals(full, EarlyRepaymentInterestCalculator.calculateProRatedInterest(full, null, LocalDate.of(2026, 7, 23),
                LocalDate.of(2026, 7, 21)));
        assertNull(EarlyRepaymentInterestCalculator.calculateProRatedInterest(null, LocalDate.of(2026, 6, 23), LocalDate.of(2026, 7, 23),
                LocalDate.of(2026, 7, 21)));
    }
}
