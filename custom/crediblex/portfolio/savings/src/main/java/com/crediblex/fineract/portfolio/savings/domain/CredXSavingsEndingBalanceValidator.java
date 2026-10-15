package com.crediblex.fineract.portfolio.savings.domain;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.exception.InsufficientAccountBalanceException;
import org.springframework.stereotype.Component;

/**
 * Withdrawal checks in core walk transactions by value date and reject the first date whose running total is negative.
 * Migrated savings are booked in entry order, so a value date can sit before the deposit that funded it. That walk goes
 * negative in the middle and still finishes at the real balance. This checks the balance after every transaction.
 */
@Component
public class CredXSavingsEndingBalanceValidator {

    public void validate(final SavingsAccount account, final BigDecimal transactionAmount, final boolean isException,
            final boolean backdatedTxnsAllowedTill) {
        final MonetaryCurrency currency = account.getCurrency();
        final Money runningBalance = endingBalance(account, currency, backdatedTxnsAllowedTill);
        final Money minRequiredBalance = minRequiredBalance(account, currency);

        if (!isException && runningBalance.minus(minRequiredBalance).isLessThanZero()) {
            throw new InsufficientAccountBalanceException("transactionAmount", account.getAccountBalance(), null, transactionAmount);
        }

        final BigDecimal savingsHoldAmount = account.getSavingsHoldAmount();
        if (savingsHoldAmount != null && savingsHoldAmount.compareTo(BigDecimal.ZERO) > 0) {
            final Money hold = Money.of(currency, savingsHoldAmount);
            if (account.isEnforceMinRequiredBalance()) {
                if (runningBalance.minus(minRequiredBalance.plus(hold)).isLessThanZero()) {
                    throw new InsufficientAccountBalanceException("transactionAmount", account.getAccountBalance(), null,
                            transactionAmount);
                }
            } else if (runningBalance.minus(hold).isLessThanZero()) {
                throw new InsufficientAccountBalanceException("transactionAmount", account.getAccountBalance(), null, transactionAmount);
            }
        }
    }

    private Money endingBalance(final SavingsAccount account, final MonetaryCurrency currency,
            final boolean backdatedTxnsAllowedTill) {
        final List<SavingsAccountTransaction> transactions = backdatedTxnsAllowedTill
                ? account.getSavingsAccountTransactionsWithPivotConfig()
                : account.getTransactions();
        Money runningBalance = Money.zero(currency);
        for (final SavingsAccountTransaction transaction : transactions) {
            if (!transaction.isNotReversed() || transaction.isReversalTransaction()) {
                continue;
            }
            if (transaction.isCredit()) {
                runningBalance = runningBalance.plus(transaction.getAmount(currency));
            } else if (transaction.isDebit()) {
                runningBalance = runningBalance.minus(transaction.getAmount(currency));
            }
        }
        return runningBalance;
    }

    private Money minRequiredBalance(final SavingsAccount account, final MonetaryCurrency currency) {
        Money minRequiredBalance = Money.zero(currency);
        if (account.isEnforceMinRequiredBalance() && account.getMinRequiredBalance() != null) {
            minRequiredBalance = minRequiredBalance.plus(account.getMinRequiredBalance());
        }
        if (account.isAllowOverdraft() && account.getOverdraftLimit() != null) {
            minRequiredBalance = minRequiredBalance.minus(account.getOverdraftLimit());
        }
        if (account.getOnHoldFunds() != null) {
            minRequiredBalance = minRequiredBalance.plus(account.getOnHoldFunds());
        }
        return minRequiredBalance;
    }
}
