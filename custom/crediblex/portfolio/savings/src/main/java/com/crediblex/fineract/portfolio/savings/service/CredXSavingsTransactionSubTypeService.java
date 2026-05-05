package com.crediblex.fineract.portfolio.savings.service;

import com.crediblex.fineract.portfolio.savings.data.CredXSavingsTransactionSubTypeData;
import com.crediblex.fineract.portfolio.savings.domain.CredXSavingsTransactionSubType;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CredXSavingsTransactionSubTypeService {

    private static final int WITHDRAWAL_TRANSACTION_TYPE = 2;
    private static final String UPDATE_SUB_TYPE_SQL = """
            UPDATE m_savings_account_transaction
            SET transaction_sub_type = ?
            WHERE id = ?
              AND transaction_type_enum = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public void markDisbursal(final Long savingsTransactionId) {
        markSubType(savingsTransactionId, CredXSavingsTransactionSubType.DISBURSAL);
    }

    public void markRefund(final Long savingsTransactionId) {
        markSubType(savingsTransactionId, CredXSavingsTransactionSubType.REFUND);
    }

    public void markEmiTransfer(final Long savingsTransactionId) {
        markSubType(savingsTransactionId, CredXSavingsTransactionSubType.EMI_TRANSFER);
    }

    public void markSubType(final Long savingsTransactionId, final CredXSavingsTransactionSubType subType) {
        if (savingsTransactionId == null || subType == null) {
            return;
        }
        this.jdbcTemplate.update(UPDATE_SUB_TYPE_SQL, subType.getValue(), savingsTransactionId, WITHDRAWAL_TRANSACTION_TYPE);
    }

    public Map<Long, CredXSavingsTransactionSubTypeData> retrieveSubTypes(final Long savingsAccountId) {
        final String sql = """
                SELECT id, transaction_sub_type
                FROM m_savings_account_transaction
                WHERE savings_account_id = ?
                  AND transaction_sub_type IS NOT NULL
                ORDER BY transaction_date DESC, id DESC
                """;
        return this.jdbcTemplate.query(sql, rs -> {
            final Map<Long, CredXSavingsTransactionSubTypeData> subTypes = new LinkedHashMap<>();
            while (rs.next()) {
                final CredXSavingsTransactionSubType subType = CredXSavingsTransactionSubType
                        .fromValue(JdbcSupport.getInteger(rs, "transaction_sub_type"));
                if (subType != null) {
                    subTypes.put(rs.getLong("id"), new CredXSavingsTransactionSubTypeData(subType));
                }
            }
            return subTypes;
        }, savingsAccountId);
    }
}
