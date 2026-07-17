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
package com.crediblex.fineract.integration.odoo.settlements;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Reads disbursement/refund settlement events from savings transactions tagged with {@code transaction_sub_type} 1
 * (Disbursal) or 2 (Refund).
 */
@Service
@RequiredArgsConstructor
public class SettlementsReportReadService {

    private static final String BASE_SELECT = """
            SELECT
                c.display_name AS client_name,
                loan_info.product_name AS product_name,
                CASE t.transaction_sub_type
                    WHEN 1 THEN 'Disbursement'
                    WHEN 2 THEN 'Refund'
                END AS event,
                loan_info.loan_id AS loan_id,
                sa.id AS savings_account_id,
                t.amount AS amount
            FROM m_savings_account_transaction t
            JOIN m_savings_account sa ON sa.id = t.savings_account_id
            JOIN m_client c ON c.id = sa.client_id
            LEFT JOIN LATERAL (
                SELECT
                    l.id AS loan_id,
                    pl.name AS product_name
                FROM m_portfolio_account_associations paa
                JOIN m_loan l ON l.id = paa.loan_account_id
                JOIN m_product_loan pl ON pl.id = l.product_id
                WHERE paa.linked_savings_account_id = sa.id
                  AND paa.association_type_enum = 1
                  AND paa.is_active = TRUE
                ORDER BY
                    CASE WHEN l.disbursedon_date = t.transaction_date THEN 0 ELSE 1 END,
                    ABS(COALESCE(l.principal_disbursed_derived, 0) - t.amount),
                    l.id DESC
                LIMIT 1
            ) loan_info ON TRUE
            WHERE t.is_reversed = FALSE
              AND t.transaction_sub_type IN (1, 2)
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Activity processed after {@code fromExclusive} up to and including {@code toInclusive}, based on transaction
     * created/submitted timestamp (falls back to transaction_date).
     */
    public List<SettlementsReportRow> findSinceLastRun(final OffsetDateTime fromExclusive, final OffsetDateTime toInclusive) {
        final String sql = BASE_SELECT + """
                  AND COALESCE(
                        t.created_on_utc,
                        t.created_date,
                        t.submitted_on_date::timestamp,
                        t.transaction_date::timestamp
                      ) > ?
                  AND COALESCE(
                        t.created_on_utc,
                        t.created_date,
                        t.submitted_on_date::timestamp,
                        t.transaction_date::timestamp
                      ) <= ?
                ORDER BY c.display_name, sa.id, t.id
                """;

        final Timestamp fromTs = Timestamp.from(fromExclusive.toInstant());
        final Timestamp toTs = Timestamp.from(toInclusive.toInstant());

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> SettlementsReportRow.builder().clientName(rs.getString("client_name"))
                        .productName(rs.getString("product_name")).event(rs.getString("event")).loanId(JdbcSupport.getLong(rs, "loan_id"))
                        .savingsAccountId(JdbcSupport.getLong(rs, "savings_account_id")).amount(rs.getBigDecimal("amount")).build(),
                fromTs, toTs);
    }
}
