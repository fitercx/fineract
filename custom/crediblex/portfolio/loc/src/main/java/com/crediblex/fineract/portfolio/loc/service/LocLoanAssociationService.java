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

package com.crediblex.fineract.portfolio.loc.service;


import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for managing associations between loans and line of credit facilities.
 * This service provides functionality to associate/disassociate loans with line of credit.
 */
@Service
public class LocLoanAssociationService {

    private final JdbcTemplate jdbcTemplate;
    private final LineOfCreditRepository lineOfCreditRepository;

    @Autowired
    public LocLoanAssociationService(JdbcTemplate jdbcTemplate, LineOfCreditRepository lineOfCreditRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.lineOfCreditRepository = lineOfCreditRepository;
    }

    /**
     * Associate a loan with a line of credit.
     *
     * @param loanId the loan ID
     * @param lineOfCreditId the line of credit ID
     * @return true if association was successful, false otherwise
     */
    public boolean associateLoanWithLineOfCredit(Long loanId, Long lineOfCreditId) {
        // Validate that the line of credit exists
        if (!lineOfCreditRepository.existsById(lineOfCreditId)) {
            return false;
        }

        // Update the loan with the line of credit ID
        String sql = "UPDATE m_loan SET line_of_credit_id = ? WHERE id = ?";
        int rowsUpdated = jdbcTemplate.update(sql, lineOfCreditId, loanId);

        return rowsUpdated > 0;
    }
}
