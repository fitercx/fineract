package com.crediblex.fineract.portfolio.savings.domain;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionSummaryWrapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Extends the stock {@link SavingsAccountTransactionSummaryWrapper} to include {@code CHARGE_REVERSAL} transactions in
 * the total-deposits calculation.
 *
 * <p>
 * Background: when a paid loan charge is reversed, the custom {@code CredXLoanChargeWritePlatformServiceImpl} credits
 * the linked savings account via a regular deposit and then flips the transaction type to {@code CHARGE_REVERSAL} (type
 * 23) using reflection. Because {@code CHARGE_REVERSAL} is a credit entry type it is already included in the
 * per-transaction running balance computed by {@code SavingsAccount.recalculateDailyBalances}. However, the stock
 * {@code calculateTotalDeposits} only counts {@code DEPOSIT} and {@code DIVIDEND_PAYOUT}, so
 * {@code account_balance_derived} (derived from {@code total_deposits_derived}) diverges from the per-transaction
 * running balance by the sum of all reversed-charge amounts.
 *
 * <p>
 * Marking this bean {@code @Primary} ensures that every place in the savings domain that receives a
 * {@link SavingsAccountTransactionSummaryWrapper} (through {@code SavingsAccountAssembler.setHelpers}) will use this
 * corrected implementation without any changes to open-source Fineract modules.
 */
@Component
@Primary
public class CredXSavingsAccountTransactionSummaryWrapper extends SavingsAccountTransactionSummaryWrapper {

    @Override
    public BigDecimal calculateTotalDeposits(final MonetaryCurrency currency, final List<SavingsAccountTransaction> transactions) {
        Money total = Money.zero(currency);
        for (final SavingsAccountTransaction transaction : transactions) {
            boolean isChargeReversalAndNotReversed = transaction.getTransactionType() == SavingsAccountTransactionType.CHARGE_REVERSAL
                    && transaction.isNotReversed();
            if ((transaction.isDepositAndNotReversed() || transaction.isDividendPayoutAndNotReversed() || isChargeReversalAndNotReversed)
                    && !transaction.isReversalTransaction()) {
                total = total.plus(transaction.getAmount(currency));
            }
        }
        return total.getAmountDefaultedToNullIfZero();
    }
}
