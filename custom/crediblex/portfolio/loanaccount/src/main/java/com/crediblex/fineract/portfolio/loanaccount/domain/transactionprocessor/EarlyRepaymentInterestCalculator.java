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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure day-count pro-ration formula for early-repayment interest reduction. Used by both
 * {@link EarlyRepaymentInterestHookImpl} (backend allocation) and the penalty/outstanding-amount calculators (UI
 * display), so both surfaces agree on the reduced interest amount.
 * <p>
 * Applies uniformly across all loan transaction-processing strategies and product types (including Factor Rate) without
 * any change to product configuration.
 */
public final class EarlyRepaymentInterestCalculator {

    private EarlyRepaymentInterestCalculator() {}

    /**
     * Pro-rates {@code fullInterest} for payment on {@code paymentDate} within [{@code fromDate}, {@code dueDate}).
     * Returns full interest when payment is on/after due date; zero when payment is on/before period start.
     */
    public static BigDecimal calculateProRatedInterest(final BigDecimal fullInterest, final LocalDate fromDate, final LocalDate dueDate,
            final LocalDate paymentDate) {
        if (fullInterest == null || fromDate == null || dueDate == null || paymentDate == null) {
            return fullInterest;
        }
        if (!paymentDate.isBefore(dueDate)) {
            return fullInterest;
        }
        final long totalDays = ChronoUnit.DAYS.between(fromDate, dueDate);
        if (totalDays <= 0) {
            return fullInterest;
        }
        final long actualDays = ChronoUnit.DAYS.between(fromDate, paymentDate);
        if (actualDays <= 0) {
            return BigDecimal.ZERO;
        }
        if (actualDays >= totalDays) {
            return fullInterest;
        }
        final int scale = Math.max(fullInterest.scale(), 6);
        return fullInterest.multiply(BigDecimal.valueOf(actualDays)).divide(BigDecimal.valueOf(totalDays), scale, RoundingMode.HALF_UP);
    }
}
