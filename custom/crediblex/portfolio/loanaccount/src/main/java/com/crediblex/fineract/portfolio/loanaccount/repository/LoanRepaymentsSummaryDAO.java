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

    /**
     * Sums installment principal/interest paid by repayments dated on or before {@code asOfDate} (excludes reversed
     * txns). Used by the backdated penalties template so P/I due match the selected value date, not later partial
     * payments.
     */
    public java.util.Map<Integer, InstallmentPaymentsAsOf> fetchInstallmentPaymentsOnOrBefore(final Long loanId, final LocalDate asOfDate) {
        final String sql = """
                select rs.installment as installmentNumber,
                       coalesce(sum(m.principal_portion_derived), 0) as principalPaid,
                       coalesce(sum(m.interest_portion_derived), 0) as interestPaid
                from m_loan_transaction_repayment_schedule_mapping m
                join m_loan_transaction t on t.id = m.loan_transaction_id
                join m_loan_repayment_schedule rs on rs.id = m.loan_repayment_schedule_id
                where t.loan_id = ?
                  and t.is_reversed = false
                  and t.transaction_type_enum = 2
                  and t.transaction_date <= ?
                group by rs.installment
                """;
        return jdbcTemplate.query(sql, rs -> {
            java.util.Map<Integer, InstallmentPaymentsAsOf> map = new java.util.HashMap<>();
            while (rs.next()) {
                map.put(rs.getInt("installmentNumber"),
                        new InstallmentPaymentsAsOf(JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalPaid"),
                                JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestPaid")));
            }
            return map;
        }, loanId, java.sql.Date.valueOf(asOfDate));
    }

    public record InstallmentPaymentsAsOf(java.math.BigDecimal principalPaid, java.math.BigDecimal interestPaid) {

        public static final InstallmentPaymentsAsOf ZERO = new InstallmentPaymentsAsOf(java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO);
    }

    public String loanPaymentsSummarySchema() {
        return """
                select
                    installment as installmentNumber,
                    fromdate as fromDate,
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

            final Date fromDate = rs.getDate("fromDate");
            final Date dueDate = rs.getDate("dueDate");
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

            return ExtendedLoanSchedulePeriodData.paymentsSummaryPeriod(installmentNumber, toLocalDateSafe(fromDate),
                    toLocalDateSafe(dueDate), isComplete, principalDue, penaltyChargesExpectedDue, totalPaidForPeriod,
                    totalOutstandingForPeriod, interestOutstanding, interestExpectedDue, interestPaid, interestWaived, interestWrittenOff,
                    principalOutstanding);
        }

        private LocalDate toLocalDateSafe(Date date) {
            return date != null ? date.toLocalDate() : null;
        }
    }
}
