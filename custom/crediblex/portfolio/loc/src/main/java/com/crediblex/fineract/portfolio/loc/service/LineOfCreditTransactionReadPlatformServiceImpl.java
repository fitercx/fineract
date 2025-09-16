package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditTransactionData;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LineOfCreditTransactionReadPlatformServiceImpl implements LineOfCreditTransactionReadPlatformService {

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class TransactionMapper implements RowMapper<LineOfCreditTransactionData> {

        public String schema() {
            return " lct.id as id, lct.line_of_credit_id as lineOfCreditId, lct.transaction_type as transactionType, "
                    + "lct.amount as amount, lct.balance_before as balanceBefore, lct.balance_after as balanceAfter, "
                    + "lct.transaction_date as transactionDate, lct.reference_number as referenceNumber, "
                    + "lct.description as description, lct.created_on_utc as createdOn, lct.created_by as createdBy "
                    + "from m_line_of_credit_transactions lct ";
        }

        @Override
        public LineOfCreditTransactionData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final Long lineOfCreditId = rs.getLong("lineOfCreditId");
            final String transactionType = rs.getString("transactionType");
            final BigDecimal amount = rs.getBigDecimal("amount");
            final BigDecimal balanceBefore = rs.getBigDecimal("balanceBefore");
            final BigDecimal balanceAfter = rs.getBigDecimal("balanceAfter");
            final OffsetDateTime transactionDate = rs.getTimestamp("transactionDate") != null ?
                rs.getTimestamp("transactionDate").toInstant().atOffset(java.time.ZoneOffset.UTC) : null;
            final String referenceNumber = rs.getString("referenceNumber");
            final String description = rs.getString("description");
            final OffsetDateTime createdOn = rs.getTimestamp("createdOn") != null ?
                rs.getTimestamp("createdOn").toInstant().atOffset(java.time.ZoneOffset.UTC) : null;
            final String createdBy = rs.getString("createdBy");

            return LineOfCreditTransactionData.builder()
                    .id(id)
                    .lineOfCreditId(lineOfCreditId)
                    .transactionType(transactionType)
                    .amount(amount)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .transactionDate(transactionDate)
                    .referenceNumber(referenceNumber)
                    .description(description)
                    .createdOn(createdOn)
                    .createdBy(createdBy)
                    .build();
        }
    }

    @Override
    public Page<LineOfCreditTransactionData> retrieveAllTransactions(Long lineOfCreditId, Pageable pageable) {
        this.context.authenticatedUser();

        final TransactionMapper mapper = new TransactionMapper();

        final String countSql = "select count(*) from m_line_of_credit_transactions where line_of_credit_id = ?";
        final Integer totalElements = this.jdbcTemplate.queryForObject(countSql, Integer.class, lineOfCreditId);

        if (totalElements == null || totalElements == 0) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        final String sql = "select " + mapper.schema() + " where lct.line_of_credit_id = ? order by lct.transaction_date desc, lct.id desc";
        final String paginatedSql = sql + " limit " + pageable.getPageSize() + " offset " + pageable.getOffset();

        final java.util.List<LineOfCreditTransactionData> transactions = this.jdbcTemplate.query(paginatedSql, mapper, lineOfCreditId);

        return new PageImpl<>(transactions, pageable, totalElements);
    }

    @Override
    public LineOfCreditTransactionData retrieveTransaction(Long lineOfCreditId, Long transactionId) {
        this.context.authenticatedUser();

        try {
            final TransactionMapper mapper = new TransactionMapper();
            final String sql = "select " + mapper.schema() + " where lct.line_of_credit_id = ? and lct.id = ?";

            return this.jdbcTemplate.queryForObject(sql, mapper, lineOfCreditId, transactionId);
        } catch (final EmptyResultDataAccessException e) {
            return null;
        }
    }
}
