package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransaction;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LineOfCreditBalanceUpdateService {

    private final LineOfCreditTransactionRepository lineOfCreditTransactionRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Computes and updates Line of Credit balance upon loan transactions (disbursement/repayment/refund/foreclosure).
     * This method: 1. Automatically adjusts the available LOC balance upon transactions 2. Updates the LOC consumed
     * amount accordingly 3. Creates and persists a LOC transaction history record with transaction metadata 4. Handles
     * backdated transactions by triggering recomputation of subsequent balances
     *
     * @param loanId
     *            the loan ID
     * @param amount
     *            the transaction amount (should be the approved_principal from the loan for disbursements)
     * @param lineOfCredit
     *            the line of credit parameters containing LOC details
     * @param transactionDate
     *            the transaction date
     * @param lineOfCreditTransactionType
     *            the type of transaction (disbursement, repayment, refund, etc.)
     */
    @Transactional
    public void computeLocBalance(Long loanId, BigDecimal amount, LineOfCredit lineOfCredit, LocalDate transactionDate,
            LineOfCreditTransactionType lineOfCreditTransactionType) {
        computeLocBalance(loanId, null, amount, lineOfCredit, transactionDate, lineOfCreditTransactionType);
    }

    /**
     * Overloaded method that includes loan transaction ID for better traceability.
     *
     * @param loanId
     *            the loan ID
     * @param loanTransactionId
     *            the loan transaction ID (can be null for non-loan transactions)
     * @param amount
     *            the transaction amount
     * @param lineOfCredit
     *            the line of credit
     * @param transactionDate
     *            the transaction date
     * @param lineOfCreditTransactionType
     *            the type of transaction
     */
    @Transactional
    public void computeLocBalance(Long loanId, Long loanTransactionId, BigDecimal amount, LineOfCredit lineOfCredit,
            LocalDate transactionDate, LineOfCreditTransactionType lineOfCreditTransactionType) {

        if (lineOfCredit == null) {
            return;
        }

        BigDecimal currentAvailableBalance = lineOfCredit.getSummary().getAvailableBalance();

        // Fetch the latest transaction once and reuse it throughout the method
        Optional<LineOfCreditTransaction> latestTransaction = lineOfCreditTransactionRepository
                .findLatestTransaction(lineOfCredit.getId(), PageRequest.of(0, 1)).stream().findFirst();

        LocalDate latestTransactionDate = latestTransaction.map(LineOfCreditTransaction::getTransactionDate).orElse(LocalDate.MIN);

        boolean isBackdatedTransaction = transactionDate.isBefore(latestTransactionDate);

        // 1. Check for insufficient balance (only relevant for real-time disbursements)
        if (lineOfCreditTransactionType.isDecrementTransaction() && currentAvailableBalance.compareTo(amount) < 0
                && !isBackdatedTransaction) {

            throw new PlatformApiDataValidationException("error.msg.loc.insufficient.balance",
                    "Insufficient line of credit balance for disbursement", "disbursementAmount", amount);
        }

        // We take the happy path first when there is no backdated transaction.
        if (!isBackdatedTransaction) {
            // Validate balance consistency before processing using the already fetched latest transaction
            validateBalanceConsistency(lineOfCredit.getId(), currentAvailableBalance, latestTransaction);

            updateLocSummaryBalances(lineOfCredit, amount, lineOfCreditTransactionType);

            BigDecimal transactionAvailableBalanceAfter = calculateTransactionBalanceAfter(currentAvailableBalance, amount,
                    lineOfCreditTransactionType);

            LineOfCreditTransaction transaction = createTransactionRecord(lineOfCredit, loanId, loanTransactionId, amount,
                    currentAvailableBalance, transactionAvailableBalanceAfter, transactionDate, lineOfCreditTransactionType);

            // Set backdated flag for auditing
            transaction.setIsBackdatedEntry(false);

            lineOfCreditTransactionRepository.saveAndFlush(transaction);
        }

        // 5. Trigger Re-computation if Backdated
        if (isBackdatedTransaction) {
            // First, create and save the new backdated transaction with placeholder balances
            // The actual balances will be corrected during recomputation
            LineOfCreditTransaction backdatedTransaction = createTransactionRecord(lineOfCredit, loanId, loanTransactionId, amount,
                    BigDecimal.ZERO, BigDecimal.ZERO, transactionDate, lineOfCreditTransactionType);
            backdatedTransaction.setIsBackdatedEntry(true);
            // Reset consumed amount fields as they will be recalculated during recomputation
            backdatedTransaction.setConsumedAmountBefore(null);
            backdatedTransaction.setConsumedAmountAfter(null);
            lineOfCreditTransactionRepository.saveAndFlush(backdatedTransaction);

            // We now have to recalculate all transactions from the backdated date onwards
            recomputeLocSummaryFromDate(transactionDate, lineOfCredit);
        }

        if (lineOfCredit.getSummary().getConsumedAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new PlatformApiDataValidationException("error.msg.loc.consumed.amount.negative", "Consumed amount cannot be negative",
                    "consumedAmount", lineOfCredit.getSummary().getConsumedAmount());
        }
    }

    /**
     * Updates LOC summary balances based on transaction type
     */
    private void updateLocSummaryBalances(LineOfCredit lineOfCredit, BigDecimal amount, LineOfCreditTransactionType type) {
        BigDecimal currentAvailableBalance = lineOfCredit.getSummary().getAvailableBalance();
        BigDecimal currentConsumedAmount = lineOfCredit.getSummary().getConsumedAmount();

        if (type.isDecrementTransaction()) {
            // Disbursement reduces available balance and increases consumed amount
            BigDecimal newAvailableBalance = currentAvailableBalance.subtract(amount);
            if (!type.isBalanceDecrement()) {
                BigDecimal newConsumedAmount = currentConsumedAmount.add(amount);
                lineOfCredit.getSummary().setConsumedAmount(newConsumedAmount);
            }

            lineOfCredit.getSummary().setAvailableBalance(newAvailableBalance);

            BigDecimal totalIncrement = type.isDisbursement() ? BigDecimal.ONE : BigDecimal.ZERO;

            if (type.isDisbursement()) {
                lineOfCredit.getSummary()
                        .setTotalDrawDownCountDerived(lineOfCredit.getSummary().getTotalDrawDownCountDerived().add(totalIncrement));
            }
        } else if (type.isIncrementTransaction()) {
            // Repayments, refunds, and foreclosures increase available balance
            BigDecimal newAvailableBalance = currentAvailableBalance.add(amount);

            // Only decrease consumed amount for reversals, refunds, undo disbursements, and write-offs
            // Regular repayments should NOT reduce consumed amount (consumed = sum of all disbursed amounts)
            if (!type.isBalanceIncrement() && (type.isReversal() || type.isRefund() || type.isUndoDisbursement() || type.isWriteOff())) {
                BigDecimal newConsumedAmount = currentConsumedAmount.subtract(amount);
                lineOfCredit.getSummary().setConsumedAmount(newConsumedAmount);
            }

            lineOfCredit.getSummary().setAvailableBalance(newAvailableBalance);
        }
    }

    /**
     * Calculates the balance after transaction for recording purposes
     */
    private BigDecimal calculateTransactionBalanceAfter(BigDecimal currentAvailableBalance, BigDecimal amount,
            LineOfCreditTransactionType loanTransactionType) {
        if (loanTransactionType.isDecrementTransaction()) {
            return currentAvailableBalance.subtract(amount);

        } else if (loanTransactionType.isIncrementTransaction()) {
            return currentAvailableBalance.add(amount);
        } else {
            return currentAvailableBalance;
        }
    }

    /**
     * Creates the appropriate transaction record based on transaction type
     */
    private LineOfCreditTransaction createTransactionRecord(LineOfCredit lineOfCredit, Long loanId, Long loanTransactionId,
            BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter, LocalDate transactionDate,
            LineOfCreditTransactionType loanTransactionType) {

        // Generate reference number based on transaction type
        // For loan-related transactions, include loan ID; for LOC operations (INCREMENT/DECREMENT), use LOC ID
        final String referenceNumber;
        if (loanId != null && (loanTransactionType.isDisbursement() || loanTransactionType.isRepayment() || loanTransactionType.isRefund()
                || loanTransactionType.isReversal() || loanTransactionType.isUndoDisbursement() || loanTransactionType.isForeclosure()
                || loanTransactionType.isWriteOff())) {
            referenceNumber = "LOAN_" + loanId + "_" + loanTransactionType.name();
        } else {
            // For INCREMENT/DECREMENT or other non-loan transactions
            referenceNumber = "LOC_" + lineOfCredit.getId() + "_" + loanTransactionType.name();
        }

        BigDecimal consumedAmountBefore = lineOfCredit.getSummary().getConsumedAmount();
        BigDecimal consumedAmountAfter = calculateConsumedAmountAfter(lineOfCredit, amount, loanTransactionType, consumedAmountBefore);

        LineOfCreditTransaction transaction = LineOfCreditTransaction.newTransactionInstance(lineOfCredit, amount, balanceBefore,
                balanceAfter, transactionDate, referenceNumber, loanTransactionType);

        // Set additional audit fields
        // Only set loanId and loanTransactionId for loan-related transactions (null for INCREMENT/DECREMENT)
        transaction.setLoanId(loanId);
        transaction.setLoanTransactionId(loanTransactionId);
        transaction.setConsumedAmountBefore(consumedAmountBefore);
        transaction.setConsumedAmountAfter(consumedAmountAfter);

        return transaction;
    }

    /**
     * Calculates consumed amount after transaction based on transaction type
     */
    private BigDecimal calculateConsumedAmountAfter(LineOfCredit lineOfCredit, BigDecimal amount, LineOfCreditTransactionType type,
            BigDecimal currentConsumedAmount) {
        if (type.isDecrementTransaction() && !type.isBalanceDecrement()) {
            // Disbursements increase consumed amount
            return currentConsumedAmount.add(amount);
        } else if (type.isIncrementTransaction() && !type.isBalanceIncrement()
                && (type.isReversal() || type.isRefund() || type.isUndoDisbursement() || type.isWriteOff())) {
            // Only reversals, refunds, undo disbursements, and write-offs decrease consumed amount
            return currentConsumedAmount.subtract(amount);
        }
        // Repayments and other increment transactions don't change consumed amount
        return currentConsumedAmount;
    }

    /**
     * Fetches transactions for the LOC from the given date forward, and re-applies their effects chronologically to
     * correct the LOC Summary. This method is optimized to fetch only transactions from the day before startDate
     * onwards.
     */
    private void recomputeLocSummaryFromDate(LocalDate startDate, LineOfCredit lineOfCredit) {

        // 1. Find the single last transaction strictly before startDate (baseline)
        Optional<LineOfCreditTransaction> lastTransactionBeforeStartOpt = lineOfCreditTransactionRepository
                .findLastTransactionBeforeDate(lineOfCredit.getId(), startDate, PageRequest.of(0, 1)).stream().findFirst();

        // 2. Fetch all transactions from startDate onward (these are the ones we will re-apply)
        List<LineOfCreditTransaction> transactionsFromStartDate = lineOfCreditTransactionRepository
                .findByLineOfCreditIdAndTransactionDateGreaterThanOrEqualTo(lineOfCredit.getId(), startDate);

        // Ensure chronological order (oldest -> newest). If transactionDate can be same for multiple txs,
        // add a tiebreaker (e.g., id) for deterministic application.
        transactionsFromStartDate
                .sort(Comparator.comparing(LineOfCreditTransaction::getTransactionDate).thenComparing(LineOfCreditTransaction::getId));

        // 3. Derive baseline (available balance and drawdown count) as of startDate
        BigDecimal baseAvailableBalance;
        BigDecimal baseTotalDrawDownCount;

        if (lastTransactionBeforeStartOpt.isPresent()) {
            LineOfCreditTransaction lastBefore = lastTransactionBeforeStartOpt.get();
            baseAvailableBalance = lastBefore.getBalanceAfter();
            baseTotalDrawDownCount = BigDecimal
                    .valueOf(lineOfCreditTransactionRepository.countByLineOfCreditIdAndTransactionDateLessThanAndTransactionType(
                            lineOfCredit.getId(), startDate, LineOfCreditTransactionType.DISBURSEMENT));
        } else {
            // No history before startDate: begin from maximum amount
            baseAvailableBalance = lineOfCredit.getMaximumAmount();
            baseTotalDrawDownCount = BigDecimal.ZERO;
        }

        BigDecimal baseConsumedAmount = lineOfCredit.getMaximumAmount().subtract(baseAvailableBalance);

        // 4. Start running values from the baseline
        BigDecimal runningAvailableBalance = baseAvailableBalance;
        BigDecimal runningConsumedAmount = baseConsumedAmount;
        BigDecimal totalDrawDownCount = baseTotalDrawDownCount;

        List<LineOfCreditTransaction> transactionsToSave = new ArrayList<>();

        // 5. Re-apply only transactions that are >= startDate (no overlap with baseline)
        for (LineOfCreditTransaction tx : transactionsFromStartDate) {
            BigDecimal transactionAmount = tx.getAmount();
            BigDecimal balanceBefore = runningAvailableBalance;

            if (tx.getTransactionType().isDecrementTransaction()) {
                if (runningAvailableBalance.compareTo(transactionAmount) < 0) {

                    throw new PlatformApiDataValidationException("error.msg.loc.insufficient.balance",
                            "LOC transaction history error: Insufficient balance during re-computation for Tx ID:" + tx.getId(), List.of());

                }
                runningAvailableBalance = runningAvailableBalance.subtract(transactionAmount);
                if (!tx.getTransactionType().isBalanceDecrement()) {
                    runningConsumedAmount = runningConsumedAmount.add(transactionAmount);
                }

                // Only increment drawdown count for actual disbursements, not for other decrement types
                if (tx.getTransactionType().isDisbursement()) {
                    totalDrawDownCount = totalDrawDownCount.add(BigDecimal.ONE);
                }
            } else if (tx.getTransactionType().isIncrementTransaction()) {
                runningAvailableBalance = runningAvailableBalance.add(transactionAmount);

                // Only decrease consumed amount for reversals, refunds, undo disbursements, and write-offs
                // Regular repayments should NOT reduce consumed amount (consumed = sum of all disbursed amounts)
                if (!tx.getTransactionType().isBalanceIncrement()
                        && (tx.getTransactionType().isReversal() || tx.getTransactionType().isRefund()
                                || tx.getTransactionType().isUndoDisbursement() || tx.getTransactionType().isWriteOff())) {
                    runningConsumedAmount = runningConsumedAmount.subtract(transactionAmount);
                }
            }

            // Calculate consumed amount before this transaction
            BigDecimal consumedBefore = runningConsumedAmount;
            if (tx.getTransactionType().isDecrementTransaction() && !tx.getTransactionType().isBalanceDecrement()) {
                // Before disbursement, consumed amount was less
                consumedBefore = runningConsumedAmount.subtract(tx.getAmount());
            } else if (tx.getTransactionType().isIncrementTransaction() && !tx.getTransactionType().isBalanceIncrement()
                    && (tx.getTransactionType().isReversal() || tx.getTransactionType().isRefund()
                            || tx.getTransactionType().isUndoDisbursement() || tx.getTransactionType().isWriteOff())) {
                // Before reversal/refund/undo, consumed amount was more
                consumedBefore = runningConsumedAmount.add(tx.getAmount());
            }

            // Extract loanId from reference_number for existing transactions that don't have it set
            if (tx.getLoanId() == null && tx.getReferenceNumber() != null && tx.getReferenceNumber().startsWith("LOAN_")) {
                try {
                    // Extract loan ID from reference_number format: "LOAN_{loanId}_{transactionType}"
                    String[] parts = tx.getReferenceNumber().split("_");
                    if (parts.length >= 2) {
                        Long extractedLoanId = Long.parseLong(parts[1]);
                        tx.setLoanId(extractedLoanId);
                    }
                } catch (NumberFormatException e) {
                    // If parsing fails, leave loanId as null (might be old format or non-loan transaction)
                }
            }

            tx.setBalanceBefore(balanceBefore);
            tx.setBalanceAfter(runningAvailableBalance);
            tx.setConsumedAmountBefore(consumedBefore);
            tx.setConsumedAmountAfter(runningConsumedAmount);

            transactionsToSave.add(tx);
        }

        // 6. Update LOC summary
        lineOfCredit.getSummary().setConsumedAmount(runningConsumedAmount);
        lineOfCredit.getSummary().setAvailableBalance(runningAvailableBalance);
        lineOfCredit.getSummary().setTotalDrawDownCountDerived(totalDrawDownCount);

        // 7. Save updated transactions (those from startDate onward)
        lineOfCreditTransactionRepository.saveAll(transactionsToSave);
    }

    /**
     * Validates that the current available balance in the system matches the expected balance based on the last
     * transaction record. This helps catch data integrity issues.
     */
    private void validateBalanceConsistency(Long locId, BigDecimal currentSystemBalance,
            Optional<LineOfCreditTransaction> latestTransaction) {
        if (latestTransaction.isPresent()) {
            BigDecimal lastTransactionBalanceAfter = latestTransaction.get().getBalanceAfter();

            // Compare the balances - they should match exactly
            if (currentSystemBalance.compareTo(lastTransactionBalanceAfter) != 0) {
                throw new PlatformApiDataValidationException("error.msg.loc.balance.inconsistency",
                        String.format(
                                "Balance inconsistency detected for LOC ID %d. "
                                        + "Current system balance: %s, Last transaction balance: %s",
                                locId, currentSystemBalance, lastTransactionBalanceAfter),
                        "availableBalance", currentSystemBalance, lastTransactionBalanceAfter);
            }
        }
        // If no transactions exist, no validation is needed as this would be the first transaction
    }

    /**
     * Reconciles consumed_amount by recalculating it from actual loan data. This method ensures consumed_amount equals
     * the sum of principal_disbursed_derived for all loans under the LOC.
     *
     * This is useful for: - Fixing data inconsistencies - Validating consumed_amount accuracy - Recovering from data
     * corruption
     *
     * @param lineOfCredit
     *            The line of credit to reconcile
     * @return The recalculated consumed amount based on actual loan data
     */
    @Transactional
    public BigDecimal reconcileConsumedAmountFromLoanData(LineOfCredit lineOfCredit) {
        // Query to get sum of principal_disbursed_derived for all loans under this LOC
        String sql = """
                SELECT COALESCE(SUM(l.principal_disbursed_derived), 0)
                FROM m_loan l
                INNER JOIN m_loan_line_of_credit_params mlcp ON mlcp.loan_id = l.id
                WHERE mlcp.line_of_credit_id = ?
                AND l.principal_disbursed_derived IS NOT NULL
                AND l.principal_disbursed_derived > 0
                """;

        BigDecimal actualConsumedAmount = jdbcTemplate.queryForObject(sql, BigDecimal.class, lineOfCredit.getId());
        if (actualConsumedAmount == null) {
            actualConsumedAmount = BigDecimal.ZERO;
        }

        // Update LOC summary
        BigDecimal oldConsumedAmount = lineOfCredit.getSummary().getConsumedAmount();
        lineOfCredit.getSummary().setConsumedAmount(actualConsumedAmount);
        lineOfCredit.getSummary().setAvailableBalance(lineOfCredit.getMaximumAmount().subtract(actualConsumedAmount));

        // Log reconciliation if there was a difference
        if (oldConsumedAmount.compareTo(actualConsumedAmount) != 0) {
            // Optionally create a reconciliation transaction record for audit
            // For now, we just update the summary
        }

        return actualConsumedAmount;
    }

}
