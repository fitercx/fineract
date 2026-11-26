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
package com.crediblex.fineract.portfolio.loanaccount.jobs.accrualreports3export;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Loans exported on a run: every Active loan (including overdue and charged-off loans, which stay Active), plus loans
 * that closed, were written off or became overpaid inside (closedFromExclusive, businessDate] so they get one final
 * file.
 */
@Component
@RequiredArgsConstructor
public class AccrualReportExportLoanSelector {

    private static final String SQL = """
            SELECT l.id FROM m_loan l WHERE l.loan_status_id = 300
            UNION
            SELECT l.id FROM m_loan l WHERE l.loan_status_id IN (600, 601, 602, 700)
              AND ((l.closedon_date > ? AND l.closedon_date <= ?)
                OR (l.writtenoffon_date > ? AND l.writtenoffon_date <= ?)
                OR (l.overpaidon_date > ? AND l.overpaidon_date <= ?))
            ORDER BY 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<Long> selectLoanIds(final LocalDate closedFromExclusive, final LocalDate businessDate) {
        return jdbcTemplate.queryForList(SQL, Long.class, closedFromExclusive, businessDate, closedFromExclusive, businessDate,
                closedFromExclusive, businessDate);
    }
}
