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
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
     * Overloaded method that includes loan transaction ID for better traceability and audit purposes.
     *
     * <p>
     * This version should be used when you have access to the specific loan transaction ID (e.g., from
     * LoanTransaction.getId()). The loanTransactionId enables direct linking between LOC transactions and loan
     * transactions, improving audit trails and making it easier to trace LOC balance changes back to specific loan
     * operations.
     * </p>
     *
     * <p>
     * Use this overload when:
     * <ul>
     * <li>Processing loan disbursements, repayments, or other loan-related transactions where the transaction ID is
     * available</li>
     * <li>You need to maintain a direct relationship between LOC transactions and loan transactions for reporting or
     * reconciliation</li>
     * <li>Creating audit trails that require transaction-level traceability</li>
     * </ul>
     * </p>
     *
     * <p>
     * Use the original method (without loanTransactionId) when:
     * <ul>
     * <li>The loan transaction ID is not available or not relevant</li>
     * <li>Processing non-loan transactions (e.g., manual LOC adjustments)</li>
     * <li>Maintaining backward compatibility with existing code</li>
     * </ul>
     * </p>
     *
     * @param loanId
     *            the loan ID
     * @param loanTransactionId
     *            the loan transaction ID for traceability (can be null for non-loan transactions or when not available)
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

            // For increment transactions (repayments), check if reconciliation is needed before updating
            if (lineOfCreditTransactionType.isIncrementTransaction() && !lineOfCreditTransactionType.isBalanceIncrement()) {
                validateAndReconcileIfNeeded(lineOfCredit, amount, loanId);
            }

            updateLocSummaryBalances(lineOfCredit, amount, lineOfCreditTransactionType, loanId, loanTransactionId);

            // Use the actual updated balance from LOC summary (which may have been capped at maximum)
            // instead of calculating from the old balance, to ensure transaction record matches actual state
            BigDecimal actualAvailableBalanceAfter = lineOfCredit.getSummary().getAvailableBalance();

            LineOfCreditTransaction transaction = createTransactionRecord(lineOfCredit, loanId, loanTransactionId, amount,
                    currentAvailableBalance, actualAvailableBalanceAfter, transactionDate, lineOfCreditTransactionType);

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

        // Final validation: Ensure consumed amount is never negative (defensive check)
        if (lineOfCredit.getSummary().getConsumedAmount().compareTo(BigDecimal.ZERO) < 0) {
            log.error(
                    "LOC consumed amount is negative after transaction. LOC ID: {}, Loan ID: {}, Loan Transaction ID: {}, "
                            + "Consumed amount: {}. This should not happen after graceful handling. Attempting reconciliation.",
                    lineOfCredit.getId(), loanId, loanTransactionId, lineOfCredit.getSummary().getConsumedAmount());

            // Attempt reconciliation as a last resort
            try {
                reconcileConsumedAmountFromLoanData(lineOfCredit);
                log.info("Successfully reconciled LOC consumed amount for LOC ID: {}", lineOfCredit.getId());
            } catch (Exception e) {
                log.error("Failed to reconcile LOC consumed amount for LOC ID: {}", lineOfCredit.getId(), e);
                throw new PlatformApiDataValidationException("error.msg.loc.consumed.amount.negative", """
                        Consumed amount cannot be negative.
                        LOC ID: %d, Loan ID: %s, Consumed amount: %s.
                        Reconciliation attempt failed: %s
                        """.formatted(lineOfCredit.getId(), loanId, lineOfCredit.getSummary().getConsumedAmount(), e.getMessage()),
                        "consumedAmount", e, lineOfCredit.getSummary().getConsumedAmount());
            }
        }
    }

    /**
     * Validates and reconciles LOC consumed amount if needed before processing a repayment transaction. This preventive
     * check helps avoid negative consumed amount errors by ensuring data consistency.
     *
     * @param lineOfCredit
     *            The line of credit to validate
     * @param repaymentAmount
     *            The repayment amount being processed
     * @param loanId
     *            The loan ID for context (can be null)
     */
    private void validateAndReconcileIfNeeded(LineOfCredit lineOfCredit, BigDecimal repaymentAmount, Long loanId) {
        BigDecimal currentConsumedAmount = lineOfCredit.getSummary().getConsumedAmount();

        // If repayment amount exceeds current consumed amount, check if reconciliation is needed
        if (repaymentAmount.compareTo(currentConsumedAmount) > 0) {
            log.warn("""
                    Repayment amount ({}) exceeds current LOC consumed amount ({}) for LOC ID: {}, Loan ID: {}.
                    Attempting reconciliation to ensure data consistency.
                    """, repaymentAmount, currentConsumedAmount, lineOfCredit.getId(), loanId);

            try {
                BigDecimal reconciledConsumedAmount = reconcileConsumedAmountFromLoanData(lineOfCredit);
                log.info("Reconciled LOC consumed amount. LOC ID: {}, Loan ID: {}, Old consumed: {}, New consumed: {}",
                        lineOfCredit.getId(), loanId, currentConsumedAmount, reconciledConsumedAmount);

                // After reconciliation, check again if repayment would still cause negative balance
                if (repaymentAmount.compareTo(reconciledConsumedAmount) > 0) {
                    log.warn("""
                            After reconciliation, repayment amount ({}) still exceeds LOC consumed amount ({})
                            for LOC ID: {}, Loan ID: {}. This will be handled gracefully by capping at zero.
                            """, repaymentAmount, reconciledConsumedAmount, lineOfCredit.getId(), loanId);
                }
            } catch (Exception e) {
                log.error("""
                        Failed to reconcile LOC consumed amount before repayment.
                        LOC ID: {}, Loan ID: {}. Will proceed with graceful handling.
                        """, lineOfCredit.getId(), loanId, e);
                // Don't throw - let the graceful handling in updateLocSummaryBalances deal with it
            }
        }
    }

    /**
     * Updates LOC summary balances based on transaction type.
     *
     * <p>
     * This method includes preventive validation and graceful handling to prevent negative consumed amounts. If a
     * repayment would cause consumed amount to go negative, it will: 1. Log a warning with detailed context 2. Cap the
     * consumed amount at zero (graceful degradation) 3. Adjust the available balance accordingly to maintain data
     * consistency
     * </p>
     *
     * @param lineOfCredit
     *            The line of credit to update
     * @param amount
     *            The transaction amount
     * @param type
     *            The transaction type
     * @param loanId
     *            The loan ID (for logging context, can be null)
     * @param loanTransactionId
     *            The loan transaction ID (for logging context, can be null)
     */
    private void updateLocSummaryBalances(LineOfCredit lineOfCredit, BigDecimal amount, LineOfCreditTransactionType type, Long loanId,
            Long loanTransactionId) {
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
            BigDecimal repaymentAmount = amount;
            // Decrease consumed amount to maintain constraint: available_balance + consumed_amount = maximum_amount
            // This applies to: repayments, reversals, refunds, undo disbursements, write-offs, and foreclosures
            // Note: For INCREMENT (balance increment), consumed_amount should not change as it represents a limit
            // increase
            if (!type.isBalanceIncrement()) {
                // PREVENTIVE VALIDATION: Check if repayment amount exceeds current consumed amount
                if (repaymentAmount.compareTo(currentConsumedAmount) > 0) {
                    BigDecimal excessAmount = repaymentAmount.subtract(currentConsumedAmount);
                    log.warn("""
                            LOC consumed amount would go negative.
                            LOC ID: {}, Loan ID: {}, Loan Transaction ID: {},
                            Current consumed amount: {}, Repayment amount: {}, Excess amount: {}, Transaction type: {}.
                            Capping consumed amount at zero. Excess repayment amount ({}) cannot be applied to available balance
                            as it would exceed maximum LOC limit. This may indicate a data inconsistency.
                            """, lineOfCredit.getId(), loanId, loanTransactionId, currentConsumedAmount, repaymentAmount, excessAmount,
                            type, excessAmount);

                    // Graceful handling: Cap consumed amount at zero
                    // Set available balance using constraint:
                    //   available = effectiveDrawableLimit - consumed = (creditLimit - blockedAmount) - 0
                    // This ensures we don't lose the excess amount silently - it's logged as a warning
                    lineOfCredit.getSummary().setConsumedAmount(BigDecimal.ZERO);
                    BigDecimal newAvailableBalance = lineOfCredit.getEffectiveDrawableLimit();
                    lineOfCredit.getSummary().setAvailableBalance(newAvailableBalance);
                } else {
                    // Normal case: consumed amount can be reduced without going negative
                    BigDecimal newConsumedAmount = currentConsumedAmount.subtract(repaymentAmount);
                    lineOfCredit.getSummary().setConsumedAmount(newConsumedAmount);
                    // Increase available balance by the repayment amount
                    BigDecimal newAvailableBalance = currentAvailableBalance.add(repaymentAmount);
                    lineOfCredit.getSummary().setAvailableBalance(newAvailableBalance);
                }
            } else {
                // For balance increment (limit increase), only increase available balance
                BigDecimal newAvailableBalance = currentAvailableBalance.add(amount);
                lineOfCredit.getSummary().setAvailableBalance(newAvailableBalance);
            }
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
            // No history before startDate: begin from effective drawable limit (creditLimit - blockedAmount)
            baseAvailableBalance = lineOfCredit.getEffectiveDrawableLimit();
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

                // Decrease consumed amount to maintain constraint: available_balance + consumed_amount = maximum_amount
                // This applies to: repayments, reversals, refunds, undo disbursements, write-offs, and foreclosures
                // Note: For INCREMENT (balance increment), consumed_amount should not change as it represents a limit
                // increase
                if (!tx.getTransactionType().isBalanceIncrement()) {
                    runningConsumedAmount = runningConsumedAmount.subtract(transactionAmount);
                    // Ensure consumed amount doesn't go negative - graceful handling during recomputation
                    if (runningConsumedAmount.compareTo(BigDecimal.ZERO) < 0) {
                        log.warn(
                                "LOC consumed amount would go negative during recomputation. LOC ID: {}, Tx ID: {}, "
                                        + "Current consumed: {}, Transaction amount: {}, Transaction type: {}. "
                                        + "Capping consumed amount at zero.",
                                lineOfCredit.getId(), tx.getId(), runningConsumedAmount.add(transactionAmount), transactionAmount,
                                tx.getTransactionType());
                        // Cap at zero to maintain data integrity
                        runningConsumedAmount = BigDecimal.ZERO;
                        // Adjust available balance to maintain constraint:
                        //   available_balance + consumed_amount = effectiveDrawableLimit (creditLimit - blockedAmount)
                        runningAvailableBalance = lineOfCredit.getEffectiveDrawableLimit();
                    }
                }
            }

            // Calculate consumed amount before this transaction
            BigDecimal consumedBefore = runningConsumedAmount;
            if (tx.getTransactionType().isDecrementTransaction() && !tx.getTransactionType().isBalanceDecrement()) {
                // Before disbursement, consumed amount was less
                consumedBefore = runningConsumedAmount.subtract(tx.getAmount());
            } else if (tx.getTransactionType().isIncrementTransaction() && !tx.getTransactionType().isBalanceIncrement()) {
                // Before increment transaction (repayment, reversal, refund, undo, write-off, foreclosure),
                // consumed amount was more
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
                throw new PlatformApiDataValidationException("error.msg.loc.balance.inconsistency", """
                        Balance inconsistency detected for LOC ID %d.
                        Current system balance: %s, Last transaction balance: %s
                        """.formatted(locId, currentSystemBalance, lastTransactionBalanceAfter), "availableBalance", currentSystemBalance,
                        lastTransactionBalanceAfter);
            }
        }
        // If no transactions exist, no validation is needed as this would be the first transaction
    }

    /**
     * Reconciles consumed_amount by recalculating it from actual loan data. This method ensures consumed_amount equals
     * the sum of principal_disbursed_derived for all loans under the LOC.
     *
     * <p>
     * This is useful for:
     * <ul>
     * <li>Fixing data inconsistencies between LOC summary and actual loan disbursements</li>
     * <li>Validating consumed_amount accuracy during audits</li>
     * <li>Recovering from data corruption or migration issues</li>
     * </ul>
     * </p>
     *
     * <p>
     * <b>Note:</b> This method directly queries and updates financial data. Comprehensive unit tests are required
     * covering scenarios such as:
     * <ul>
     * <li>LOC with zero loans (should return 0)</li>
     * <li>LOC with multiple loans (should sum all principal_disbursed_derived)</li>
     * <li>Null handling for lineOfCredit parameter</li>
     * <li>Edge cases with loans having null or zero principal_disbursed_derived</li>
     * <li>Verification that available balance is correctly recalculated</li>
     * </ul>
     * </p>
     *
     * @param lineOfCredit
     *            The line of credit to reconcile (must not be null)
     * @return The recalculated consumed amount based on actual loan data
     * @throws IllegalArgumentException
     *             if lineOfCredit is null
     */
    @Transactional
    public BigDecimal reconcileConsumedAmountFromLoanData(LineOfCredit lineOfCredit) {
        if (lineOfCredit == null) {
            throw new IllegalArgumentException("LineOfCredit cannot be null");
        }
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
        // Available Amount = Credit Limit - Blocked Amount - Consumed Amount
        BigDecimal oldConsumedAmount = lineOfCredit.getSummary().getConsumedAmount();
        lineOfCredit.getSummary().setConsumedAmount(actualConsumedAmount);
        lineOfCredit.getSummary().setAvailableBalance(lineOfCredit.getEffectiveDrawableLimit().subtract(actualConsumedAmount));

        // Log reconciliation if there was a difference
        if (oldConsumedAmount.compareTo(actualConsumedAmount) != 0) {
            // Optionally create a reconciliation transaction record for audit
            // For now, we just update the summary
        }

        return actualConsumedAmount;
    }

}
