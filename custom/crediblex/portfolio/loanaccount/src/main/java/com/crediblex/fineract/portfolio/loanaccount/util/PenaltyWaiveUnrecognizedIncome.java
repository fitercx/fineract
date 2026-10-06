package com.crediblex.fineract.portfolio.loanaccount.util;

import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;

/**
 * LMS-128: CRED VAT reused the waive factory's 3rd argument as tax. For penalty/LPI only, keep that argument at zero
 * and store leftover as unrecognized income (Apache behaviour). Fee and VAT waives are unchanged so repayment/tax flows
 * are not distorted.
 */
public final class PenaltyWaiveUnrecognizedIncome {

    private PenaltyWaiveUnrecognizedIncome() {}

    public static Money thirdArgumentForWaiveFactory(final LoanCharge loanCharge, final Money unrecognizedIncome) {
        if (loanCharge != null && loanCharge.isPenaltyCharge() && unrecognizedIncome != null) {
            return unrecognizedIncome.zero();
        }
        return unrecognizedIncome;
    }

    public static void recordOnPenaltyWaive(final LoanTransaction waiveTransaction, final LoanCharge loanCharge,
            final Money unrecognizedIncome) {
        if (waiveTransaction == null || loanCharge == null || unrecognizedIncome == null || !loanCharge.isPenaltyCharge()
                || !unrecognizedIncome.isGreaterThanZero()) {
            return;
        }
        final Money zero = unrecognizedIncome.zero();
        waiveTransaction.updateUnrecognizedChargesComponents(zero, zero, unrecognizedIncome);
    }
}
