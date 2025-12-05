package com.crediblex.fineract.portfolio.loanaccount.repository;

import com.crediblex.fineract.portfolio.loanaccount.data.ExtendedLoanSchedulePeriodData;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LoanRepaymentsSummaryDAO {

    private final JdbcTemplate jdbcTemplate;

    public LoanRepaymentsSummaryDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Collection<LoanSchedulePeriodData> fetchLoanRepaymentsSummary(final Long loanId) {
        return jdbcTemplate.query(loanPaymentsSummarySchema(), new LoanRepaymentsSummaryMapper(), loanId);
    }

    public String loanPaymentsSummarySchema() {
        return """
                select
                    installment as installmentNumber,
                    duedate as dueDate,
                    completed_derived as isComplete,
                    principal_amount as principalDue,
                    principal_completed_derived as principalPaid,
                    principal_writtenoff_derived as principalWrittenOff,
                    interest_amount as interestDue,
                    interest_waived_derived as interestWaived,
                    interest_writtenoff_derived as interestWrittenOff,
                    interest_completed_derived as interestPaid,
                    fee_charges_amount as feeChargesDue,
                    fee_charges_waived_derived as feeChargesWaived,
                    fee_charges_writtenoff_derived as feeChargesWrittenOff,
                    fee_charges_completed_derived as feeChargesPaid,
                    penalty_charges_amount as penaltyChargesDue,
                    penalty_charges_waived_derived as penaltyChargesWaived,
                    penalty_charges_writtenoff_derived as penaltyChargesWrittenOff,
                    penalty_charges_completed_derived as penaltyChargesPaid
                from m_loan_repayment_schedule
                where loan_id = ?
                order by installment asc
                """;
    }

    private static final class LoanRepaymentsSummaryMapper implements RowMapper<LoanSchedulePeriodData> {

        @Override
        public LoanSchedulePeriodData mapRow(ResultSet rs, int rowNum) throws SQLException {
            final Integer installmentNumber = JdbcSupport.getInteger(rs, "installmentNumber");

            final Date dueDate = rs.getDate("duedate");
            final boolean isComplete = rs.getBoolean("isComplete");

            final BigDecimal principalDue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalDue");
            final BigDecimal principalPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalPaid");
            final BigDecimal principalWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalWrittenOff");

            final BigDecimal interestExpectedDue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestDue");
            final BigDecimal interestWaived = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestWaived");
            final BigDecimal interestWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestWrittenOff");
            final BigDecimal interestPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestPaid");

            final BigDecimal feeChargesExpectedDue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeChargesDue");
            final BigDecimal feeChargesPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeChargesPaid");
            final BigDecimal feeChargesWaived = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeChargesWaived");
            final BigDecimal feeChargesWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeChargesWrittenOff");

            final BigDecimal penaltyChargesExpectedDue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyChargesDue");
            final BigDecimal penaltyChargesPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyChargesPaid");
            final BigDecimal penaltyChargesWaived = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyChargesWaived");
            final BigDecimal penaltyChargesWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyChargesWrittenOff");

            final BigDecimal totalPaidForPeriod = principalPaid.add(interestPaid).add(feeChargesPaid).add(penaltyChargesPaid);

            final BigDecimal principalOutstanding = principalDue.subtract(principalPaid).subtract(principalWrittenOff);

            final BigDecimal interestActualDue = interestExpectedDue.subtract(interestWaived).subtract(interestWrittenOff);
            final BigDecimal interestOutstanding = interestActualDue.subtract(interestPaid);

            final BigDecimal feeChargesActualDue = feeChargesExpectedDue.subtract(feeChargesWaived).subtract(feeChargesWrittenOff);
            final BigDecimal feeChargesOutstanding = feeChargesActualDue.subtract(feeChargesPaid);

            final BigDecimal penaltyChargesActualDue = penaltyChargesExpectedDue.subtract(penaltyChargesWaived)
                    .subtract(penaltyChargesWrittenOff);
            final BigDecimal penaltyChargesOutstanding = penaltyChargesActualDue.subtract(penaltyChargesPaid);

            final BigDecimal totalOutstandingForPeriod = principalOutstanding.add(interestOutstanding).add(feeChargesOutstanding)
                    .add(penaltyChargesOutstanding);

            return ExtendedLoanSchedulePeriodData.paymentsSummaryPeriod(installmentNumber, toLocalDateSafe(dueDate), isComplete,
                    principalDue, penaltyChargesExpectedDue, totalPaidForPeriod, totalOutstandingForPeriod, interestOutstanding,
                    principalOutstanding);
        }

        private LocalDate toLocalDateSafe(Date date) {
            return date != null ? date.toLocalDate() : null;
        }
    }
}
