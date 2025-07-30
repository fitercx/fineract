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

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom service to handle Line of Credit relationships for loans.
 */
@Service
public class CustomLoanLineOfCreditService {

    private final JdbcTemplate jdbcTemplate;

    public CustomLoanLineOfCreditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Links a loan to a line of credit.
     * This method should be called after the main loan creation/update.
     */
    @Transactional
    public void linkLoanToLineOfCredit(Long loanId, JsonCommand command) {
        if (command.parameterExists("lineOfCreditId")) {
            Long lineOfCreditId = command.longValueOfParameterNamed("lineOfCreditId");
            
            // Validate that the line of credit exists
            if (lineOfCreditId != null && !lineOfCreditExists(lineOfCreditId)) {
                throw new IllegalArgumentException("Line of Credit with ID " + lineOfCreditId + " does not exist");
            }
            
            String sql = "UPDATE m_loan SET line_of_credit_id = ? WHERE id = ?";
            jdbcTemplate.update(sql, lineOfCreditId, loanId);
        }
    }

    /**
     * Retrieves the line of credit ID associated with a loan.
     */
    public Long getLineOfCreditIdForLoan(Long loanId) {
        String sql = "SELECT line_of_credit_id FROM m_loan WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, loanId);
    }

    /**
     * Retrieves all loans associated with a specific line of credit.
     */
    public java.util.List<Long> getLoansForLineOfCredit(Long lineOfCreditId) {
        String sql = "SELECT id FROM m_loan WHERE line_of_credit_id = ?";
        return jdbcTemplate.queryForList(sql, Long.class, lineOfCreditId);
    }

    /**
     * Unlinks a loan from its line of credit.
     */
    @Transactional
    public void unlinkLoanFromLineOfCredit(Long loanId) {
        String sql = "UPDATE m_loan SET line_of_credit_id = NULL WHERE id = ?";
        jdbcTemplate.update(sql, loanId);
    }

    /**
     * Validates that a line of credit exists.
     */
    private boolean lineOfCreditExists(Long lineOfCreditId) {
        String sql = "SELECT COUNT(*) FROM m_line_of_credit WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, lineOfCreditId);
        return count != null && count > 0;
    }
} 