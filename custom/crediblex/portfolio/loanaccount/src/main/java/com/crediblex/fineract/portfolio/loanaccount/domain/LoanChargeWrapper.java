package com.crediblex.fineract.portfolio.loanaccount.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.tax.domain.TaxComponent;
import org.apache.fineract.portfolio.tax.domain.TaxGroup;
import org.apache.fineract.portfolio.tax.domain.TaxGroupMappings;

public class LoanChargeWrapper {

    private final LoanCharge loanCharge;

    public LoanChargeWrapper(LoanCharge loanCharge) {
        this.loanCharge = loanCharge;
    }

    public LoanCharge getLoanCharge() {
        return loanCharge;
    }

    public boolean isOverdue() {
        LocalDate due = loanCharge.getDueDate();
        return due != null && due.isBefore(LocalDate.now());
    }

    /**
     * Returns true if this charge has a tax component (i.e., is linked to a TaxGroup with at least one TaxComponent).
     */
    public boolean hasTaxComponent() {
        Charge charge = loanCharge.getCharge();
        TaxGroup taxGroup = charge.getTaxGroup();
        return taxGroup != null && taxGroup.getTaxGroupMappings() != null && !taxGroup.getTaxGroupMappings().isEmpty();
    }

    /**
     * Calculates the tax amount for this charge, based on its TaxGroup and TaxComponents. Returns BigDecimal.ZERO if no
     * TAX applies.
     */
    public BigDecimal getTaxesAmount() {
        if (!hasTaxComponent()) {
            return BigDecimal.ZERO;
        }
        Charge charge = loanCharge.getCharge();
        TaxGroup taxGroup = charge.getTaxGroup();
        BigDecimal chargeAmount = loanCharge.amountOutstanding();

        BigDecimal totalTax = BigDecimal.ZERO;
        for (TaxGroupMappings mapping : taxGroup.getTaxGroupMappings()) {
            TaxComponent taxComponent = mapping.getTaxComponent();
            BigDecimal rate = taxComponent.getPercentage();
            if (rate != null && chargeAmount != null) {
                BigDecimal tax = chargeAmount.multiply(rate).divide(BigDecimal.valueOf(100));
                totalTax = totalTax.add(tax);
            }
        }
        return totalTax;
    }

    /**
     * Returns the fee portion of the charge (excluding TAX). If no TAX applies, returns the full charge amount.
     */
    public BigDecimal getFeePortionExcludingTax() {
        BigDecimal chargeAmount = loanCharge.amountOutstanding();
        return chargeAmount.subtract(getTaxesAmount());
    }
}
