package com.crediblex.fineract.portfolio.dpdrepayment.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DpdMaxDaysPastDueService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;

    public int calculateMaxDpd(final Long loanId, final LocalDate asOfDate) {
        if (loanId == null) {
            return 0;
        }
        final LocalDate effectiveDate = asOfDate != null ? asOfDate : DateUtils.getBusinessLocalDate();
        final String dpdExpression = sqlGenerator.dateDiff("?", "ls.duedate");
        final String sql = "select coalesce(max(" + dpdExpression + "), 0) from m_loan_repayment_schedule ls "
                + "where ls.loan_id = ? and ls.duedate < ? and " + OverdueInstallmentBalanceSql.hasChargeableOutstanding("ls");
        try {
            final Integer maxDpd = jdbcTemplate.queryForObject(sql, Integer.class, effectiveDate, loanId, effectiveDate);
            return maxDpd != null && maxDpd > 0 ? maxDpd : 0;
        } catch (final EmptyResultDataAccessException e) {
            return 0;
        }
    }
}
