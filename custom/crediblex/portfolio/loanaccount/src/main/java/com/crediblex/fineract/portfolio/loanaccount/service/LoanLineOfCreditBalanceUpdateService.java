package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransaction;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanLineOfCreditBalanceUpdateService {

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
     * @param lineOfCreditParamsOptional
     *            the line of credit parameters containing LOC details
     * @param transactionDate
     *            the transaction date
     * @param loanTransactionType
     *            the type of transaction (disbursement, repayment, refund, etc.)
     */
    public void computeLocBalance(Long loanId, BigDecimal amount, Optional<LoanLineOfCreditParams> lineOfCreditParamsOptional,
            LocalDate transactionDate, LoanTransactionType loanTransactionType) {

        if (lineOfCreditParamsOptional.isEmpty()) {
            return;
        }

        LoanLineOfCreditParams lineOfCreditParams = lineOfCreditParamsOptional.get();

        Long locId = lineOfCreditParams.getLineOfCredit().getId();
        BigDecimal currentAvailableBalance = lineOfCreditParams.getLineOfCredit().getSummary().getAvailableBalance();

        // Fetch the latest transaction once and reuse it throughout the method
        Optional<LineOfCreditTransaction> latestTransaction = lineOfCreditTransactionRepository
                .findTopByLineOfCreditIdOrderByTransactionDateDesc(locId);

        LocalDate latestTransactionDate = latestTransaction.map(LineOfCreditTransaction::getTransactionDate).orElse(LocalDate.MIN);

        boolean isBackdatedTransaction = transactionDate.isBefore(latestTransactionDate);

        // 1. Check for insufficient balance (only relevant for real-time disbursements)
        if (loanTransactionType.isDisbursement() && currentAvailableBalance.compareTo(amount) < 0 && !isBackdatedTransaction) {

            throw new PlatformApiDataValidationException("error.msg.loc.insufficient.balance",
                    "Insufficient line of credit balance for disbursement", "disbursementAmount", amount);
        }

        // We take the happy path first when there is no backdated transaction.
        if (!isBackdatedTransaction) {
            // Validate balance consistency before processing using the already fetched latest transaction
            validateBalanceConsistency(locId, currentAvailableBalance, latestTransaction);

            updateLocSummaryBalances(lineOfCreditParams, amount, loanTransactionType);

            // 4. Create and Save the Transaction Record
            BigDecimal transactionAvailableBalanceAfter = calculateTransactionBalanceAfter(lineOfCreditParams, currentAvailableBalance,
                    amount, loanTransactionType, isBackdatedTransaction);

            LineOfCreditTransaction transaction = createTransactionRecord(lineOfCreditParams.getLineOfCredit(), loanId, amount,
                    currentAvailableBalance, transactionAvailableBalanceAfter, transactionDate, loanTransactionType);

            // Set backdated flag for auditing
            transaction.setIsBackdatedEntry(false);

            lineOfCreditTransactionRepository.saveAndFlush(transaction);
        }

        // 5. Trigger Re-computation if Backdated
        if (isBackdatedTransaction) {
            // We now have to recalculate all subsequent transactions' effect on the summary balance.
            recomputeLocSummaryFromDate(locId, transactionDate, lineOfCreditParams);
        }
    }

    /**
     * Updates LOC summary balances based on transaction type
     */
    private void updateLocSummaryBalances(LoanLineOfCreditParams lineOfCreditParams, BigDecimal amount,
            LoanTransactionType loanTransactionType) {
        BigDecimal currentAvailableBalance = lineOfCreditParams.getLineOfCredit().getSummary().getAvailableBalance();
        BigDecimal currentConsumedAmount = lineOfCreditParams.getLineOfCredit().getSummary().getConsumedAmount();

        if (loanTransactionType.isDisbursement()) {
            // Disbursement reduces available balance and increases consumed amount
            BigDecimal newAvailableBalance = currentAvailableBalance.subtract(amount);
            BigDecimal newConsumedAmount = currentConsumedAmount.add(amount);

            lineOfCreditParams.getLineOfCredit().getSummary().setConsumedAmount(newConsumedAmount);
            lineOfCreditParams.getLineOfCredit().getSummary().setAvailableBalance(newAvailableBalance);
            lineOfCreditParams.getLineOfCredit().getSummary().setTotalDrawDownCountDerived(
                    lineOfCreditParams.getLineOfCredit().getSummary().getTotalDrawDownCountDerived().add(BigDecimal.ONE));
        } else if (isBalanceIncreasingTransaction(loanTransactionType)) {
            // Repayments, refunds, and foreclosures increase available balance and decrease consumed amount
            BigDecimal newAvailableBalance = currentAvailableBalance.add(amount);
            BigDecimal newConsumedAmount = currentConsumedAmount.subtract(amount);

            lineOfCreditParams.getLineOfCredit().getSummary().setConsumedAmount(newConsumedAmount);
            lineOfCreditParams.getLineOfCredit().getSummary().setAvailableBalance(newAvailableBalance);
        }
    }

    /**
     * Calculates the balance after transaction for recording purposes
     */
    private BigDecimal calculateTransactionBalanceAfter(LoanLineOfCreditParams lineOfCreditParams, BigDecimal currentAvailableBalance,
            BigDecimal amount, LoanTransactionType loanTransactionType, boolean isBackdatedTransaction) {
        if (loanTransactionType.isDisbursement()) {
            return isBackdatedTransaction ? currentAvailableBalance.subtract(amount) // Temporary value for backdated
                                                                                     // record
                    : lineOfCreditParams.getLineOfCredit().getSummary().getAvailableBalance(); // Final value for
                                                                                               // standard record
        } else if (isBalanceIncreasingTransaction(loanTransactionType)) {
            return isBackdatedTransaction ? currentAvailableBalance.add(amount) // Temporary value for backdated record
                    : lineOfCreditParams.getLineOfCredit().getSummary().getAvailableBalance(); // Final value for
                                                                                               // standard record
        } else {
            // For unsupported transaction types, return current balance
            return currentAvailableBalance;
        }
    }

    /**
     * Creates the appropriate transaction record based on transaction type
     */
    private LineOfCreditTransaction createTransactionRecord(LineOfCredit lineOfCredit, Long loanId, BigDecimal amount,
            BigDecimal balanceBefore, BigDecimal balanceAfter, LocalDate transactionDate, LoanTransactionType loanTransactionType) {
        String referenceNumber = "LOAN_" + loanId + "_" + loanTransactionType.name();

        return switch (loanTransactionType.getValue()) {
            case 1 -> // DISBURSEMENT
                LineOfCreditTransaction.disbursement(lineOfCredit, amount, balanceBefore, balanceAfter, transactionDate, referenceNumber);
            case 2 -> // REPAYMENT
                LineOfCreditTransaction.repayment(lineOfCredit, amount, balanceBefore, balanceAfter, transactionDate, referenceNumber);
            case 4 -> // REFUND
                LineOfCreditTransaction.refund(lineOfCredit, amount, balanceBefore, balanceAfter, transactionDate, referenceNumber); // WAIVE_INTEREST
                                                                                                                                     // (treating
                                                                                                                                     // as
                                                                                                                                     // foreclosure
                                                                                                                                     // for
                                                                                                                                     // LOC
                                                                                                                                     // purposes)
            case 5, 6 -> // WRITEOFF (treating as foreclosure for LOC purposes)
                LineOfCreditTransaction.foreclosure(lineOfCredit, amount, balanceBefore, balanceAfter, transactionDate, referenceNumber);
            default ->
                // For any other transaction type, create a generic repayment-style transaction
                LineOfCreditTransaction.repayment(lineOfCredit, amount, balanceBefore, balanceAfter, transactionDate, referenceNumber);
        };
    }

    /**
     * Helper method to check if a transaction type increases the LOC balance
     */
    private boolean isBalanceIncreasingTransaction(LoanTransactionType loanTransactionType) {
        return loanTransactionType.isRepayment() || loanTransactionType.getValue().equals(4) // REFUND
                || loanTransactionType.getValue().equals(5) // WAIVE_INTEREST
                || loanTransactionType.getValue().equals(6); // WRITEOFF
    }

    /**
     * Fetches transactions for the LOC from the given date forward, and re-applies their effects chronologically to
     * correct the LOC Summary. This method is optimized to fetch only transactions from the day before startDate
     * onwards.
     */
    private void recomputeLocSummaryFromDate(Long locId, LocalDate startDate, LoanLineOfCreditParams lineOfCreditParams) {

        // 1. Calculate the date from which we need to fetch transactions (day before startDate)
        LocalDate fetchFromDate = startDate.minusDays(1);

        // 2. Fetch only transactions from the day before startDate onwards
        List<LineOfCreditTransaction> relevantTransactions = lineOfCreditTransactionRepository
                .findByLineOfCreditIdAndTransactionDateGreaterThanEqualOrderByTransactionDateAsc(locId, fetchFromDate);

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
                    .findTopByLineOfCreditIdOrderByTransactionDateDesc(locId).filter(tx -> tx.getTransactionDate().isBefore(startDate));

            if (lastTransactionBeforeStart.isPresent()) {
                baseAvailableBalance = lastTransactionBeforeStart.get().getBalanceAfter();
                // Count disbursements up to this point (this is an approximation, could be optimized with a count
                // query)
                baseTotalDrawDownCount = lineOfCreditTransactionRepository
                        .findByLineOfCreditIdAndTransactionDateLessThanOrderByTransactionDateAsc(locId, startDate).stream()
                        .map(tx -> isDisbursementTransaction(tx) ? BigDecimal.ONE : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            } else {
                // No transactions before startDate at all, use the original maximum amount
                baseAvailableBalance = lineOfCreditParams.getLineOfCredit().getMaximumAmount();
                baseTotalDrawDownCount = BigDecimal.ZERO;
            }
        } else {
            // Calculate baseline from the fetched transactions before startDate
            baseAvailableBalance = lineOfCreditParams.getLineOfCredit().getMaximumAmount();
            baseTotalDrawDownCount = BigDecimal.ZERO;

            // We need to get the state just before our fetched transactions to have the correct baseline
            Optional<LineOfCreditTransaction> lastTransactionBeforeFetch = lineOfCreditTransactionRepository
                    .findTopByLineOfCreditIdOrderByTransactionDateDesc(locId).filter(tx -> tx.getTransactionDate().isBefore(fetchFromDate));

            if (lastTransactionBeforeFetch.isPresent()) {
                baseAvailableBalance = lastTransactionBeforeFetch.get().getBalanceAfter();
                // Get the count of disbursements before our fetch date
                baseTotalDrawDownCount = lineOfCreditTransactionRepository
                        .findByLineOfCreditIdAndTransactionDateLessThanOrderByTransactionDateAsc(locId, fetchFromDate).stream()
                        .map(tx -> isDisbursementTransaction(tx) ? BigDecimal.ONE : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            // Apply the transactions we fetched that are before startDate
            for (LineOfCreditTransaction tx : transactionsBeforeStartDate) {
                if (isDisbursementTransaction(tx)) {
                    baseAvailableBalance = baseAvailableBalance.subtract(tx.getAmount());
                    baseTotalDrawDownCount = baseTotalDrawDownCount.add(BigDecimal.ONE);
                } else if (isRepaymentTransaction(tx)) {
                    baseAvailableBalance = baseAvailableBalance.add(tx.getAmount());
                }
            }
        }

        BigDecimal baseConsumedAmount = lineOfCreditParams.getLineOfCredit().getMaximumAmount().subtract(baseAvailableBalance);

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

            if (isDisbursementTransaction(tx)) {
                // Validate availability during re-computation for integrity check
                if (runningAvailableBalance.compareTo(transactionAmount) < 0) {
                    // This indicates an over-disbursement in history, which is a severe data error.
                    throw new IllegalStateException(
                            "LOC history error: Insufficient balance during re-computation for Tx ID: " + tx.getId());
                }
                runningAvailableBalance = runningAvailableBalance.subtract(transactionAmount);
                runningConsumedAmount = runningConsumedAmount.add(transactionAmount);
                totalDrawDownCount = totalDrawDownCount.add(BigDecimal.ONE);
            } else if (isRepaymentTransaction(tx)) {
                runningAvailableBalance = runningAvailableBalance.add(transactionAmount);
                runningConsumedAmount = runningConsumedAmount.subtract(transactionAmount);
            }

            // Update the transaction record itself with the correct calculated balances
            tx.setBalanceBefore(balanceBefore);
            tx.setBalanceAfter(runningAvailableBalance);
            transactionsToSave.add(tx);
        }

        // 6. Update the LOC Summary with the final, correct balances
        lineOfCreditParams.getLineOfCredit().getSummary().setConsumedAmount(runningConsumedAmount);
        lineOfCreditParams.getLineOfCredit().getSummary().setAvailableBalance(runningAvailableBalance);
        lineOfCreditParams.getLineOfCredit().getSummary().setTotalDrawDownCountDerived(totalDrawDownCount);

        // 7. Save all updated transaction records in a batch
        lineOfCreditTransactionRepository.saveAll(transactionsToSave);
    }

    /**
     * Helper method to check if a transaction is a disbursement
     */
    private boolean isDisbursementTransaction(LineOfCreditTransaction transaction) {
        return LoanTransactionType.DISBURSEMENT.name().equals(transaction.getTransactionType());
    }

    /**
     * Helper method to check if a transaction increases the LOC balance (repayment, refund, foreclosure)
     */
    private boolean isRepaymentTransaction(LineOfCreditTransaction transaction) {
        return LoanTransactionType.REPAYMENT.name().equals(transaction.getTransactionType())
                || LoanTransactionType.REFUND.name().equals(transaction.getTransactionType())
                || LoanTransactionType.WAIVE_INTEREST.name().equals(transaction.getTransactionType())
                || LoanTransactionType.WRITEOFF.name().equals(transaction.getTransactionType());
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
     * Gets the latest transaction date for a Line of Credit
     */
    private LocalDate getLatestTransactionDateForLoc(Long locId) {
        return lineOfCreditTransactionRepository.findTopByLineOfCreditIdOrderByTransactionDateDesc(locId)
                .map(LineOfCreditTransaction::getTransactionDate).orElse(LocalDate.MIN);
    }

}
