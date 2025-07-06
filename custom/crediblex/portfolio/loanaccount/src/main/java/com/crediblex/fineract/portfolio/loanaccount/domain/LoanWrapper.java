package com.crediblex.fineract.portfolio.loanaccount.domain;

import java.math.BigDecimal;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;

public class LoanWrapper {

    private final Loan loan;

    public LoanWrapper(Loan loan) {
        this.loan = loan;
    }

    /**
     * Calculates the total of all fee charges due at disbursement (excluding TAX).
     */
    public BigDecimal deriveTotalFeeChargesDueAtDisbursement() {
        BigDecimal totalFees = BigDecimal.ZERO;
        for (LoanCharge charge : loan.getActiveCharges()) {
            if (charge.isDueAtDisbursement() && charge.isChargePending()) {
                LoanChargeWrapper chargeWrapper = new LoanChargeWrapper(charge);
                totalFees = totalFees.add(chargeWrapper.getFeePortionExcludingTax());
            }
        }
        return totalFees;
    }

    /**
     * Calculates the total TAX amount for all charges due at disbursement.
     */
    public BigDecimal deriveTotalVATChargesDueAtDisbursement() {
        BigDecimal totalTax = BigDecimal.ZERO;
        for (LoanCharge charge : loan.getActiveCharges()) {
            if (charge.isDueAtDisbursement() && charge.isChargePending()) {
                LoanChargeWrapper chargeWrapper = new LoanChargeWrapper(charge);
                totalTax = totalTax.add(chargeWrapper.getTaxesAmount());
            }
        }
        return totalTax;
    }
}
