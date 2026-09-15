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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Computes a loan's maximum days past due: the age of its oldest installment that is overdue and still carries an
 * outstanding balance.
 *
 * <p>
 * The age is derived in Java from the earliest such due date rather than with a SQL date difference, which keeps the
 * query portable across the databases Fineract supports.
 */
@Service
@RequiredArgsConstructor
public class DpdMaxDaysPastDueService {

    private static final String EARLIEST_UNPAID_OVERDUE_DUE_DATE_SQL = """
            SELECT MIN(ls.duedate)
              FROM m_loan_repayment_schedule ls
             WHERE ls.loan_id = ?
               AND ls.duedate < ?
               AND (COALESCE(ls.principal_amount, 0) - COALESCE(ls.principal_completed_derived, 0)
                        - COALESCE(ls.principal_writtenoff_derived, 0)
                    + COALESCE(ls.interest_amount, 0) - COALESCE(ls.interest_completed_derived, 0)
                        - COALESCE(ls.interest_waived_derived, 0) - COALESCE(ls.interest_writtenoff_derived, 0)
                    + COALESCE(ls.fee_charges_amount, 0) - COALESCE(ls.fee_charges_completed_derived, 0)
                        - COALESCE(ls.fee_charges_waived_derived, 0) - COALESCE(ls.fee_charges_writtenoff_derived, 0)
                    + COALESCE(ls.penalty_charges_amount, 0) - COALESCE(ls.penalty_charges_completed_derived, 0)
                        - COALESCE(ls.penalty_charges_waived_derived, 0) - COALESCE(ls.penalty_charges_writtenoff_derived, 0)) > 0
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @return days past due of the loan's oldest unsettled overdue installment as of {@code asOfDate}, or 0 when the
     *         loan has nothing overdue.
     */
    public int calculateMaxDpd(final Long loanId, final LocalDate asOfDate) {
        if (loanId == null || asOfDate == null) {
            return 0;
        }
        return daysPastDue(jdbcTemplate.queryForObject(EARLIEST_UNPAID_OVERDUE_DUE_DATE_SQL, LocalDate.class, loanId, asOfDate), asOfDate);
    }

    /**
     * Same measure as {@link #calculateMaxDpd(Long, LocalDate)} but read off the loan's in-memory schedule.
     *
     * <p>
     * Callers that hold the {@link Loan} entity must use this overload. The SQL variant reads committed rows, so it
     * cannot see schedule changes made earlier in the same transaction - a repayment that has just been allocated, or
     * penalties applied by an earlier COB step - and would answer with the loan's pre-change position.
     */
    public int calculateMaxDpd(final Loan loan, final LocalDate asOfDate) {
        if (loan == null || asOfDate == null) {
            return 0;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        LocalDate earliestOverdueDueDate = null;
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            final LocalDate dueDate = installment.getDueDate();
            if (installment.isDownPayment() || dueDate == null || !dueDate.isBefore(asOfDate)) {
                continue;
            }
            if (!installment.getTotalOutstanding(currency).isGreaterThanZero()) {
                continue;
            }
            if (earliestOverdueDueDate == null || dueDate.isBefore(earliestOverdueDueDate)) {
                earliestOverdueDueDate = dueDate;
            }
        }
        return daysPastDue(earliestOverdueDueDate, asOfDate);
    }

    private int daysPastDue(final LocalDate earliestOverdueDueDate, final LocalDate asOfDate) {
        if (earliestOverdueDueDate == null) {
            return 0;
        }
        final long daysPastDue = ChronoUnit.DAYS.between(earliestOverdueDueDate, asOfDate);
        return daysPastDue > 0 ? (int) daysPastDue : 0;
    }
}
