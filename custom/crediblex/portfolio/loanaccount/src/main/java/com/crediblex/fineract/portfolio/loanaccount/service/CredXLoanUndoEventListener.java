package com.crediblex.fineract.portfolio.loanaccount.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanUndoApprovalBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanUndoDisbursalBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanUndoLastDisbursalBusinessEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener that handles cleanup of standing instructions when loan operations are undone. This prevents duplicate
 * key violations when re-approving and re-disbursing loans.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CredXLoanUndoEventListener {

    private final CredXStandingInstructionCleanupService standingInstructionCleanupService;

    /**
     * Handles cleanup when loan disbursement is undone. This is triggered after the core Fineract undo disbursement
     * logic completes.
     *
     * @param event
     *            The loan undo disbursement business event
     */
    @EventListener
    public void handleLoanUndoDisbursal(LoanUndoDisbursalBusinessEvent event) {
        log.info("Handling LoanUndoDisbursalBusinessEvent for loan ID: {}", event.get().getId());
        try {
            standingInstructionCleanupService.cleanupForLoan(event.get());
        } catch (Exception e) {
            log.error("Error cleaning up standing instructions during undo disbursal for loan ID: {}", event.get().getId(), e);
            // Don't rethrow - we don't want to break the undo operation if cleanup fails
            // The worst case is that a duplicate key error will occur on re-disbursement,
            // which is better than breaking the undo operation
        }
    }

    /**
     * Handles cleanup when the last loan disbursement is undone (for multi-disbursal loans). This is triggered after
     * the core Fineract undo last disbursement logic completes.
     *
     * @param event
     *            The loan undo last disbursement business event
     */
    @EventListener
    public void handleLoanUndoLastDisbursal(LoanUndoLastDisbursalBusinessEvent event) {
        log.info("Handling LoanUndoLastDisbursalBusinessEvent for loan ID: {}", event.get().getId());
        try {
            standingInstructionCleanupService.cleanupForLoan(event.get());
        } catch (Exception e) {
            log.error("Error cleaning up standing instructions during undo last disbursal for loan ID: {}", event.get().getId(), e);
            // Don't rethrow - see handleLoanUndoDisbursal for rationale
        }
    }

    /**
     * Handles cleanup when loan approval is undone. This is triggered after the core Fineract undo approval logic
     * completes.
     *
     * Note: This may be called after undo disbursal, so we need to ensure idempotency.
     *
     * @param event
     *            The loan undo approval business event
     */
    @EventListener
    public void handleLoanUndoApproval(LoanUndoApprovalBusinessEvent event) {
        log.info("Handling LoanUndoApprovalBusinessEvent for loan ID: {}", event.get().getId());
        try {
            standingInstructionCleanupService.cleanupForLoan(event.get());
        } catch (Exception e) {
            log.error("Error cleaning up standing instructions during undo approval for loan ID: {}", event.get().getId(), e);
            // Don't rethrow - see handleLoanUndoDisbursal for rationale
        }
    }
}
