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

package com.crediblex.fineract.portfolio.account.repository;

import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EzySqlLoanLocRepository {

    private final JdbcTemplate jdbcTemplate;

    public EzySqlLoanLocRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByLoanId(Long loanId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM m_loan_line_of_credit_params WHERE loan_id = ?", Integer.class,
                loanId);
        return count != null && count > 0;
    }

    public Optional<Long> findLocIdByLoanId(Long loanId) {
        try {
            Long locId = jdbcTemplate.queryForObject("SELECT line_of_credit_id FROM m_loan_line_of_credit_params WHERE loan_id = ? LIMIT 1",
                    Long.class, loanId);
            return Optional.ofNullable(locId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
