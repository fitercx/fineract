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

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * LMS-133: mapping of the {@code backdated-transaction-max-days} global configuration row onto the effective backdate
 * window. A missing row or a nonsensical value must never silently remove the guard.
 */
class BackdateWindowSettingsTest {

    @Test
    void enabledWithAPositiveValueUsesThatDayCount() {
        assertThat(BackdateWindowSettings.resolveMaxBackdateDays(true, 90L)).isEqualTo(90);
        assertThat(BackdateWindowSettings.resolveMaxBackdateDays(true, 1L)).isEqualTo(1);
    }

    @Test
    void disabledMeansNoDayLimit() {
        assertThat(BackdateWindowSettings.resolveMaxBackdateDays(false, 90L)).isNull();
        assertThat(BackdateWindowSettings.resolveMaxBackdateDays(false, null)).isNull();
    }

    @Test
    void missingRowFallsBackToTheDefaultWindow() {
        assertThat(BackdateWindowSettings.resolveMaxBackdateDays(null, null)).isEqualTo(BackdateWindowSettings.DEFAULT_MAX_BACKDATE_DAYS);
    }

    @Test
    void enabledWithoutAUsableValueFallsBackToTheDefaultWindow() {
        assertThat(BackdateWindowSettings.resolveMaxBackdateDays(true, null)).isEqualTo(BackdateWindowSettings.DEFAULT_MAX_BACKDATE_DAYS);
        assertThat(BackdateWindowSettings.resolveMaxBackdateDays(true, 0L)).isEqualTo(BackdateWindowSettings.DEFAULT_MAX_BACKDATE_DAYS);
        assertThat(BackdateWindowSettings.resolveMaxBackdateDays(true, -5L)).isEqualTo(BackdateWindowSettings.DEFAULT_MAX_BACKDATE_DAYS);
    }

    @Test
    void absurdlyLargeValueIsClampedInsteadOfOverflowing() {
        assertThat(BackdateWindowSettings.resolveMaxBackdateDays(true, Long.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
    }

    /**
     * Regression: the static accessor used to return the resolved window through a ternary whose other branch was the
     * int default, making the expression type int - so a switched-off day limit was unboxed into a NullPointerException
     * and every repayment/foreclosure template returned HTTP 500.
     */
    @Test
    void staticAccessorPassesThroughAnAbsentDayLimitWithoutUnboxing() {
        withPublishedSettings(null, () -> assertThat(BackdateWindowSettings.maxBackdateDays()).isNull());
    }

    @Test
    void staticAccessorPassesThroughAConfiguredDayCount() {
        withPublishedSettings(90, () -> assertThat(BackdateWindowSettings.maxBackdateDays()).isEqualTo(90));
    }

    /** Publishes a settings bean whose database read is stubbed, then restores the unconfigured state. */
    private void withPublishedSettings(final Integer resolvedWindow, final Runnable assertion) {
        final BackdateWindowSettings settings = new BackdateWindowSettings(mock(JdbcTemplate.class)) {

            @Override
            Integer readMaxBackdateDays() {
                return resolvedWindow;
            }
        };
        BackdateWindowSettings.publishInstance(settings);
        try {
            assertion.run();
        } finally {
            BackdateWindowSettings.publishInstance(null);
        }
    }
}
