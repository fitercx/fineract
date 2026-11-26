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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportRowData;
import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportCalculator.Input;
import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportCalculator.Period;
import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportCalculator.Totals;
import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportCalculator.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.fineract.portfolio.loanaccount.data.LoanStatusEnumData;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mirrors fineract-frontend/src/app/loans/accrual-report.util.spec.ts scenario for scenario, with the same expected
 * values, so the S3 export and the Finance download stay identical.
 */
class AccrualReportCalculatorTest {

    // Disbursed 22 Jun 2026. Installment 1 covers 22 Jun - 22 Jul (30 days, 30.00 interest = 1.00/day),
    // installment 2 covers 22 Jul - 22 Aug (31 days, 31.00 interest = 1.00/day). Lifetime interest 61.00.
    private static final List<Period> PERIODS = List.of(new Period(null, null, LocalDate.of(2026, 6, 22), null, bd(1000), null, bd(1000)),
            new Period(1, LocalDate.of(2026, 6, 22), LocalDate.of(2026, 7, 22), bd(30), null, bd(500), bd(500)),
            new Period(2, LocalDate.of(2026, 7, 22), LocalDate.of(2026, 8, 22), bd(31), null, bd(500), bd(0)));

    private static BigDecimal bd(final double value) {
        return BigDecimal.valueOf(value);
    }

    private static Transaction accrual(final LocalDate date, final double interest) {
        return new Transaction(date, false, "loanTransactionType.accrual", true, bd(interest));
    }

    private static List<Transaction> dailyAccruals(final LocalDate from, final LocalDate to, final double amount) {
        final List<Transaction> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            result.add(accrual(day, amount));
        }
        return result;
    }

    private static List<AccrualReportRowData> build(final List<Transaction> transactions, final LocalDate businessDate) {
        return build(transactions, businessDate, true, PERIODS);
    }

    private static List<AccrualReportRowData> build(final List<Transaction> transactions, final LocalDate businessDate,
            final boolean forecastEnabled, final List<Period> periods) {
        return AccrualReportCalculator.computeMonthlyRows(
                new Input(periods, transactions, LocalDate.of(2026, 6, 22), LocalDate.of(2026, 8, 22), businessDate, forecastEnabled));
    }

    private static List<String> column(final List<AccrualReportRowData> rows, final Function<AccrualReportRowData, String> getter) {
        return rows.stream().map(getter).toList();
    }

    private static List<String> actual(final List<AccrualReportRowData> rows) {
        return column(rows, AccrualReportRowData::interestAccruedActual);
    }

    private static List<String> forecast(final List<AccrualReportRowData> rows) {
        return column(rows, AccrualReportRowData::interestForecast);
    }

    private static double sum(final List<String> values) {
        return values.stream().filter(v -> !v.isEmpty()).mapToDouble(Double::parseDouble).sum();
    }

    @Nested
    class InterestAccruedActual {

        @Test
        void sumsPostedDailyAccrualInterestPerValueDateMonth() {
            final List<AccrualReportRowData> rows = build(
                    List.of(accrual(LocalDate.of(2026, 6, 23), 0.5), accrual(LocalDate.of(2026, 6, 30), 0.5),
                            accrual(LocalDate.of(2026, 7, 1), 0.7), accrual(LocalDate.of(2026, 7, 31), 0.3)),
                    LocalDate.of(2026, 8, 5));

            assertThat(actual(rows)).containsExactly("1.00", "1.00", "0.00");
            assertThat(rows.get(0)).isEqualTo(new AccrualReportRowData(1, LocalDate.of(2026, 6, 30), "1000.00", "1000.00", "1.00", ""));
            assertThat(rows.get(1).closingPrincipal()).isEqualTo("500.00");
            assertThat(rows.get(2).endOfMonth()).isEqualTo(LocalDate.of(2026, 8, 22));
            assertThat(rows.get(2).closingPrincipal()).isEqualTo("0.00");
        }

        @Test
        void excludesReversedAccrualsNetsAccrualAdjustmentsAndIgnoresNonAccrualTransactions() {
            final List<AccrualReportRowData> rows = build(
                    List.of(accrual(LocalDate.of(2026, 7, 10), 2),
                            new Transaction(LocalDate.of(2026, 7, 11), true, "loanTransactionType.accrual", true, bd(5)),
                            new Transaction(LocalDate.of(2026, 7, 12), false, "loanTransactionType.accrualAdjustment", false, bd(0.5)),
                            new Transaction(LocalDate.of(2026, 7, 22), false, "loanTransactionType.repayment", false, bd(20))),
                    LocalDate.of(2026, 8, 5));

            assertThat(rows.get(1).interestAccruedActual()).isEqualTo("1.50");
        }

        @Test
        void leavesMonthsStartingAfterTheBusinessDateBlank() {
            final List<AccrualReportRowData> rows = build(List.of(accrual(LocalDate.of(2026, 6, 25), 1)), LocalDate.of(2026, 7, 15));

            assertThat(actual(rows)).containsExactly("1.00", "0.00", "");
        }

        @Test
        void extendsPastMaturityWhenAccrualsWerePostedAfterTheMaturityMonth() {
            final List<AccrualReportRowData> rows = build(List.of(accrual(LocalDate.of(2026, 9, 3), 0.4)), LocalDate.of(2026, 9, 10), false,
                    PERIODS);

            assertThat(rows).hasSize(4);
            assertThat(rows.get(3).endOfMonth()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(rows.get(3).interestAccruedActual()).isEqualTo("0.40");
        }
    }

    @Nested
    class InterestForecast {

        @Test
        void spreadsEachInstallmentEvenlyOverItsDaysAndSplitsPeriodsThatSpanTwoMonths() {
            final List<AccrualReportRowData> rows = build(List.of(), LocalDate.of(2026, 6, 22));

            assertThat(forecast(rows)).containsExactly("8.00", "31.00", "22.00");
            assertThat(sum(forecast(rows))).isCloseTo(61, within(0.005));
        }

        @Test
        void fillsBothActualAndForecastForTheCurrentMonthAndForecastsUnpostedLagDays() {
            final List<AccrualReportRowData> rows = build(dailyAccruals(LocalDate.of(2026, 6, 23), LocalDate.of(2026, 7, 5), 1),
                    LocalDate.of(2026, 7, 10));

            assertThat(actual(rows)).containsExactly("8.00", "5.00", "");
            assertThat(forecast(rows)).containsExactly("", "26.00", "22.00");
            assertThat(sum(actual(rows)) + sum(forecast(rows))).isCloseTo(61, within(0.005));
        }

        @Test
        void reconcilesAPartiallyAccruedInstallmentSoActualPlusForecastEqualsTheScheduleInterest() {
            final List<AccrualReportRowData> rows = build(List.of(accrual(LocalDate.of(2026, 6, 30), 10)), LocalDate.of(2026, 6, 30));

            assertThat(forecast(rows)).containsExactly("", "29.00", "22.00");
            assertThat(sum(actual(rows)) + sum(forecast(rows))).isCloseTo(61, within(0.005));
        }

        @Test
        void usesInterestDueSoWaivedInterestIsNotForecast() {
            final List<Period> waivedPeriods = PERIODS.stream()
                    .map(p -> Integer.valueOf(1).equals(p.period())
                            ? new Period(1, p.fromDate(), p.dueDate(), bd(20), null, p.principalDue(), p.principalLoanBalanceOutstanding())
                            : p)
                    .toList();
            final List<AccrualReportRowData> rows = build(List.of(), LocalDate.of(2026, 6, 22), true, waivedPeriods);

            assertThat(sum(forecast(rows))).isCloseTo(51, within(0.005));
        }

        @Test
        void doesNotForecastForLoansWithForecastingDisabled() {
            final List<AccrualReportRowData> rows = build(List.of(), LocalDate.of(2026, 6, 22), false, PERIODS);

            assertThat(forecast(rows)).containsExactly("", "", "");
        }

        @Test
        void doesNotForecastPastMaturityOnceTheScheduleIsFullyAccrued() {
            final List<AccrualReportRowData> rows = build(dailyAccruals(LocalDate.of(2026, 6, 23), LocalDate.of(2026, 8, 22), 1),
                    LocalDate.of(2026, 9, 10));

            assertThat(rows).hasSize(3);
            assertThat(forecast(rows)).containsExactly("", "", "");
            assertThat(sum(actual(rows))).isCloseTo(61, within(0.005));
        }

        @Test
        void forecastsTheRemainderOfAnElapsedUnderAccruedPeriodInTheBusinessDateMonth() {
            final List<AccrualReportRowData> rows = build(List.of(accrual(LocalDate.of(2026, 8, 22), 40)), LocalDate.of(2026, 9, 10));

            assertThat(rows).hasSize(4);
            assertThat(rows.get(3).endOfMonth()).isEqualTo(LocalDate.of(2026, 9, 30));
            // Installment 1 (30.00) has nothing posted inside its period and no days left after the last accrual.
            assertThat(forecast(rows)).containsExactly("", "", "", "30.00");
        }
    }

    @Nested
    class ColumnTotals {

        @Test
        void addsColumnTotalsAndTheLifetimeTotal() {
            final List<AccrualReportRowData> rows = build(dailyAccruals(LocalDate.of(2026, 6, 23), LocalDate.of(2026, 7, 5), 1),
                    LocalDate.of(2026, 7, 10));

            assertThat(AccrualReportCalculator.computeTotals(rows, true)).isEqualTo(new Totals("13.00", "48.00", "61.00"));
        }

        @Test
        void omitsTheForecastTotalWhenForecastingIsDisabled() {
            final List<AccrualReportRowData> rows = build(List.of(accrual(LocalDate.of(2026, 6, 25), 1)), LocalDate.of(2026, 7, 10), false,
                    PERIODS);

            assertThat(AccrualReportCalculator.computeTotals(rows, false)).isEqualTo(new Totals("1.00", "", "1.00"));
        }
    }

    @Test
    void forecastIsEnabledForActiveLoansAndDisabledForClosedOverpaidWrittenOffAndChargedOffLoans() {
        assertThat(AccrualReportCalculator.isForecastEnabled(status(300), false)).isTrue();
        assertThat(AccrualReportCalculator.isForecastEnabled(status(600), false)).isFalse();
        assertThat(AccrualReportCalculator.isForecastEnabled(status(700), false)).isFalse();
        assertThat(AccrualReportCalculator.isForecastEnabled(status(601), false)).isFalse();
        assertThat(AccrualReportCalculator.isForecastEnabled(status(300), true)).isFalse();
    }

    @Test
    void formatsAmountsLikeJavaScriptToFixed() {
        assertThat(AccrualReportCalculator.toFixed2(1.005)).isEqualTo("1.00");
        assertThat(AccrualReportCalculator.toFixed2(2.675)).isEqualTo("2.67");
        assertThat(AccrualReportCalculator.toFixed2(0.125)).isEqualTo("0.13");
        assertThat(AccrualReportCalculator.toFixed2(71000)).isEqualTo("71000.00");
        assertThat(AccrualReportCalculator.toFixed2(-1.5)).isEqualTo("-1.50");
        assertThat(AccrualReportCalculator.toFixed2(-0.001)).isEqualTo("-0.00");
        assertThat(AccrualReportCalculator.toFixed2(0.1 + 0.2)).isEqualTo("0.30");
    }

    private static LoanStatusEnumData status(final long id) {
        return new LoanStatusEnumData(id, "loanStatusType." + id, String.valueOf(id));
    }
}
