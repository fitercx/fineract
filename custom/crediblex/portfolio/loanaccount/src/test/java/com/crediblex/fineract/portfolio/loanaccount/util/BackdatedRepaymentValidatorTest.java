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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * LMS-133: how far back a transaction may be dated comes from the {@code backdated-transaction-max-days} global
 * configuration - a day count when the config is enabled, or the loan's first instalment period start when it is
 * disabled. Asserted through the package-private overload, so neither the configuration nor the business date has to be
 * mutated per test.
 */
class BackdatedRepaymentValidatorTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 30);
    private static final int DEFAULT_DAYS = BackdateWindowSettings.DEFAULT_MAX_BACKDATE_DAYS;
    private static final LocalDate DEFAULT_FLOOR = BUSINESS_DATE.minusDays(DEFAULT_DAYS);

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    private Loan loan(final LocalDate disbursementDate, final LocalDate firstPeriodStartDate) {
        final Loan loan = mock(Loan.class);
        when(loan.getId()).thenReturn(4711L);
        when(loan.getDisbursementDate()).thenReturn(disbursementDate);
        if (firstPeriodStartDate == null) {
            when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of());
        } else {
            final LoanRepaymentScheduleInstallment firstInstallment = mock(LoanRepaymentScheduleInstallment.class);
            when(firstInstallment.getFromDate()).thenReturn(firstPeriodStartDate);
            when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(firstInstallment));
        }
        return loan;
    }

    @Test
    void dayLimitStopsAtTheConfiguredNumberOfDaysForAnOldLoan() {
        final Loan oldLoan = loan(BUSINESS_DATE.minusMonths(8), BUSINESS_DATE.minusMonths(8));

        assertThat(BackdatedRepaymentValidator.computeEarliestAllowedTransactionDate(oldLoan, DEFAULT_DAYS)).isEqualTo(DEFAULT_FLOOR);
        assertThat(BackdatedRepaymentValidator.computeEarliestAllowedTransactionDate(oldLoan, 90)).isEqualTo(BUSINESS_DATE.minusDays(90));
    }

    @Test
    void dayLimitStopsAtDisbursementForARecentlyDisbursedLoan() {
        final LocalDate disbursedTenDaysAgo = BUSINESS_DATE.minusDays(10);
        final Loan recentLoan = loan(disbursedTenDaysAgo, disbursedTenDaysAgo);

        assertThat(BackdatedRepaymentValidator.computeEarliestAllowedTransactionDate(recentLoan, DEFAULT_DAYS))
                .isEqualTo(disbursedTenDaysAgo);
    }

    @Test
    void noDayLimitReachesBackToTheFirstInstalmentPeriodStart() {
        final LocalDate loanStart = BUSINESS_DATE.minusMonths(8);
        final Loan oldLoan = loan(loanStart, loanStart);

        assertThat(BackdatedRepaymentValidator.computeEarliestAllowedTransactionDate(oldLoan, null)).isEqualTo(loanStart);
    }

    @Test
    void noDayLimitFallsBackToDisbursementDateWhenScheduleIsNotGenerated() {
        final LocalDate disbursedFourMonthsAgo = BUSINESS_DATE.minusMonths(4);
        final Loan loanWithoutSchedule = loan(disbursedFourMonthsAgo, null);

        assertThat(BackdatedRepaymentValidator.computeEarliestAllowedTransactionDate(loanWithoutSchedule, null))
                .isEqualTo(disbursedFourMonthsAgo);
    }

    @Test
    void noDayLimitNeverReachesBeforeDisbursement() {
        final LocalDate disbursementDate = BUSINESS_DATE.minusMonths(3);
        final Loan loanWithEarlierPeriodStart = loan(disbursementDate, disbursementDate.minusDays(15));

        assertThat(BackdatedRepaymentValidator.computeEarliestAllowedTransactionDate(loanWithEarlierPeriodStart, null))
                .isEqualTo(disbursementDate);
    }

    @Test
    void noDayLimitFallsBackToTheDefaultWindowWhenLoanStartIsUnknown() {
        final Loan undisbursedLoan = loan(null, null);

        assertThat(BackdatedRepaymentValidator.computeEarliestAllowedTransactionDate(undisbursedLoan, null)).isEqualTo(DEFAULT_FLOOR);
    }

    @Test
    void unconfiguredContextKeepsTheDefaultWindow() {
        final LocalDate loanStart = BUSINESS_DATE.minusMonths(8);
        final Loan oldLoan = loan(loanStart, loanStart);

        // No Spring context in a plain unit test - the static accessor must degrade to the seeded 30-day policy.
        assertThat(BackdateWindowSettings.maxBackdateDays()).isEqualTo(DEFAULT_DAYS);
        assertThat(BackdatedRepaymentValidator.computeEarliestAllowedTransactionDate(oldLoan)).isEqualTo(DEFAULT_FLOOR);
    }

    @Test
    void rejectsATransactionDatedBeforeTheWindowAndQuotesTheConfiguredDayCount() {
        final LocalDate loanStart = BUSINESS_DATE.minusMonths(8);
        final Loan oldLoan = loan(loanStart, loanStart);

        assertThatThrownBy(() -> BackdatedRepaymentValidator.validateWithinBackdateLimit(oldLoan, DEFAULT_FLOOR.minusDays(1), "repayment"))
                .isInstanceOf(GeneralPlatformDomainRuleException.class).hasMessageContaining("too far in the past")
                .hasMessageContaining("up to " + DEFAULT_DAYS + " days before today");
    }

    @Test
    void allowsATransactionOnTheEarliestAllowedDate() {
        final LocalDate loanStart = BUSINESS_DATE.minusMonths(8);
        final Loan oldLoan = loan(loanStart, loanStart);

        assertThatCode(() -> BackdatedRepaymentValidator.validateWithinBackdateLimit(oldLoan, DEFAULT_FLOOR, "repayment"))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsValidationForANullTransactionDate() {
        final Loan oldLoan = loan(BUSINESS_DATE.minusMonths(8), BUSINESS_DATE.minusMonths(8));

        assertThatCode(() -> BackdatedRepaymentValidator.validateWithinBackdateLimit(oldLoan, null, "foreclosure"))
                .doesNotThrowAnyException();
    }
}
