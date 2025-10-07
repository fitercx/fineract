package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransaction;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
            // We now have to recalculate all subsequent transactions' effect on the summary balance.
            recomputeLocSummaryFromDate(transactionDate, lineOfCredit);
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

            if (!type.isDecrement()) {
                lineOfCredit.getSummary().setConsumedAmount(newConsumedAmount);
            }
            lineOfCredit.getSummary().setAvailableBalance(newAvailableBalance);

            BigDecimal totalIncrement = type.isDisbursement() ? BigDecimal.ONE : BigDecimal.ZERO;

            lineOfCredit.getSummary()
                    .setTotalDrawDownCountDerived(lineOfCredit.getSummary().getTotalDrawDownCountDerived().add(totalIncrement));
        } else if (type.isIncrementTransaction()) {
            // Repayments, refunds, and foreclosures increase available balance and decrease consumed amount
            BigDecimal newAvailableBalance = currentAvailableBalance.add(amount);
            BigDecimal newConsumedAmount = currentConsumedAmount.subtract(amount);

            if (!type.isIncrement()) {
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

        // 1. Calculate the date from which we need to fetch transactions (day before startDate)
        LocalDate fetchFromDate = startDate.minusDays(1);

        List<LineOfCreditTransaction> relevantTransactions = lineOfCreditTransactionRepository
                .findByLineOfCreditIdAndTransactionDateGreaterThanOrEqualTo(lineOfCredit.getId(), fetchFromDate);

        // Split transactions into before startDate and from startDate
        List<LineOfCreditTransaction> transactionsBeforeStartDate = new ArrayList<>();
        List<LineOfCreditTransaction> transactionsFromStartDate = new ArrayList<>();

        for (LineOfCreditTransaction tx : relevantTransactions) {
            if (tx.getTransactionDate().isBefore(startDate)) {
                transactionsBeforeStartDate.add(tx);
            } else {
                transactionsFromStartDate.add(tx);
            }
        }

        // 3. Calculate baseline balance from the last transaction before startDate
        BigDecimal baseAvailableBalance;
        BigDecimal baseTotalDrawDownCount;

        if (transactionsBeforeStartDate.isEmpty()) {
            // No transactions before startDate in our fetched set, use the balance from the last known transaction
            Optional<LineOfCreditTransaction> lastTransactionBeforeStart = lineOfCreditTransactionRepository
                    .findLatestTransaction(lineOfCredit.getId(), PageRequest.of(0, 1)).stream().findFirst();

            if (lastTransactionBeforeStart.isPresent()) {
                baseAvailableBalance = lastTransactionBeforeStart.get().getBalanceAfter();
                // Count disbursements up to this point (this is an approximation, could be optimized with a count
                // query)
                baseTotalDrawDownCount = BigDecimal
                        .valueOf(lineOfCreditTransactionRepository.countByLineOfCreditIdAndTransactionDateLessThanAndTransactionType(
                                lineOfCredit.getId(), startDate, LineOfCreditTransactionType.DISBURSEMENT));
            } else {
                // No transactions before startDate at all, use the original maximum amount
                baseAvailableBalance = lineOfCredit.getMaximumAmount();
                baseTotalDrawDownCount = BigDecimal.ZERO;
            }
        } else {
            // Calculate baseline from the fetched transactions before startDate
            baseAvailableBalance = lineOfCredit.getMaximumAmount();
            baseTotalDrawDownCount = BigDecimal.ZERO;

            Optional<LineOfCreditTransaction> lastTransactionBeforeFetch = lineOfCreditTransactionRepository
                    .findLastTransactionBeforeDate(lineOfCredit.getId(), fetchFromDate, PageRequest.of(0, 1)).stream().findFirst();

            if (lastTransactionBeforeFetch.isPresent()) {
                baseAvailableBalance = lastTransactionBeforeFetch.get().getBalanceAfter();
                // Get the count of disbursements before our fetch date
                baseTotalDrawDownCount = BigDecimal
                        .valueOf(lineOfCreditTransactionRepository.countByLineOfCreditIdAndTransactionDateLessThanAndTransactionType(
                                lineOfCredit.getId(), startDate, LineOfCreditTransactionType.DISBURSEMENT));
            }

            // Apply the transactions we fetched that are before startDate
            for (LineOfCreditTransaction tx : transactionsBeforeStartDate) {
                if (tx.getTransactionType().isDecrementTransaction()) {
                    baseAvailableBalance = baseAvailableBalance.subtract(tx.getAmount());
                    baseTotalDrawDownCount = baseTotalDrawDownCount.add(BigDecimal.ONE);
                } else if (tx.getTransactionType().isIncrementTransaction()) {
                    baseAvailableBalance = baseAvailableBalance.add(tx.getAmount());
                }
            }
        }

        BigDecimal baseConsumedAmount = lineOfCredit.getMaximumAmount().subtract(baseAvailableBalance);

        // 4. Start with the calculated base values for recomputation
        BigDecimal runningAvailableBalance = baseAvailableBalance;
        BigDecimal runningConsumedAmount = baseConsumedAmount;
        BigDecimal totalDrawDownCount = baseTotalDrawDownCount;

        List<LineOfCreditTransaction> transactionsToSave = new ArrayList<>();

        // 5. Iterate and re-apply transactions from startDate forward chronologically
        for (LineOfCreditTransaction tx : transactionsFromStartDate) {
            BigDecimal transactionAmount = tx.getAmount();

            // Record the balance *before* this transaction for the updated record
            BigDecimal balanceBefore = runningAvailableBalance;

            if (tx.isDisbursement()) {
                // Validate availability during re-computation for integrity check
                if (runningAvailableBalance.compareTo(transactionAmount) < 0) {
                    // This indicates an over-disbursement in history, which is a severe data error.
                    throw new IllegalStateException(
                            "LOC history error: Insufficient balance during re-computation for Tx ID: " + tx.getId());
                }
                runningAvailableBalance = runningAvailableBalance.subtract(transactionAmount);
                runningConsumedAmount = runningConsumedAmount.add(transactionAmount);
                totalDrawDownCount = totalDrawDownCount.add(BigDecimal.ONE);
            } else if (tx.getTransactionType().isIncrementTransaction()) {
                runningAvailableBalance = runningAvailableBalance.add(transactionAmount);
                runningConsumedAmount = runningConsumedAmount.subtract(transactionAmount);
            }

            // Update the transaction record itself with the correct calculated balances
            tx.setBalanceBefore(balanceBefore);
            tx.setBalanceAfter(runningAvailableBalance);
            transactionsToSave.add(tx);
        }

        // 6. Update the LOC Summary with the final, correct balances
        lineOfCredit.getSummary().setConsumedAmount(runningConsumedAmount);
        lineOfCredit.getSummary().setAvailableBalance(runningAvailableBalance);
        lineOfCredit.getSummary().setTotalDrawDownCountDerived(totalDrawDownCount);

        // 7. Save all updated transaction records in a batch
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
