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
package com.crediblex.fineract.portfolio.loanaccount.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.Getter;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsData;
import org.apache.fineract.portfolio.loanproduct.calc.data.ProgressiveLoanInterestScheduleModel;
import org.apache.fineract.portfolio.loanproduct.calc.data.RepaymentPeriod;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductMinimumRepaymentScheduleRelatedDetail;

/**
 * Simple interest schedule model specifically for Receivable Line of Credit loans. This model provides basic
 * functionality without relying heavily on progressive loan logic.
 */
@Getter
public class ReceivableLineOfCreditLoanInterestScheduleModel extends ProgressiveLoanInterestScheduleModel {

    private final LocalDate startDate;
    private final LocalDate maturityDate;
    private final Money principalAmount;
    private final BigDecimal annualInterestRate;
    private final MathContext mathContext;

    // Track interest rate changes and pauses
    private final Map<LocalDate, BigDecimal> interestRateChanges = new HashMap<>();
    private final Map<LocalDate, LocalDate> interestPauses = new HashMap<>();

    public ReceivableLineOfCreditLoanInterestScheduleModel(final List<RepaymentPeriod> repaymentPeriods,
            final LoanProductMinimumRepaymentScheduleRelatedDetail loanProductRelatedDetail,
            final List<LoanTermVariationsData> loanTermVariations, final Integer installmentAmountInMultiplesOf, final MathContext mc,
            final LocalDate startDate, LocalDate maturityDate, final Money principalAmount, final BigDecimal annualInterestRate) {
        super(repaymentPeriods, loanProductRelatedDetail, loanTermVariations, installmentAmountInMultiplesOf, mc);
        this.mathContext = mc;
        this.startDate = startDate;
        this.maturityDate = maturityDate;
        this.principalAmount = principalAmount;
        this.annualInterestRate = annualInterestRate;

    }

    /**
     * Apply interest rate change on a specific date
     */
    public void changeInterestRate(LocalDate effectiveDate, BigDecimal newRate) {
        interestRateChanges.put(effectiveDate, newRate);
    }

    /**
     * Apply interest pause from start date to end date
     */
    public void applyInterestPause(LocalDate pauseStartDate, LocalDate pauseEndDate) {
        interestPauses.put(pauseStartDate, pauseEndDate);
    }

    /**
     * Get the effective interest rate considering all changes and pauses
     */
    private BigDecimal getEffectiveInterestRate() {
        BigDecimal currentRate = annualInterestRate;

        // Apply rate changes in chronological order
        List<LocalDate> sortedChangeDates = new ArrayList<>(interestRateChanges.keySet());
        Collections.sort(sortedChangeDates);

        for (LocalDate changeDate : sortedChangeDates) {
            if (!changeDate.isAfter(maturityDate) && !changeDate.isBefore(startDate)) {
                currentRate = interestRateChanges.get(changeDate);
            }
        }

        // Check if there are any interest pauses that affect the calculation
        // For simplicity, if there's a pause that covers the entire period, return zero
        for (Map.Entry<LocalDate, LocalDate> pause : interestPauses.entrySet()) {
            LocalDate pauseStart = pause.getKey();
            LocalDate pauseEnd = pause.getValue();

            if (!pauseStart.isAfter(startDate) && !pauseEnd.isBefore(maturityDate)) {
                // Pause covers the entire period
                return BigDecimal.ZERO;
            }
        }

        return currentRate;
    }

    /**
     * Calculate interest for a specific period with rate changes and pauses
     */
    public Money calculateInterestForPeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (principalAmount == null || annualInterestRate == null) {
            return Money.zero(principalAmount.getCurrency(), mathContext);
        }

        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal currentRate = annualInterestRate;

        // Get all relevant dates (rate changes and pauses) within the period
        TreeSet<LocalDate> relevantDates = new TreeSet<>();
        relevantDates.add(periodStart);
        relevantDates.add(periodEnd);

        interestRateChanges.keySet().stream().filter(date -> !date.isBefore(periodStart) && date.isBefore(periodEnd))
                .forEach(relevantDates::add);

        interestPauses.keySet().stream().filter(date -> !date.isBefore(periodStart) && date.isBefore(periodEnd))
                .forEach(relevantDates::add);

        interestPauses.values().stream().filter(date -> !date.isBefore(periodStart) && date.isBefore(periodEnd))
                .forEach(relevantDates::add);

        // Calculate interest for each sub-period
        LocalDate previousDate = periodStart;

        for (LocalDate date : relevantDates) {

            if (date.equals(periodStart)) {
                continue;
            }

            // Create final variables for lambda
            final LocalDate subPeriodStart = previousDate;
            final LocalDate subPeriodEnd = date;

            // Check if this sub-period is during an interest pause
            boolean isPaused = interestPauses.entrySet().stream()
                    .anyMatch(pause -> !subPeriodStart.isBefore(pause.getKey()) && !subPeriodEnd.isAfter(pause.getValue()));

            if (!isPaused) {
                long daysInSubPeriod = ChronoUnit.DAYS.between(previousDate, date);
                BigDecimal timeInYears = BigDecimal.valueOf(daysInSubPeriod).divide(BigDecimal.valueOf(360), mathContext);

                BigDecimal subPeriodInterest = principalAmount.getAmount()
                        .multiply(currentRate.divide(BigDecimal.valueOf(100), mathContext)).multiply(timeInYears);

                totalInterest = totalInterest.add(subPeriodInterest);
            }

            // Update current rate if there's a rate change at this date
            if (interestRateChanges.containsKey(date)) {
                currentRate = interestRateChanges.get(date);
            }

            previousDate = date;
        }

        return Money.of(principalAmount.getCurrency(), totalInterest, mathContext);
    }

}
