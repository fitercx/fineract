/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.account.service;

import com.crediblex.fineract.commands.LoanStatusWebhookPublisher;
import com.crediblex.fineract.infrastructure.commands.utils.LoanTransactionInstallmentUtils;
import com.crediblex.fineract.infrastructure.events.business.domain.accounttransfer.SavingsToLoanTransferReversedBusinessEvent;
import com.crediblex.fineract.portfolio.account.exception.StandingInstructionHistoryNotFoundException;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionType;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditBalanceUpdateService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.account.domain.AccountTransferRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSubStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingInstructionReversalServiceImpl implements StandingInstructionReversalService {

    private final JdbcTemplate jdbcTemplate;
    private final AccountTransferRepository accountTransferRepository;
    private final LoanAccountDomainService loanAccountDomainService;
    private final SavingsAccountWritePlatformService savingsAccountWritePlatformService;
    private final PlatformSecurityContext context;
    private final BusinessEventNotifierService businessEventNotifierService;
    private final LoanStatusWebhookPublisher loanStatusWebhookPublisher;

    @Autowired(required = false)
    private LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

    @Autowired(required = false)
    private LineOfCreditBalanceUpdateService lineOfCreditBalanceUpdateService;

    @Override
    @Transactional
    public CommandProcessingResult reverseExecution(final Long historyId, final JsonCommand command) {
        context.authenticatedUser().validateHasUpdatePermission("STANDING_INSTRUCTION_HISTORY");

        // 1. Load history row
        HistoryRow history = loadHistoryRow(historyId);

        // 2. Validate the execution is reversible
        validateHistoryRow(history, historyId);

        // 3. Resolve the AccountTransferTransaction
        AccountTransferTransaction att = resolveAccountTransferTransaction(history);

        if (att.isReversed()) {
            throw new GeneralPlatformDomainRuleException("error.msg.standing.instruction.transfer.already.reversed",
                    "The account transfer for standing instruction history " + historyId + " has already been reversed.");
        }

        // 4. Validate loan state
        Loan loan = att.accountTransferDetails().toLoanAccount();
        validateLoanState(loan);

        // 5. Validate no subsequent repayment-type transactions exist after this one
        LoanTransaction repaymentTransaction = att.getToLoanTransaction();
        validateNoSubsequentTransactions(loan, repaymentTransaction);

        // 6. Validate mandatory note
        String note = command.stringValueOfParameterNamed("note");
        if (StringUtils.isBlank(note)) {
            throw new GeneralPlatformDomainRuleException("error.msg.standing.instruction.reversal.note.required",
                    "A note is required when reversing a standing instruction payment.");
        }

        // 7. Atomic reversal — mirrors AccountTransfersWritePlatformServiceImpl.undoTransactions()
        loanAccountDomainService.reverseTransfer(repaymentTransaction);
        savingsAccountWritePlatformService.undoTransaction(att.accountTransferDetails().fromSavingsAccount().getId(),
                att.getFromTransaction().getId(), true);
        att.reverse();
        accountTransferRepository.save(att);

        // 8. Stamp history row with reversal metadata (optimistic lock: WHERE is_reversed=false)
        Long currentUserId = context.authenticatedUser().getId();
        int rowsUpdated = jdbcTemplate.update(
                "UPDATE m_account_transfer_standing_instructions_history SET is_reversed=true, reversed_at=now(), reversed_by_user_id=? WHERE id=? AND is_reversed=false",
                currentUserId, historyId);
        if (rowsUpdated == 0) {
            throw new GeneralPlatformDomainRuleException("error.msg.standing.instruction.execution.already.reversed",
                    "Standing instruction execution " + historyId + " has already been reversed by another request.");
        }

        // 9. Update LOC balance if applicable
        updateLocBalanceIfApplicable(loan, repaymentTransaction);

        // 10. Recompute CustomLoanStatus
        LoanTransactionInstallmentUtils.computeCustomLoanStatusForLoan(loan);

        // 11. Fire business event
        businessEventNotifierService.notifyPostBusinessEvent(new SavingsToLoanTransferReversedBusinessEvent(att.accountTransferDetails()));

        // 12. Publish webhook after transaction commits
        final Loan finalLoan = loan;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                try {
                    loanStatusWebhookPublisher.publish(finalLoan, finalLoan.hasCustomStatus() ? finalLoan.getCustomLoanStatus() : null);
                } catch (Exception e) {
                    log.warn("Failed to publish webhook after standing instruction reversal for loan {}: {}", finalLoan.getId(),
                            e.getMessage());
                }
            }
        });

        return new CommandProcessingResultBuilder().withEntityId(historyId).build();
    }

    private void validateHistoryRow(final HistoryRow history, final Long historyId) {
        if (!"success".equals(history.status()) && !"partial".equals(history.status())) {
            throw new GeneralPlatformDomainRuleException("error.msg.standing.instruction.execution.not.reversible",
                    "Standing instruction execution " + historyId + " has status '" + history.status()
                            + "' and transferred no funds — nothing to reverse.");
        }
        if (history.isReversed()) {
            throw new GeneralPlatformDomainRuleException("error.msg.standing.instruction.execution.already.reversed",
                    "Standing instruction execution " + historyId + " has already been reversed.");
        }
    }

    private void validateLoanState(final Loan loan) {
        if (loan.isClosedWrittenOff()) {
            throw new GeneralPlatformDomainRuleException("error.msg.standing.instruction.loan.written.off",
                    "Cannot reverse standing instruction payment on a written-off loan.");
        }
        if (loan.getLoanSubStatus() != null && LoanSubStatus.FORECLOSED.equals(loan.getLoanSubStatus())) {
            throw new GeneralPlatformDomainRuleException("error.msg.standing.instruction.loan.foreclosed",
                    "Cannot reverse standing instruction payment on a foreclosed loan.");
        }
    }

    private void validateNoSubsequentTransactions(final Loan loan, final LoanTransaction targetTransaction) {
        boolean hasSubsequent = loan.getLoanTransactions().stream().filter(t -> !t.isReversed())
                .filter(t -> !t.getId().equals(targetTransaction.getId())).filter(t -> t.isRepaymentLikeType() || t.isChargePayment())
                .anyMatch(t -> {
                    LocalDate tDate = t.getTransactionDate();
                    LocalDate targetDate = targetTransaction.getTransactionDate();
                    return tDate.isAfter(targetDate) || (tDate.isEqual(targetDate) && t.getId() > targetTransaction.getId());
                });
        if (hasSubsequent) {
            throw new GeneralPlatformDomainRuleException("error.msg.standing.instruction.reversal.subsequent.transactions.exist",
                    "Cannot reverse this standing instruction payment: subsequent loan transactions exist after "
                            + targetTransaction.getTransactionDate() + ". Please reverse those transactions first, then retry.");
        }
    }

    private AccountTransferTransaction resolveAccountTransferTransaction(final HistoryRow history) {
        if (history.accountTransferTransactionId() != null) {
            return accountTransferRepository.findById(history.accountTransferTransactionId())
                    .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.standing.instruction.transfer.not.found",
                            "Account transfer transaction " + history.accountTransferTransactionId() + " not found."));
        }
        // Fallback: match by standing_instruction details_id + execution date + amount
        return resolveByFallback(history);
    }

    private AccountTransferTransaction resolveByFallback(final HistoryRow history) {
        // 1) Legacy path: ATT still attached to the SI template details row (rare for job executions).
        List<AccountTransferTransaction> candidates = accountTransferRepository
                .findByDetailsAndDateAndAmount(history.accountTransferDetailsId(), history.executionDate(), history.amount());

        // 2) Normal SI job path: each execution creates a *new* AccountTransferDetails row, so the SI template
        // details id does not match the ATT. Match by savings→loan accounts + date + amount instead.
        if (candidates.isEmpty() && history.fromSavingsAccountId() != null && history.toLoanAccountId() != null) {
            candidates = accountTransferRepository.findByFromSavingsToLoanAndDateAndAmount(history.fromSavingsAccountId(),
                    history.toLoanAccountId(), history.executionDate(), history.amount());
        }

        if (candidates.isEmpty()) {
            throw new GeneralPlatformDomainRuleException("error.msg.standing.instruction.transfer.not.found",
                    "No account transfer transaction found for standing instruction history " + history.id()
                            + ". This execution may predate the reversal feature or has already been reversed.");
        }
        if (candidates.size() > 1) {
            throw new GeneralPlatformDomainRuleException("error.msg.standing.instruction.transfer.ambiguous",
                    "Multiple account transfer transactions match history " + history.id()
                            + ". Cannot determine which to reverse — use the history ID after re-running the job.");
        }
        return candidates.get(0);
    }

    private void updateLocBalanceIfApplicable(final Loan loan, final LoanTransaction repaymentTransaction) {
        if (loanLineOfCreditParamsRepository == null || lineOfCreditBalanceUpdateService == null) {
            return;
        }
        Optional<LoanLineOfCreditParams> locOpt = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
        if (locOpt.isEmpty()) {
            return;
        }
        BigDecimal amount = repaymentTransaction.getPrincipalPortion();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = repaymentTransaction.getAmount();
        }
        try {
            lineOfCreditBalanceUpdateService.computeLocBalance(loan.getId(), repaymentTransaction.getId(), amount,
                    locOpt.get().getLineOfCredit(), repaymentTransaction.getTransactionDate(), LineOfCreditTransactionType.REVERSAL);
        } catch (Exception e) {
            log.warn("LOC balance update failed after standing instruction reversal for loan {}: {}", loan.getId(), e.getMessage());
        }
    }

    private HistoryRow loadHistoryRow(final Long historyId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT h.id, h.standing_instruction_id, h.status, h.amount, DATE(h.execution_time) AS execution_date, "
                            + "h.account_transfer_transaction_id, h.is_reversed, si.account_transfer_details_id, "
                            + "atd.from_savings_account_id, atd.to_loan_account_id "
                            + "FROM m_account_transfer_standing_instructions_history h "
                            + "INNER JOIN m_account_transfer_standing_instructions si ON h.standing_instruction_id = si.id "
                            + "INNER JOIN m_account_transfer_details atd ON atd.id = si.account_transfer_details_id "
                            + "WHERE h.id = ?",
                    (rs, rowNum) -> mapHistoryRow(rs), historyId);
        } catch (EmptyResultDataAccessException e) {
            throw new StandingInstructionHistoryNotFoundException(historyId);
        }
    }

    private HistoryRow mapHistoryRow(final ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        Long standingInstructionId = rs.getLong("standing_instruction_id");
        String status = rs.getString("status");
        BigDecimal amount = rs.getBigDecimal("amount");
        LocalDate executionDate = rs.getDate("execution_date").toLocalDate();
        Long attId = rs.getObject("account_transfer_transaction_id", Long.class);
        boolean isReversed = rs.getBoolean("is_reversed");
        Long detailsId = rs.getLong("account_transfer_details_id");
        Long fromSavingsAccountId = rs.getObject("from_savings_account_id", Long.class);
        Long toLoanAccountId = rs.getObject("to_loan_account_id", Long.class);
        return new HistoryRow(id, standingInstructionId, status, amount, executionDate, attId, isReversed, detailsId, fromSavingsAccountId,
                toLoanAccountId);
    }

    private record HistoryRow(Long id, Long standingInstructionId, String status, BigDecimal amount, LocalDate executionDate,
            Long accountTransferTransactionId, boolean isReversed, Long accountTransferDetailsId, Long fromSavingsAccountId,
            Long toLoanAccountId) {
    }
}
