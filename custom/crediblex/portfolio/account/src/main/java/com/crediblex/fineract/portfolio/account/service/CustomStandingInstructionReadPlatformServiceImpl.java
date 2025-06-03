package com.crediblex.fineract.portfolio.account.service;

import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.portfolio.account.data.StandingInstructionDuesData;
import org.apache.fineract.portfolio.account.service.PortfolioAccountReadPlatformService;
import org.apache.fineract.portfolio.account.service.StandingInstructionReadPlatformServiceImpl;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

@Service
@Primary
public class CustomStandingInstructionReadPlatformServiceImpl extends StandingInstructionReadPlatformServiceImpl {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;

    public CustomStandingInstructionReadPlatformServiceImpl(final JdbcTemplate jdbcTemplate,
                                                            final ClientReadPlatformService clientReadPlatformService, final OfficeReadPlatformService officeReadPlatformService,
                                                            final PortfolioAccountReadPlatformService portfolioAccountReadPlatformService,
                                                            final DropdownReadPlatformService dropdownReadPlatformService, final ColumnValidator columnValidator,
                                                            DatabaseSpecificSQLGenerator sqlGenerator, PaginationHelper paginationHelper) {
        super(jdbcTemplate, clientReadPlatformService, officeReadPlatformService, portfolioAccountReadPlatformService,
                dropdownReadPlatformService, columnValidator, sqlGenerator, paginationHelper);
        this.jdbcTemplate = jdbcTemplate;
        this.sqlGenerator = sqlGenerator;
    }

    @Override
    public StandingInstructionDuesData retriveLoanDuesData(final Long loanId) {
        final StandingInstructionLoanDuesMapper rm = new StandingInstructionLoanDuesMapper();
        final String sql = "select " + rm.schema() + " where ml.id= ? and ls.duedate <= " + sqlGenerator.currentBusinessDate()
                + " and ls.completed_derived = false";
        return this.jdbcTemplate.queryForObject(sql, rm, new Object[] { loanId }); // NOSONAR
    }

    private static final class StandingInstructionLoanDuesMapper implements RowMapper<StandingInstructionDuesData> {

        private final String schemaSql;

        StandingInstructionLoanDuesMapper() {
            final StringBuilder sqlBuilder = new StringBuilder(400);

            sqlBuilder.append("max(ls.duedate) as dueDate,sum(ls.principal_amount) as principalAmount,");
            sqlBuilder.append("sum(ls.principal_completed_derived) as principalCompleted,");
            sqlBuilder.append("sum(ls.principal_writtenoff_derived) as principalWrittenOff,");
            sqlBuilder.append("sum(ls.interest_amount) as interestAmount,");
            sqlBuilder.append("sum(ls.interest_completed_derived) as interestCompleted,");
            sqlBuilder.append("sum(ls.interest_writtenoff_derived) as interestWrittenOff,");
            sqlBuilder.append("sum(ls.interest_waived_derived) as interestWaived,");
            sqlBuilder.append("sum(ls.penalty_charges_amount) as penalityAmount,");
            sqlBuilder.append("sum(ls.penalty_charges_completed_derived) as penalityCompleted,");
            sqlBuilder.append("sum(ls.penalty_charges_writtenoff_derived)as penaltyWrittenOff,");
            sqlBuilder.append("sum(ls.penalty_charges_waived_derived) as penaltyWaived,");
            sqlBuilder.append("sum(ls.fee_charges_amount) as feeAmount,");
            sqlBuilder.append("sum(ls.fee_charges_completed_derived) as feecompleted,");
            sqlBuilder.append("sum(ls.fee_charges_writtenoff_derived) as feeWrittenOff,");
            sqlBuilder.append("sum(ls.fee_charges_waived_derived) as feeWaived ");
            sqlBuilder.append("from m_loan_repayment_schedule ls ");
            sqlBuilder.append(" join m_loan ml on ml.id = ls.loan_id ");

            this.schemaSql = sqlBuilder.toString();
        }

        public String schema() {
            return this.schemaSql;
        }

        @Override
        public StandingInstructionDuesData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

            final LocalDate dueDate = JdbcSupport.getLocalDate(rs, "dueDate");
            final BigDecimal principalDue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalAmount");
            final BigDecimal principalPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalCompleted");
            final BigDecimal principalWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalWrittenOff");
            final BigDecimal principalOutstanding = principalDue.subtract(principalPaid).subtract(principalWrittenOff);

            final BigDecimal interestExpectedDue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestAmount");
            final BigDecimal interestPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestCompleted");
            final BigDecimal interestWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestWrittenOff");
            final BigDecimal interestWaived = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestWaived");
            final BigDecimal interestActualDue = interestExpectedDue.subtract(interestWaived).subtract(interestWrittenOff);
            final BigDecimal interestOutstanding = interestActualDue.subtract(interestPaid);

            final BigDecimal penaltyChargesExpectedDue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penalityAmount");
            final BigDecimal penaltyChargesPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penalityCompleted");
            final BigDecimal penaltyChargesWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyWrittenOff");
            final BigDecimal penaltyChargesWaived = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyWaived");
            final BigDecimal penaltyChargesActualDue = penaltyChargesExpectedDue.subtract(penaltyChargesWaived)
                    .subtract(penaltyChargesWrittenOff);
            final BigDecimal penaltyChargesOutstanding = penaltyChargesActualDue.subtract(penaltyChargesPaid);

            final BigDecimal feeChargesExpectedDue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeAmount");
            final BigDecimal feeChargesPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feecompleted");
            final BigDecimal feeChargesWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeWrittenOff");
            final BigDecimal feeChargesWaived = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeWaived");
            final BigDecimal feeChargesActualDue = feeChargesExpectedDue.subtract(feeChargesWaived).subtract(feeChargesWrittenOff);
            final BigDecimal feeChargesOutstanding = feeChargesActualDue.subtract(feeChargesPaid);

            final BigDecimal totalOutstanding = principalOutstanding.add(interestOutstanding).add(feeChargesOutstanding)
                    .add(penaltyChargesOutstanding);

            return new StandingInstructionDuesData(dueDate, totalOutstanding);
        }
    }
}
