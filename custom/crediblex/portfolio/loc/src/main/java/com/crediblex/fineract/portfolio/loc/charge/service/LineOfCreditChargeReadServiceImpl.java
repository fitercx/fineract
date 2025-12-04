package com.crediblex.fineract.portfolio.loc.charge.service;

import com.crediblex.fineract.portfolio.loc.charge.data.LocChargeData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LineOfCreditChargeReadServiceImpl implements LineOfCreditChargeReadService {

    private final JdbcTemplate jdbcTemplate;

    public LineOfCreditChargeReadServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final class LocChargeRowMapper implements RowMapper<LocChargeData> {

        @Override
        public LocChargeData mapRow(ResultSet rs, int rowNum) throws SQLException {
            return LocChargeData.builder().id(rs.getLong("id")).chargeDefinitionId(rs.getLong("chargeDefinitionId"))
                    .penalty(rs.getBoolean("penalty")).chargeTime(rs.getInt("chargeTime")).chargeCalculation(rs.getInt("chargeCalculation"))
                    .dueDate(rs.getDate("dueDate") != null ? rs.getDate("dueDate").toLocalDate() : null)
                    .feeOnMonth(getInteger(rs, "feeOnMonth")).feeOnDay(getInteger(rs, "feeOnDay"))
                    .feeInterval(getInteger(rs, "feeInterval")).amount(rs.getBigDecimal("amount"))
                    .amountOutstanding(rs.getBigDecimal("amountOutstanding")).amountPaid(rs.getBigDecimal("amountPaid"))
                    .amountWaived(rs.getBigDecimal("amountWaived")).paid(rs.getBoolean("isPaid")).waived(rs.getBoolean("waived"))
                    .chargeName(rs.getString("chargeName")).active(rs.getBoolean("active"))
                    .percentageAmount(rs.getBigDecimal("percentageAmount")).taxAmount(rs.getBigDecimal("taxAmount")).build();
        }

        private Integer getInteger(ResultSet rs, String column) throws SQLException {
            int val = rs.getInt(column);
            return rs.wasNull() ? null : val;
        }
    }

    private static final String BASE_SELECT = "SELECT locc.tax_amount as taxAmount,locc.calculation_percentage as percentageAmount, c.name as chargeName,locc.id as id, locc.charge_id as chargeDefinitionId, locc.is_penalty as penalty, "
            + "locc.charge_time_enum as chargeTime, locc.charge_calculation_enum as chargeCalculation, locc.charge_due_date as dueDate, "
            + "locc.fee_on_month as feeOnMonth, locc.fee_on_day as feeOnDay, locc.fee_interval as feeInterval, locc.amount as amount, "
            + "locc.amount_outstanding_derived as amountOutstanding, locc.amount_paid_derived as amountPaid, locc.amount_waived_derived as amountWaived, "
            + "locc.is_paid_derived as isPaid, locc.waived as waived, locc.is_active as active " + "FROM m_line_of_credit_charge locc "
            + " left join m_charge c on locc.charge_id = c.id ";

    @Override
    public List<LocChargeData> listActive(Long locId) {
        String sql = BASE_SELECT + "WHERE locc.line_of_credit_id = ? AND locc.is_active = true";
        return jdbcTemplate.query(sql, new LocChargeRowMapper(), locId);
    }

    @Override
    public List<LocChargeData> listDueOrOverdue(Long locId, LocalDate asOfDate) {
        String sql = BASE_SELECT
                + "WHERE locc.line_of_credit_id = ? AND locc.is_active = true AND locc.waived = false AND locc.is_paid_derived = false "
                + "AND locc.charge_due_date IS NOT NULL AND locc.charge_due_date <= ?";
        return jdbcTemplate.query(sql, new LocChargeRowMapper(), locId, asOfDate);
    }

    @Override
    public LocChargeData getOne(Long locId, Long chargeInstanceId) {
        String sql = BASE_SELECT + "WHERE locc.line_of_credit_id = ? AND locc.id = ?";
        List<LocChargeData> list = jdbcTemplate.query(sql, new LocChargeRowMapper(), locId, chargeInstanceId);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Charge not found");
        }
        return list.get(0);
    }
}
