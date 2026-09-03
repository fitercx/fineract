package com.crediblex.fineract.portfolio.savings.service;

import com.crediblex.fineract.portfolio.savings.data.CredXSavingsTransactionSubTypeData;
import com.crediblex.fineract.portfolio.savings.domain.CredXSavingsTransactionSubType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CredXSavingsTransactionSubTypeService {

    private static final int DEPOSIT_TRANSACTION_TYPE = 1;
    private static final int WITHDRAWAL_TRANSACTION_TYPE = 2;
    public static final String FORECLOSURE_REFUND_DESCRIPTION = "Foreclosure refund of prepaid future-installment income";
    private static final String UPDATE_SUB_TYPE_SQL = """
            UPDATE m_savings_account_transaction
            SET transaction_sub_type = ?
            WHERE id = ?
              AND transaction_type_enum = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public void markFromPaymentType(final Long savingsTransactionId) {
        if (savingsTransactionId == null) {
            return;
        }
        try {
            final Integer subType = this.jdbcTemplate.queryForObject("""
                    SELECT pt.savings_transaction_sub_type
                    FROM m_savings_account_transaction tx
                    JOIN m_payment_detail pd ON pd.id = tx.payment_detail_id
                    JOIN m_payment_type pt ON pt.id = pd.payment_type_id
                    WHERE tx.id = ?
                      AND tx.transaction_type_enum = ?
                      AND pt.savings_transaction_sub_type IS NOT NULL
                    """, Integer.class, savingsTransactionId, WITHDRAWAL_TRANSACTION_TYPE);
            markSubType(savingsTransactionId, CredXSavingsTransactionSubType.fromValue(subType));
        } catch (EmptyResultDataAccessException ignored) {
            // Payment types without a configured savings subtype remain legacy parent-only withdrawals.
        }
    }

    public void markDisbursal(final Long savingsTransactionId) {
        markSubType(savingsTransactionId, CredXSavingsTransactionSubType.DISBURSAL);
    }

    public void markRefund(final Long savingsTransactionId) {
        markSubType(savingsTransactionId, CredXSavingsTransactionSubType.REFUND);
    }

    public void markEmiTransfer(final Long savingsTransactionId) {
        markSubType(savingsTransactionId, CredXSavingsTransactionSubType.EMI_TRANSFER);
    }

    /**
     * Marks the linked-savings deposit created by the foreclosure prepaid-income undo. This semantic marker is kept
     * separate from normal deposits and LPI refunds so repayment, LPI reprocessing, and foreclosure accounting retain
     * their existing transaction behavior while the savings UI can identify the source of the credit.
     */
    public void markForeclosureRefund(final Long savingsTransactionId) {
        markSubType(savingsTransactionId, CredXSavingsTransactionSubType.FORECLOSURE_REFUND, DEPOSIT_TRANSACTION_TYPE);
    }

    public void markSubType(final Long savingsTransactionId, final CredXSavingsTransactionSubType subType) {
        markSubType(savingsTransactionId, subType, WITHDRAWAL_TRANSACTION_TYPE);
    }

    private void markSubType(final Long savingsTransactionId, final CredXSavingsTransactionSubType subType, final int transactionType) {
        if (savingsTransactionId == null || subType == null) {
            return;
        }
        this.jdbcTemplate.update(UPDATE_SUB_TYPE_SQL, subType.getValue(), savingsTransactionId, transactionType);
    }

    public Map<Long, CredXSavingsTransactionSubTypeData> retrieveSubTypes(final Long savingsAccountId) {
        final String sql = """
                SELECT tx.id,
                       COALESCE(tx.transaction_sub_type,
                                CASE WHEN transfer.description = ? THEN ? END) AS transaction_sub_type
                FROM m_savings_account_transaction tx
                LEFT JOIN m_account_transfer_transaction transfer
                       ON transfer.to_savings_transaction_id = tx.id
                      AND transfer.is_reversed = false
                WHERE tx.savings_account_id = ?
                  AND (tx.transaction_sub_type IS NOT NULL OR transfer.description = ?)
                ORDER BY tx.transaction_date DESC, tx.id DESC
                """;
        return this.jdbcTemplate.query(sql, rs -> {
            final Map<Long, CredXSavingsTransactionSubTypeData> subTypes = new LinkedHashMap<>();
            while (rs.next()) {
                addSubType(subTypes, rs);
            }
            return subTypes;
        }, FORECLOSURE_REFUND_DESCRIPTION, CredXSavingsTransactionSubType.FORECLOSURE_REFUND.getValue(), savingsAccountId,
                FORECLOSURE_REFUND_DESCRIPTION);
    }

    public Map<Long, CredXSavingsTransactionSubTypeData> retrieveSubType(final Long savingsAccountId, final Long savingsTransactionId) {
        final String sql = """
                SELECT tx.id,
                       COALESCE(tx.transaction_sub_type,
                                CASE WHEN transfer.description = ? THEN ? END) AS transaction_sub_type
                FROM m_savings_account_transaction tx
                LEFT JOIN m_account_transfer_transaction transfer
                       ON transfer.to_savings_transaction_id = tx.id
                      AND transfer.is_reversed = false
                WHERE tx.id = ?
                  AND tx.savings_account_id = ?
                  AND (tx.transaction_sub_type IS NOT NULL OR transfer.description = ?)
                """;
        final List<Map.Entry<Long, CredXSavingsTransactionSubTypeData>> entries = this.jdbcTemplate.query(sql, (rs, rowNum) -> {
            final CredXSavingsTransactionSubType subType = CredXSavingsTransactionSubType
                    .fromValue(JdbcSupport.getInteger(rs, "transaction_sub_type"));
            return Map.entry(rs.getLong("id"), new CredXSavingsTransactionSubTypeData(subType));
        }, FORECLOSURE_REFUND_DESCRIPTION, CredXSavingsTransactionSubType.FORECLOSURE_REFUND.getValue(), savingsTransactionId,
                savingsAccountId, FORECLOSURE_REFUND_DESCRIPTION);
        final Map<Long, CredXSavingsTransactionSubTypeData> subTypes = new LinkedHashMap<>();
        entries.stream().filter(entry -> entry.getValue().getValue() != null)
                .forEach(entry -> subTypes.put(entry.getKey(), entry.getValue()));
        return subTypes;
    }

    private void addSubType(final Map<Long, CredXSavingsTransactionSubTypeData> subTypes, final ResultSet rs) throws SQLException {
        final CredXSavingsTransactionSubType subType = CredXSavingsTransactionSubType
                .fromValue(JdbcSupport.getInteger(rs, "transaction_sub_type"));
        if (subType != null) {
            subTypes.put(rs.getLong("id"), new CredXSavingsTransactionSubTypeData(subType));
        }
    }
}
