package com.crediblex.fineract.portfolio.savings.service;

import com.crediblex.fineract.portfolio.savings.data.CredXSavingsAssociatedLoanData;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CredXSavingsAssociatedLoanService {

    private static final String RBF_PRODUCT_SHORT_NAME = "RBF";

    private static final String RECORD_LOAN_LINK_SQL = """
            UPDATE m_savings_account_transaction
            SET associated_loan_id = ?,
                associated_from_installment = NULL,
                associated_to_installment = NULL
            WHERE id = ?
            """;

    private static final String RECORD_EMI_TRANSFER_SQL = """
            UPDATE m_savings_account_transaction
            SET associated_loan_id = ?,
                associated_from_installment = ?,
                associated_to_installment = ?
            WHERE id = ?
            """;

    private static final String IS_RBF_LOAN_SQL = """
            SELECT pl.short_name
            FROM m_loan l
            JOIN m_product_loan pl ON pl.id = l.product_id
            WHERE l.id = ?
            """;

    private static final String INSTALLMENT_RANGE_SQL = """
            SELECT MIN(rs.installment) AS from_installment,
                   MAX(rs.installment) AS to_installment
            FROM m_loan_transaction_repayment_schedule_mapping map
            JOIN m_loan_repayment_schedule rs ON rs.id = map.loan_repayment_schedule_id
            WHERE map.loan_transaction_id = ?
            """;

    private static final String RETRIEVE_ASSOCIATED_LOANS_SQL = """
            SELECT tx.id AS savings_transaction_id,
                   tx.associated_loan_id,
                   l.client_id AS loan_client_id,
                   tx.associated_from_installment,
                   tx.associated_to_installment
            FROM m_savings_account_transaction tx
            JOIN m_loan l ON l.id = tx.associated_loan_id
            WHERE tx.savings_account_id = ?
              AND tx.associated_loan_id IS NOT NULL
            ORDER BY tx.transaction_date DESC, tx.id DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public void recordLoanLink(final Long savingsTransactionId, final Long loanId) {
        if (savingsTransactionId == null || loanId == null) {
            return;
        }
        this.jdbcTemplate.update(RECORD_LOAN_LINK_SQL, loanId, savingsTransactionId);
    }

    public void recordEmiTransfer(final Long savingsTransactionId, final Long loanId, final Long loanTransactionId) {
        if (savingsTransactionId == null || loanId == null) {
            return;
        }

        Integer fromInstallment = null;
        Integer toInstallment = null;
        if (loanTransactionId != null && isRbfLoan(loanId)) {
            final Integer[] range = resolveInstallmentRange(loanTransactionId);
            fromInstallment = range[0];
            toInstallment = range[1];
        }

        this.jdbcTemplate.update(RECORD_EMI_TRANSFER_SQL, loanId, fromInstallment, toInstallment, savingsTransactionId);
    }

    public Map<Long, CredXSavingsAssociatedLoanData> retrieveAssociatedLoans(final Long savingsAccountId) {
        return this.jdbcTemplate.query(RETRIEVE_ASSOCIATED_LOANS_SQL, rs -> {
            final Map<Long, CredXSavingsAssociatedLoanData> associatedLoans = new LinkedHashMap<>();
            while (rs.next()) {
                associatedLoans.put(rs.getLong("savings_transaction_id"),
                        new CredXSavingsAssociatedLoanData(rs.getLong("associated_loan_id"), rs.getLong("loan_client_id"),
                                JdbcSupport.getInteger(rs, "associated_from_installment"),
                                JdbcSupport.getInteger(rs, "associated_to_installment")));
            }
            return associatedLoans;
        }, savingsAccountId);
    }

    private boolean isRbfLoan(final Long loanId) {
        try {
            final String shortName = this.jdbcTemplate.queryForObject(IS_RBF_LOAN_SQL, String.class, loanId);
            return RBF_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (EmptyResultDataAccessException ex) {
            return false;
        }
    }

    private Integer[] resolveInstallmentRange(final Long loanTransactionId) {
        try {
            return this.jdbcTemplate.query(INSTALLMENT_RANGE_SQL, rs -> {
                if (!rs.next()) {
                    return new Integer[] { null, null };
                }
                return new Integer[] { JdbcSupport.getInteger(rs, "from_installment"), JdbcSupport.getInteger(rs, "to_installment") };
            }, loanTransactionId);
        } catch (EmptyResultDataAccessException ex) {
            return new Integer[] { null, null };
        }
    }
}
