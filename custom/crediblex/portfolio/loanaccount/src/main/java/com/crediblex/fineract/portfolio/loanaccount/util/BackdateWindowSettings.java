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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * How far back a repayment / savings-to-loan transfer / foreclosure may be dated, read from the tenant's global
 * configuration ({@code c_configuration}) so it is the same code in every environment and can be changed by an
 * administrator under Admin &gt; System &gt; Configurations without a release.
 * <p>
 * Config {@value #CONFIG_NAME}:
 * <ul>
 * <li>{@code enabled = true, value = N} - backdating allowed up to N days before the business date. Seeded as
 * {@value #DEFAULT_MAX_BACKDATE_DAYS} days.</li>
 * <li>{@code enabled = false} - no day limit; backdating allowed to the start of the loan's first instalment period
 * (still never before disbursement). This is what UAT/STG use to settle long-lived test loans.</li>
 * </ul>
 * Anything unexpected (row missing, unreadable, or {@code value} absent/not positive while enabled) falls back to
 * {@value #DEFAULT_MAX_BACKDATE_DAYS} days, so a misconfiguration can never silently remove the guard.
 * <p>
 * The static accessor exists because {@link BackdatedRepaymentValidator} is a static utility called from four write and
 * read paths across two Gradle modules; threading the value through every caller signature would be far more invasive.
 * Same workaround shape as core Fineract's {@code TemporaryConfigurationServiceContainer}. Reads before Spring has
 * initialised this bean (plain unit tests) get the safe default.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackdateWindowSettings implements InitializingBean {

    public static final String CONFIG_NAME = "backdated-transaction-max-days";

    /** Applied when the configuration row is missing or unusable - the long-standing hardcoded policy. */
    public static final int DEFAULT_MAX_BACKDATE_DAYS = 30;

    private static volatile BackdateWindowSettings instance;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Configured number of days a transaction may be backdated, or {@code null} when the day limit is switched off and
     * backdating is instead allowed to the start of the loan's first instalment period. Read fresh on every call so an
     * administrator's change takes effect immediately; both call paths are per-request single-loan operations, never
     * loops.
     */
    public static Integer maxBackdateDays() {
        final BackdateWindowSettings settings = BackdateWindowSettings.instance;
        // Deliberately not a ternary: mixing the int constant with the Integer result makes the expression type int,
        // which unboxes a null return into a NullPointerException whenever the day limit is switched off.
        if (settings == null) {
            return DEFAULT_MAX_BACKDATE_DAYS;
        }
        return settings.readMaxBackdateDays();
    }

    // Package-private rather than private so a test can stub out the database read.
    Integer readMaxBackdateDays() {
        try {
            final BackdateWindowConfigRow row = jdbcTemplate
                    .queryForObject("SELECT enabled, value FROM c_configuration WHERE name = ? LIMIT 1", (rs, rowNum) -> {
                        // c_configuration.value is a nullable INT, so read it and check wasNull rather than casting
                        // getObject(), which hands back an Integer. wasNull() reports on the most recent column read,
                        // so it has to be consumed here, before any other getter runs.
                        final long rawValue = rs.getLong("value");
                        final Long value = rs.wasNull() ? null : rawValue;
                        return new BackdateWindowConfigRow(rs.getBoolean("enabled"), value);
                    }, CONFIG_NAME);
            return resolveMaxBackdateDays(row == null ? null : row.enabled(), row == null ? null : row.value());
        } catch (EmptyResultDataAccessException e) {
            return DEFAULT_MAX_BACKDATE_DAYS;
        } catch (Exception e) {
            log.warn("Could not read '{}' configuration, falling back to {} days: {}", CONFIG_NAME, DEFAULT_MAX_BACKDATE_DAYS,
                    e.getMessage());
            return DEFAULT_MAX_BACKDATE_DAYS;
        }
    }

    /**
     * Maps the raw configuration row onto the effective window. Visible for testing so the semantics can be asserted
     * without a database.
     */
    static Integer resolveMaxBackdateDays(final Boolean enabled, final Long value) {
        if (enabled == null) {
            return DEFAULT_MAX_BACKDATE_DAYS;
        }
        if (!enabled) {
            return null;
        }
        if (value == null || value <= 0) {
            log.warn("Configuration '{}' is enabled without a positive day count (value={}), falling back to {} days", CONFIG_NAME, value,
                    DEFAULT_MAX_BACKDATE_DAYS);
            return DEFAULT_MAX_BACKDATE_DAYS;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        publishInstance(this);
    }

    // Static write routed through a static method (SpotBugs flags static writes from instance methods). Visible for
    // testing so a test can publish a stub and reset it afterwards.
    static void publishInstance(final BackdateWindowSettings settings) {
        BackdateWindowSettings.instance = settings;
    }

    private record BackdateWindowConfigRow(Boolean enabled, Long value) {
    }
}
