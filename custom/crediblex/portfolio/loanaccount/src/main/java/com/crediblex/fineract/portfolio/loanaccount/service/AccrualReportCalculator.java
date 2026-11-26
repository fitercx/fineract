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
package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportRowData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.fineract.portfolio.loanaccount.data.LoanStatusEnumData;

/**
 * Month-by-month loan Accrual Report computation. This is a line-for-line port of the frontend download
 * ({@code fineract-frontend/src/app/loans/accrual-report.util.ts}, LMS-182 and LMS-185) and must stay in step with it:
 * the S3 export and the Finance download have to produce identical figures. Arithmetic is done in {@code double}, in
 * the same order as the frontend, and amounts are formatted with JavaScript {@code toFixed(2)} semantics.
 * <p>
 * "Interest Accrued (Actual)" is the interest portion of the loan's non-reversed Accrual transactions, net of Accrual
 * Adjustments, bucketed by value-date month; months starting after the business date are blank.
 * <p>
 * "Interest Forecast" is the scheduled interest still to accrue, mirroring Fineract's daily accrual
 * (AbstractCumulativeLoanScheduleGenerator.getPeriodInterestTillDate): per installment, interestDue minus the accruals
 * already posted inside (fromDate, dueDate], spread evenly over the period's days after the latest posted accrual. A
 * fully elapsed but under-accrued period is forecast in the business-date month.
 */
public final class AccrualReportCalculator {

    public static final String FORECAST_NOTE = "Interest Forecast assumes the current repayment schedule (no prepayments, reschedules or rate changes) and excludes late payment interest.";

    private static final String ACCRUAL_CODE = "loanTransactionType.accrual";
    private static final String ACCRUAL_ADJUSTMENT_CODE = "loanTransactionType.accrualAdjustment";
    private static final double MIN_DISPLAY_AMOUNT = 0.005;

    private AccrualReportCalculator() {}

    /** A repayment schedule period, as returned by GET /loans/{id}?associations=repaymentSchedule. */
    public record Period(Integer period, LocalDate fromDate, LocalDate dueDate, BigDecimal interestDue, BigDecimal principalDisbursed,
            BigDecimal principalDue, BigDecimal principalLoanBalanceOutstanding) {
    }

    /** A loan transaction, as returned by GET /loans/{id}?associations=transactions. */
    public record Transaction(LocalDate date, boolean reversed, String typeCode, boolean typeAccrual, BigDecimal interestPortion) {
    }

    public record Input(List<Period> periods, List<Transaction> transactions, LocalDate startDate, LocalDate maturityDate,
            LocalDate businessDate, boolean forecastEnabled) {
    }

    /** Column totals under the month rows; the Forecast total is the empty string when forecasting is disabled. */
    public record Totals(String interestAccruedActual, String interestForecast, String lifetimeInterest) {
    }

    private record PostedAccrual(LocalDate date, double interest) {
    }

    /** No forecast for loans that are closed, overpaid, written off or charged off. */
    public static boolean isForecastEnabled(final LoanStatusEnumData status, final Boolean chargedOff) {
        if (Boolean.TRUE.equals(chargedOff)) {
            return false;
        }
        return status == null || !(status.isClosed() || status.isClosedObligationsMet() || status.isClosedWrittenOff()
                || status.isClosedRescheduled() || status.isOverpaid());
    }

    public static List<AccrualReportRowData> computeMonthlyRows(final Input input) {
        final List<Period> periods = input.periods();
        final List<AccrualReportRowData> rows = new ArrayList<>();
        if (periods == null || periods.isEmpty()) {
            return rows;
        }
        final LocalDate startDate = input.startDate();
        final LocalDate maturityDate = input.maturityDate();
        final LocalDate businessDate = input.businessDate();

        final List<PostedAccrual> postedAccruals = collectPostedAccruals(input.transactions());
        final Map<YearMonth, Double> postedAccrualByMonth = new HashMap<>();
        LocalDate lastAccrualDate = null;
        for (PostedAccrual entry : postedAccruals) {
            addToMonth(postedAccrualByMonth, entry.date(), entry.interest());
            if (lastAccrualDate == null || entry.date().isAfter(lastAccrualDate)) {
                lastAccrualDate = entry.date();
            }
        }

        final Map<YearMonth, Double> forecastByMonth = input.forecastEnabled()
                ? computeForecastByMonth(periods, postedAccruals, lastAccrualDate, businessDate)
                : Map.of();

        LocalDate lastReportDate = lastAccrualDate != null && lastAccrualDate.isAfter(maturityDate) ? lastAccrualDate : maturityDate;
        if (forecastByMonth.containsKey(YearMonth.from(businessDate)) && businessDate.isAfter(lastReportDate)) {
            lastReportDate = businessDate;
        }

        final Period firstDisbursementPeriod = periods.stream().filter(p -> toNumber(p.principalDisbursed()) > 0).findFirst().orElse(null);
        final double initialPrincipal = firstDisbursementPeriod != null ? toNumber(firstDisbursementPeriod.principalDisbursed())
                : toNumber(periods.get(0).principalLoanBalanceOutstanding()) + toNumber(periods.get(0).principalDue());
        double previousPrincipalBalance = initialPrincipal;

        final YearMonth startMonth = YearMonth.from(startDate);
        final YearMonth maturityMonth = YearMonth.from(maturityDate);
        final YearMonth finalMonth = YearMonth.from(lastReportDate);
        int index = 1;
        for (YearMonth currentMonth = startMonth; !currentMonth.isAfter(finalMonth); currentMonth = currentMonth.plusMonths(1)) {
            final boolean isFirstMonth = currentMonth.equals(startMonth);
            final boolean isMaturityMonth = currentMonth.equals(maturityMonth);
            final LocalDate monthEndDate = isMaturityMonth ? maturityDate : currentMonth.atEndOfMonth();
            final LocalDate monthStartDate = isFirstMonth ? startDate : currentMonth.atDay(1);

            // Opening principal (balance entering the month, before this month's payment).
            double openingPrincipal = previousPrincipalBalance;
            final Period lastBefore = lastPeriodMatching(periods, dueDate -> dueDate.isBefore(monthStartDate));
            if (lastBefore != null) {
                openingPrincipal = isNonZero(lastBefore.principalDisbursed()) ? toNumber(lastBefore.principalDisbursed())
                        : toNumber(lastBefore.principalLoanBalanceOutstanding());
            } else if (isFirstMonth) {
                openingPrincipal = initialPrincipal;
            }

            // Closing principal (balance after this month's payment).
            double closingPrincipal = openingPrincipal;
            final Period lastDueThisMonth = lastPeriodMatching(periods,
                    dueDate -> !dueDate.isBefore(monthStartDate) && !dueDate.isAfter(monthEndDate));
            if (lastDueThisMonth != null) {
                closingPrincipal = toNumber(lastDueThisMonth.principalLoanBalanceOutstanding());
            }
            if (isMaturityMonth) {
                closingPrincipal = 0;
            }

            final String interestAccrued = monthStartDate.isAfter(businessDate) ? ""
                    : toFixed2(postedAccrualByMonth.getOrDefault(currentMonth, 0.0));
            final double forecast = forecastByMonth.getOrDefault(currentMonth, 0.0);
            final String interestForecast = Math.abs(forecast) >= MIN_DISPLAY_AMOUNT ? toFixed2(forecast) : "";

            rows.add(new AccrualReportRowData(index, monthEndDate, toFixed2(openingPrincipal), toFixed2(closingPrincipal), interestAccrued,
                    interestForecast));
            previousPrincipalBalance = closingPrincipal;
            index++;
        }
        return rows;
    }

    /** Totals of the formatted month cells, as the frontend's appendAccrualTotals sums them. */
    public static Totals computeTotals(final List<AccrualReportRowData> rows, final boolean forecastEnabled) {
        double actualTotal = 0;
        double forecastTotal = 0;
        for (AccrualReportRowData row : rows) {
            actualTotal += parseAmount(row.interestAccruedActual());
        }
        for (AccrualReportRowData row : rows) {
            forecastTotal += parseAmount(row.interestForecast());
        }
        return new Totals(toFixed2(actualTotal), forecastEnabled ? toFixed2(forecastTotal) : "", toFixed2(actualTotal + forecastTotal));
    }

    /** JavaScript {@code Number.prototype.toFixed(2)}: rounds the exact binary value, ties away from zero. */
    static String toFixed2(final double value) {
        final String magnitude = new BigDecimal(Math.abs(value)).setScale(2, RoundingMode.HALF_UP).toPlainString();
        return value < 0 ? "-" + magnitude : magnitude;
    }

    private static List<PostedAccrual> collectPostedAccruals(final List<Transaction> transactions) {
        final List<PostedAccrual> entries = new ArrayList<>();
        if (transactions == null) {
            return entries;
        }
        for (Transaction transaction : transactions) {
            if (transaction.reversed() || transaction.date() == null) {
                continue;
            }
            final String code = transaction.typeCode();
            final boolean isAccrual = ACCRUAL_CODE.equals(code) || (code == null && transaction.typeAccrual());
            final boolean isAccrualAdjustment = ACCRUAL_ADJUSTMENT_CODE.equals(code);
            if (!isAccrual && !isAccrualAdjustment) {
                continue;
            }
            final double interest = toNumber(transaction.interestPortion());
            if (interest == 0) {
                continue;
            }
            entries.add(new PostedAccrual(transaction.date(), isAccrualAdjustment ? -interest : interest));
        }
        return entries;
    }

    private static Map<YearMonth, Double> computeForecastByMonth(final List<Period> periods, final List<PostedAccrual> postedAccruals,
            final LocalDate lastAccrualDate, final LocalDate businessDate) {
        final Map<YearMonth, Double> byMonth = new HashMap<>();
        for (Period period : periods) {
            final double interest = toNumber(period.interestDue());
            if (period.period() == null || period.period() == 0 || period.fromDate() == null || period.dueDate() == null
                    || !(interest > 0)) {
                continue;
            }
            final LocalDate fromDate = period.fromDate();
            final LocalDate dueDate = period.dueDate();
            if (!dueDate.isAfter(fromDate)) {
                continue;
            }

            double postedInPeriod = 0;
            for (PostedAccrual entry : postedAccruals) {
                if (entry.date().isAfter(fromDate) && !entry.date().isAfter(dueDate)) {
                    postedInPeriod += entry.interest();
                }
            }
            final double remaining = interest - postedInPeriod;
            if (remaining <= 0) {
                continue;
            }

            final LocalDate accrueFrom = lastAccrualDate != null && lastAccrualDate.isAfter(fromDate) ? lastAccrualDate : fromDate;
            final long remainingDays = ChronoUnit.DAYS.between(accrueFrom, dueDate);
            if (remainingDays <= 0) {
                addToMonth(byMonth, businessDate, remaining);
                continue;
            }

            final double perDay = remaining / remainingDays;
            for (long day = 1; day <= remainingDays; day++) {
                addToMonth(byMonth, accrueFrom.plusDays(day), perDay);
            }
        }
        return byMonth;
    }

    private static Period lastPeriodMatching(final List<Period> periods, final Predicate<LocalDate> dueDateMatches) {
        Period match = null;
        for (Period period : periods) {
            if (period.dueDate() != null && dueDateMatches.test(period.dueDate())) {
                match = period;
            }
        }
        return match;
    }

    private static void addToMonth(final Map<YearMonth, Double> byMonth, final LocalDate date, final double amount) {
        byMonth.merge(YearMonth.from(date), amount, Double::sum);
    }

    private static double toNumber(final BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private static boolean isNonZero(final BigDecimal value) {
        return value != null && value.signum() != 0;
    }

    private static double parseAmount(final String value) {
        return value == null || value.isEmpty() ? 0 : Double.parseDouble(value);
    }
}
