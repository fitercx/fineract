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
     * @param installmentNumber
     *            The repayment installment being rendered
     * @param fromDate
     *            The start date of the period
     * @param dueDate
     *            The end date of the period
     * @param isPenalty
     *            true for penalty charges, false for fee charges
     * @return The sum of reversed charges for the period, or BigDecimal.ZERO if none found
     */
    public BigDecimal calculateReversedCharges(Long loanId, Integer installmentNumber, LocalDate fromDate, LocalDate dueDate,
            boolean isPenalty) {
        final String sql = "SELECT COALESCE(SUM(lcpb.amount), 0) FROM m_loan_charge lc "
                + "JOIN m_loan_charge_paid_by lcpb ON lcpb.loan_charge_id = lc.id "
                + "JOIN m_loan_transaction lt ON lt.id = lcpb.loan_transaction_id "
                + "LEFT JOIN m_loan_overdue_installment_charge loic ON loic.loan_charge_id = lc.id "
                + "LEFT JOIN m_loan_repayment_schedule linked_rs ON linked_rs.id = loic.loan_schedule_id "
                + "WHERE lc.loan_id = ? AND lc.is_active = false AND lc.is_penalty = ? "
                + "AND lt.is_reversed = false AND lt.transaction_type_enum = 26 "
                + "AND ((lc.charge_time_enum = 9 AND COALESCE(lcpb.installment_number, linked_rs.installment, "
                + "(SELECT CASE WHEN COUNT(*) = 1 THEN MIN(base_rs.installment) END FROM m_loan_repayment_schedule base_rs "
                + "WHERE base_rs.loan_id = lc.loan_id AND base_rs.is_down_payment = false AND base_rs.is_additional = false "
                + "AND base_rs.recalculated_interest_component = false AND base_rs.duedate <= lc.due_for_collection_as_of_date "
                + "AND ABS(base_rs.principal_amount - lc.calculation_on_amount) <= 0.01), "
                + "(SELECT MAX(date_rs.installment) FROM m_loan_repayment_schedule date_rs "
                + "WHERE date_rs.loan_id = lc.loan_id AND date_rs.is_down_payment = false AND date_rs.is_additional = false "
                + "AND date_rs.recalculated_interest_component = false AND date_rs.duedate = ("
                + "SELECT MAX(candidate_rs.duedate) FROM m_loan_repayment_schedule candidate_rs "
                + "WHERE candidate_rs.loan_id = lc.loan_id AND candidate_rs.is_down_payment = false "
                + "AND candidate_rs.is_additional = false AND candidate_rs.recalculated_interest_component = false "
                + "AND candidate_rs.duedate <= lc.due_for_collection_as_of_date))) = ?) "
                + "OR (lc.charge_time_enum = 2 AND lc.due_for_collection_as_of_date > ? " + "AND lc.due_for_collection_as_of_date <= ?))";

        try {
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, loanId, isPenalty, installmentNumber, fromDate, dueDate);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
