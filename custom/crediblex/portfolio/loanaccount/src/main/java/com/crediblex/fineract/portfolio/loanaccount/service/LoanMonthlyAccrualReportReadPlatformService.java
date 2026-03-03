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
package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportRowData;
import com.crediblex.fineract.portfolio.loanaccount.data.LoanMonthlyAccrualData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Reads accrual report data for a loan using the same calculation logic as "Generate Loan Monthly Accrual Summations"
 * job: monthly interest from accrual transactions (m_loan_transaction, type 10), with principal from schedule.
 * <p>
 * The job is used for posting (e.g. month-end); this service is for report generation only: it reuses only the logic
 * needed to compute accrued interest on a monthly basis. When the report is run from the repayment schedule page, the
 * present (business) date is used so that the current month shows interest accrued only up to that date in "Actual
 * Interest Accrued". No posting or side effects occur.
 */
@Service
@RequiredArgsConstructor
public class LoanMonthlyAccrualReportReadPlatformService {

    private static final int ACCRUAL_TRANSACTION_TYPE = 10;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Retrieves the accrual report for a loan: month-wise interest accrued (same logic as job, from accrual
     * transactions) and opening/closing principal (from schedule). Uses businessDate so that for the current month
     * "Actual Interest Accrued" is day-weighted up to the present date only.
     */
    public List<AccrualReportRowData> retrieveAccrualReportForLoan(final Long loanId, final LocalDate businessDate) {
        List<LoanMonthlyAccrualData> accrualByMonth = retrieveMonthlyAccrualForLoan(loanId);
        List<ScheduleInstallment> schedule = retrieveScheduleWithOutstanding(loanId);
        if (schedule.isEmpty() && accrualByMonth.isEmpty()) {
            return List.of();
        }

        LocalDate loanStart = earliestDueDate(schedule);
        LocalDate maturityDate = schedule.isEmpty() ? null : latestDueDate(schedule);
        if (!accrualByMonth.isEmpty()) {
            LocalDate firstAccrual = accrualByMonth.get(0).getAccrualMonthDate();
            LocalDate lastAccrual = accrualByMonth.get(accrualByMonth.size() - 1).getAccrualMonthDate();
            if (loanStart == null || firstAccrual.isBefore(loanStart)) {
                loanStart = firstAccrual.withDayOfMonth(1);
            }
            if (maturityDate == null || lastAccrual.plusMonths(1).isAfter(maturityDate)) {
                maturityDate = lastAccrual;
            }
        }
        if (loanStart == null || maturityDate == null) {
            return List.of();
        }

        Map<LocalDate, BigDecimal> interestByMonth = new LinkedHashMap<>();
        for (LoanMonthlyAccrualData d : accrualByMonth) {
            interestByMonth.put(d.getAccrualMonthDate(), d.getTotalInterestAccrualDerived());
        }
        // When no accrual transactions exist (e.g. periodic accrual not run), use schedule interest so report has data
        Map<LocalDate, BigDecimal> scheduleInterestByMonth = scheduleInterestByMonth(schedule);

        List<AccrualReportRowData> rows = new ArrayList<>();
        int index = 1;
        LocalDate monthStart = loanStart.withDayOfMonth(1);
        BigDecimal openingPrincipal = initialPrincipal(schedule, monthStart);

        while (!monthStart.isAfter(maturityDate)) {
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            if (monthEnd.isAfter(maturityDate)) {
                monthEnd = maturityDate;
            }

            BigDecimal closingPrincipal = closingPrincipalForDate(schedule, monthEnd);
            if (monthEnd.equals(maturityDate)) {
                closingPrincipal = BigDecimal.ZERO;
            }

            BigDecimal interest = interestByMonth.getOrDefault(monthStart, BigDecimal.ZERO);
            if (interest == null || interest.compareTo(BigDecimal.ZERO) == 0) {
                interest = scheduleInterestByMonth.getOrDefault(monthStart, BigDecimal.ZERO);
            }
            // Actual Interest Accrued: full month for past; day-weighted up to businessDate for current month; null for
            // future
            BigDecimal actualInterest = actualInterestAccruedToDate(monthStart, monthEnd, interest, businessDate);

            rows.add(AccrualReportRowData.builder().index(index).endOfMonth(monthEnd)
                    .openingPrincipal(openingPrincipal != null ? openingPrincipal : BigDecimal.ZERO)
                    .closingPrincipal(closingPrincipal != null ? closingPrincipal : BigDecimal.ZERO).interestAccrued(interest)
                    .actualInterestAccrued(actualInterest).build());

            openingPrincipal = closingPrincipal;
            monthStart = monthStart.plusMonths(1);
            index++;
        }

        return rows;
    }

    /**
     * Actual Interest Accrued: for past months full interest; for current month day-weighted up to businessDate; for
     * future months null (report shows blank).
     */
    private BigDecimal actualInterestAccruedToDate(LocalDate monthStart, LocalDate monthEnd, BigDecimal fullMonthInterest,
            LocalDate businessDate) {
        if (monthEnd.isBefore(businessDate)) {
            return fullMonthInterest;
        }
        if (monthStart.isAfter(businessDate)) {
            return null;
        }
        // Current month: prorate by days from month start to businessDate (inclusive)
        int daysInMonth = monthStart.lengthOfMonth();
        int daysToDate = (int) ChronoUnit.DAYS.between(monthStart, businessDate) + 1;
        if (daysToDate <= 0) {
            return null;
        }
        if (daysToDate >= daysInMonth) {
            return fullMonthInterest;
        }
        return fullMonthInterest.multiply(BigDecimal.valueOf(daysToDate)).divide(BigDecimal.valueOf(daysInMonth), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Same aggregation as GenerateLoanMonthlyAccrualSummationsTasklet but for one loan and all accrual transactions (no
     * filter on is_processed_by_monthly_job).
     */
    private List<LoanMonthlyAccrualData> retrieveMonthlyAccrualForLoan(final Long loanId) {
        final String sql = """
                SELECT
                    date_trunc('month', mlt.transaction_date)::date AS "accrualMonth",
                    SUM(mlt.interest_portion_derived) AS "totalInterestAccrualDerived"
                FROM m_loan_transaction mlt
                WHERE mlt.loan_id = ?
                    AND mlt.transaction_type_enum = ?
                    AND COALESCE(mlt.interest_portion_derived, 0) > 0
                    AND mlt.is_reversed = FALSE
                GROUP BY date_trunc('month', mlt.transaction_date)
                ORDER BY "accrualMonth"
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> LoanMonthlyAccrualData.builder().loanId(loanId).accrualMonthDate(rs.getDate("accrualMonth").toLocalDate())
                        .totalInterestAccrualDerived(rs.getBigDecimal("totalInterestAccrualDerived")).build(),
                loanId, ACCRUAL_TRANSACTION_TYPE);
    }

    private static final class ScheduleInstallment {

        LocalDate dueDate;
        BigDecimal principalDue;
        BigDecimal principalOutstanding;
        BigDecimal interestDue;
    }

    private LocalDate earliestDueDate(List<ScheduleInstallment> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return null;
        }
        LocalDate earliest = null;
        for (ScheduleInstallment inst : schedule) {
            if (inst.dueDate != null && (earliest == null || inst.dueDate.isBefore(earliest))) {
                earliest = inst.dueDate;
            }
        }
        return earliest;
    }

    private LocalDate latestDueDate(List<ScheduleInstallment> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return null;
        }
        LocalDate latest = null;
        for (ScheduleInstallment inst : schedule) {
            if (inst.dueDate != null && (latest == null || inst.dueDate.isAfter(latest))) {
                latest = inst.dueDate;
            }
        }
        return latest;
    }

    /** Sum of schedule interest by calendar month (from installments whose due date falls in that month). */
    private Map<LocalDate, BigDecimal> scheduleInterestByMonth(List<ScheduleInstallment> schedule) {
        Map<LocalDate, BigDecimal> byMonth = new LinkedHashMap<>();
        for (ScheduleInstallment inst : schedule) {
            if (inst.dueDate == null || inst.interestDue == null || inst.interestDue.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            LocalDate monthKey = inst.dueDate.withDayOfMonth(1);
            byMonth.merge(monthKey, inst.interestDue, BigDecimal::add);
        }
        return byMonth;
    }

    /**
     * Fetches schedule with principal and interest. Includes all rows that have principal_amount and/or interest_amount
     * so the first period (e.g. disbursement with interest but zero principal) is included for first-month interest.
     */
    private List<ScheduleInstallment> retrieveScheduleWithOutstanding(final Long loanId) {
        String sql = """
                SELECT installment, duedate, principal_amount, interest_amount
                FROM m_loan_repayment_schedule
                WHERE loan_id = ? AND (principal_amount IS NOT NULL OR interest_amount IS NOT NULL)
                ORDER BY installment ASC
                """;
        List<ScheduleInstallment> raw = jdbcTemplate.query(sql, new RowMapper<ScheduleInstallment>() {

            @Override
            public ScheduleInstallment mapRow(ResultSet rs, int rowNum) throws SQLException {
                ScheduleInstallment i = new ScheduleInstallment();
                i.dueDate = rs.getDate("duedate") != null ? rs.getDate("duedate").toLocalDate() : null;
                i.principalDue = rs.getBigDecimal("principal_amount");
                i.interestDue = rs.getBigDecimal("interest_amount");
                return i;
            }
        }, loanId);

        BigDecimal running = BigDecimal.ZERO;
        for (int i = raw.size() - 1; i >= 0; i--) {
            ScheduleInstallment inst = raw.get(i);
            BigDecimal principal = inst.principalDue != null ? inst.principalDue : BigDecimal.ZERO;
            running = running.add(principal);
            inst.principalOutstanding = running;
        }
        return raw;
    }

    /** Principal at start of monthStart = principal at beginning of first period with dueDate >= monthStart. */
    private BigDecimal initialPrincipal(List<ScheduleInstallment> schedule, LocalDate monthStart) {
        if (schedule.isEmpty()) {
            return BigDecimal.ZERO;
        }
        for (ScheduleInstallment inst : schedule) {
            if (inst.dueDate != null && !inst.dueDate.isBefore(monthStart) && inst.principalOutstanding != null) {
                return inst.principalOutstanding;
            }
        }
        return schedule.get(0).principalOutstanding != null ? schedule.get(0).principalOutstanding : BigDecimal.ZERO;
    }

    /** Principal outstanding at end of date = principal at start of first period with dueDate > date. */
    private BigDecimal closingPrincipalForDate(List<ScheduleInstallment> schedule, LocalDate date) {
        if (schedule.isEmpty()) {
            return BigDecimal.ZERO;
        }
        for (ScheduleInstallment inst : schedule) {
            if (inst.dueDate != null && inst.dueDate.isAfter(date) && inst.principalOutstanding != null) {
                return inst.principalOutstanding;
            }
        }
        return BigDecimal.ZERO;
    }
}
