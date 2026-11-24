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

import java.math.BigDecimal;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads the unpaid Late Payment Interest (LPI) on a loan: active overdue-installment penalty charges only. Other
 * penalty charges are deliberately excluded.
 */
@Repository
public class LoanLpiOutstandingReader {

    private static final String LPI_OUTSTANDING_SQL = """
            SELECT COALESCE(SUM(lc.amount_outstanding_derived), 0)
            FROM m_loan_charge lc
            WHERE lc.loan_id = ?
              AND lc.is_active = true
              AND lc.is_penalty = true
              AND lc.charge_time_enum = ?
              AND lc.is_paid_derived = false
              AND lc.waived = false
            """;

    private final JdbcTemplate jdbcTemplate;

    public LoanLpiOutstandingReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BigDecimal outstandingLpi(Long loanId) {
        BigDecimal outstanding = jdbcTemplate.queryForObject(LPI_OUTSTANDING_SQL, BigDecimal.class, loanId,
                ChargeTimeType.OVERDUE_INSTALLMENT.getValue());
        return outstanding == null ? BigDecimal.ZERO : outstanding;
    }
}
