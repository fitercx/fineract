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

package com.crediblex.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for calculating reversed charges for loan repayment schedule periods. This service handles the calculation of
 * reversed fee and penalty charges that fall within a specific period.
 */
@Service
public class CustomReversedChargeCalculationService {

    private final JdbcTemplate jdbcTemplate;

    public CustomReversedChargeCalculationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Calculates reversed charges for a specific period. This method queries the database for inactive charges that
     * fall within the given period.
     *
     * @param loanId
     *            The loan ID
     * @param fromDate
     *            The start date of the period
     * @param dueDate
     *            The end date of the period
     * @param isPenalty
     *            true for penalty charges, false for fee charges
     * @return The sum of reversed charges for the period, or BigDecimal.ZERO if none found
     */
    public BigDecimal calculateReversedCharges(Long loanId, LocalDate fromDate, LocalDate dueDate, boolean isPenalty) {
        // This method calculates reversed charges for a specific period
        // We need to query the database for inactive charges that fall within this period
        final String sql = "SELECT COALESCE(SUM(lc.amount), 0) FROM m_loan_charge lc "
                + "WHERE lc.loan_id = ? AND lc.is_active = false AND lc.is_penalty = ? "
                + "AND ((lc.charge_time_enum = 4 AND lc.due_for_collection_as_of_date >= ? AND lc.due_for_collection_as_of_date <= ?) "
                + "OR (lc.charge_time_enum = 2 AND ? <= ? AND ? >= ?))";

        try {
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, loanId, isPenalty, fromDate, dueDate, fromDate, fromDate, dueDate,
                    fromDate);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
