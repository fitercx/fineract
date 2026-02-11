package com.crediblex.fineract.portfolio.loanaccount.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanUndoApprovalBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanUndoDisbursalBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanUndoLastDisbursalBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.domain.AccountTransferRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferTransaction;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Business event listener that handles cleanup of standing instructions and related account transfers when loan
 * operations are undone. This prevents duplicate key violations when re-approving and re-disbursing loans and ensures
 * that disbursements made to savings are correctly reversed on undo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CredXLoanUndoEventListener implements InitializingBean {

    private final CredXStandingInstructionCleanupService standingInstructionCleanupService;
    private final AccountTransferRepository accountTransferRepository;
    private final AccountTransfersWritePlatformService accountTransfersWritePlatformService;
    private final BusinessEventNotifierService businessEventNotifierService;

    @Override
    public void afterPropertiesSet() {
        // Register this listener for the relevant loan undo events using typed lambdas
        businessEventNotifierService.addPostBusinessEventListener(LoanUndoDisbursalBusinessEvent.class, this::handleUndoDisbursal);
        businessEventNotifierService.addPostBusinessEventListener(LoanUndoLastDisbursalBusinessEvent.class, this::handleUndoLastDisbursal);
        businessEventNotifierService.addPostBusinessEventListener(LoanUndoApprovalBusinessEvent.class, this::handleUndoApproval);
        log.info(
                "CredXLoanUndoEventListener registered for LoanUndoDisbursalBusinessEvent, LoanUndoLastDisbursalBusinessEvent and LoanUndoApprovalBusinessEvent");
    }

    private void handleUndoDisbursal(LoanUndoDisbursalBusinessEvent event) {
        Loan loan = event.get();
        log.info("CredXLoanUndoEventListener handling LoanUndoDisbursalBusinessEvent for loan ID: {}", loan.getId());
        cleanupStandingInstructionsSafely(loan, "undo disbursal");
        reverseLoanDisbursalsToSavings(loan);
    }

    private void handleUndoLastDisbursal(LoanUndoLastDisbursalBusinessEvent event) {
        Loan loan = event.get();
        log.info("CredXLoanUndoEventListener handling LoanUndoLastDisbursalBusinessEvent for loan ID: {}", loan.getId());
        cleanupStandingInstructionsSafely(loan, "undo last disbursal");
        reverseLoanDisbursalsToSavings(loan);
    }

    private void handleUndoApproval(LoanUndoApprovalBusinessEvent event) {
        Loan loan = event.get();
        log.info("CredXLoanUndoEventListener handling LoanUndoApprovalBusinessEvent for loan ID: {}", loan.getId());
        cleanupStandingInstructionsSafely(loan, "undo approval");
    }

    private void reverseLoanDisbursalsToSavings(Loan loan) {
        List<AccountTransferTransaction> transfersFromLoan = accountTransferRepository.findByFromLoanId(loan.getId());
        if (transfersFromLoan == null || transfersFromLoan.isEmpty()) {
            log.info("No loan-to-savings account transfer records found for loan ID: {}. Nothing to reverse.", loan.getId());
            return;
        }

        log.info("Found {} account transfer record(s) with from-loan ID: {} to inspect for reversal.", transfersFromLoan.size(),
                loan.getId());

        Set<Long> disbursementLoanTransactionIds = transfersFromLoan.stream()
                .filter(att -> att.accountTransferDetails() != null && att.accountTransferDetails().toSavingsAccount() != null)
                .map(AccountTransferTransaction::getFromLoanTransaction).filter(Objects::nonNull)
                .filter(ltx -> ltx.getTypeOf().isDisbursement() && ltx.isReversed()).map(LoanTransaction::getId)
                .collect(Collectors.toSet());

        if (disbursementLoanTransactionIds.isEmpty()) {
            log.info(
                    "No reversed loan DISBURSEMENT transactions linked to loan-to-savings transfers were found for loan ID: {}. Nothing to reverse on savings.",
                    loan.getId());
            return;
        }

        log.info("Reversing {} loan-to-savings disbursement transfer(s) for loan ID: {}", disbursementLoanTransactionIds.size(),
                loan.getId());

        accountTransfersWritePlatformService.reverseTransfersWithFromAccountTransactions(disbursementLoanTransactionIds,
                PortfolioAccountType.LOAN);
    }

    private void cleanupStandingInstructionsSafely(Loan loan, String operation) {
        try {
            standingInstructionCleanupService.cleanupForLoan(loan);
        } catch (Exception e) {
            log.error("Error cleaning up standing instructions during {} for loan ID: {}", operation, loan.getId(), e);
        }
    }
}
