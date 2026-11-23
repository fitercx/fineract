package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepositoryWrapper;
import com.crediblex.fineract.portfolio.loc.exception.LineOfCreditNotFoundException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.exception.RollbackTransactionNotApprovedException;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds one pending expiry-change slot per facility in its own transaction.
 *
 * <p>
 * The command handler's transaction rolls back when maker-checker leaves the request awaiting approval, and that
 * rollback happens before the awaiting command row is committed. A lock taken only inside the handler is already gone
 * by then. This slot is committed first and kept until the awaiting row exists, so a second submit cannot pass the
 * pending check in that window.
 */
@Service
@RequiredArgsConstructor
public class LineOfCreditExpiryPendingLock {

    static final String ACTION = "CHANGEEXPIRYDATE";
    static final String ENTITY = "LINE_OF_CREDIT";
    static final int AWAITING_APPROVAL = 2;

    private final LineOfCreditRepositoryWrapper lineOfCreditRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(final Long clientId, final Long lineOfCreditId) {
        assertClient(clientId, lineOfCreditId);
        final List<Map<String, Object>> rows = this.jdbcTemplate.queryForList("""
                SELECT command_id FROM m_loc_expiry_change_guard
                WHERE line_of_credit_id = ?
                FOR UPDATE
                """, lineOfCreditId);
        if (!rows.isEmpty()) {
            final Long commandId = commandId(rows.get(0).get("command_id"));
            if (slotIsHeld(commandId, commandId != null && commandAwaiting(commandId, lineOfCreditId))) {
                throw pending();
            }
            this.jdbcTemplate.update("DELETE FROM m_loc_expiry_change_guard WHERE line_of_credit_id = ?", lineOfCreditId);
        }
        if (awaitingCount(lineOfCreditId) > 0) {
            throw pending();
        }
        try {
            this.jdbcTemplate.update("INSERT INTO m_loc_expiry_change_guard (line_of_credit_id, command_id) VALUES (?, NULL)",
                    lineOfCreditId);
        } catch (DataIntegrityViolationException ex) {
            throw pending(ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attach(final Long lineOfCreditId, final Long commandId) {
        if (commandId == null) {
            return;
        }
        this.jdbcTemplate.update("UPDATE m_loc_expiry_change_guard SET command_id = ? WHERE line_of_credit_id = ?", commandId,
                lineOfCreditId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(final Long lineOfCreditId) {
        this.jdbcTemplate.update("DELETE FROM m_loc_expiry_change_guard WHERE line_of_credit_id = ?", lineOfCreditId);
    }

    /**
     * A slot with no command id is a submit that has not saved its awaiting row yet. A slot whose command is still
     * awaiting stays held. A slot whose command was approved, rejected, or deleted can be taken.
     */
    static boolean slotIsHeld(final Long commandId, final boolean commandAwaiting) {
        return commandId == null || commandAwaiting;
    }

    public static RollbackTransactionNotApprovedException rollbackCause(final Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof RollbackTransactionNotApprovedException rollback) {
                return rollback;
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean commandAwaiting(final Long commandId, final Long lineOfCreditId) {
        final Integer count = this.jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM m_portfolio_command_source
                WHERE id = ? AND action_name = ? AND entity_name = ? AND resource_id = ? AND status = ?
                """, Integer.class, commandId, ACTION, ENTITY, lineOfCreditId, AWAITING_APPROVAL);
        return count != null && count > 0;
    }

    private int awaitingCount(final Long lineOfCreditId) {
        final Integer count = this.jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM m_portfolio_command_source
                WHERE action_name = ? AND entity_name = ? AND resource_id = ? AND status = ?
                """, Integer.class, ACTION, ENTITY, lineOfCreditId, AWAITING_APPROVAL);
        return count == null ? 0 : count;
    }

    private void assertClient(final Long clientId, final Long lineOfCreditId) {
        final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findOneWithNotFoundDetection(lineOfCreditId);
        if (lineOfCredit.getClient() == null || !clientId.equals(lineOfCredit.getClient().getId())) {
            throw new LineOfCreditNotFoundException(lineOfCredit.getId());
        }
    }

    private static Long commandId(final Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }

    private static PlatformApiDataValidationException pending() {
        return pending(null);
    }

    private static PlatformApiDataValidationException pending(final Throwable cause) {
        return new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError("validation.msg.loc.expiry.pending",
                LineOfCreditExpiryDateRules.PENDING_MESSAGE, "endDate")), cause);
    }
}
