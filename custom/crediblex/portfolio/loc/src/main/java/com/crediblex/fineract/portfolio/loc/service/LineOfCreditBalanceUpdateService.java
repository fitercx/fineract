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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LineOfCreditBalanceUpdateService {

    private final LineOfCreditTransactionRepository lineOfCreditTransactionRepository;

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

            LineOfCreditTransaction transaction = createTransactionRecord(lineOfCredit, loanId, amount, currentAvailableBalance,
                    transactionAvailableBalanceAfter, transactionDate, lineOfCreditTransactionType);

            // Set backdated flag for auditing
            transaction.setIsBackdatedEntry(false);

            lineOfCreditTransactionRepository.saveAndFlush(transaction);
        }

        // 5. Trigger Re-computation if Backdated
        if (isBackdatedTransaction) {
            // First, create and save the new backdated transaction with placeholder balances
            // The actual balances will be corrected during recomputation
            LineOfCreditTransaction backdatedTransaction = createTransactionRecord(lineOfCredit, loanId, amount, BigDecimal.ZERO,
                    BigDecimal.ZERO, transactionDate, lineOfCreditTransactionType);
            backdatedTransaction.setIsBackdatedEntry(true);
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
            BigDecimal newConsumedAmount = currentConsumedAmount.add(amount);

            lineOfCredit.getSummary().setConsumedAmount(newConsumedAmount);
            lineOfCredit.getSummary().setAvailableBalance(newAvailableBalance);

            BigDecimal totalIncrement = type.isDisbursement() ? BigDecimal.ONE : BigDecimal.ZERO;

            if (type.isDisbursement()) {
                lineOfCredit.getSummary()
                        .setTotalDrawDownCountDerived(lineOfCredit.getSummary().getTotalDrawDownCountDerived().add(totalIncrement));
            }
        } else if (type.isIncrementTransaction()) {
            // Repayments, refunds, and foreclosures increase available balance and decrease consumed amount
            BigDecimal newAvailableBalance = currentAvailableBalance.add(amount);
            BigDecimal newConsumedAmount = currentConsumedAmount.subtract(amount);

            lineOfCredit.getSummary().setConsumedAmount(newConsumedAmount);
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
    private LineOfCreditTransaction createTransactionRecord(LineOfCredit lineOfCredit, Long loanId, BigDecimal amount,
            BigDecimal balanceBefore, BigDecimal balanceAfter, LocalDate transactionDate, LineOfCreditTransactionType loanTransactionType) {

        final String referenceNumber = "LOAN_" + loanId + "_" + loanTransactionType.name();

        return LineOfCreditTransaction.newTransactionInstance(lineOfCredit, amount, balanceBefore, balanceAfter, transactionDate,
                referenceNumber, loanTransactionType);
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
                    throw new IllegalStateException(
                            "LOC history error: Insufficient balance during re-computation for Tx ID: " + tx.getId());
                }
                runningAvailableBalance = runningAvailableBalance.subtract(transactionAmount);
                runningConsumedAmount = runningConsumedAmount.add(transactionAmount);

                // Only increment drawdown count for actual disbursements, not for other decrement types
                if (tx.getTransactionType().isDisbursement()) {
                    totalDrawDownCount = totalDrawDownCount.add(BigDecimal.ONE);
                }
            } else if (tx.getTransactionType().isIncrementTransaction()) {
                runningAvailableBalance = runningAvailableBalance.add(transactionAmount);
                runningConsumedAmount = runningConsumedAmount.subtract(transactionAmount);
            }

            tx.setBalanceBefore(balanceBefore);
            tx.setBalanceAfter(runningAvailableBalance);
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

}
