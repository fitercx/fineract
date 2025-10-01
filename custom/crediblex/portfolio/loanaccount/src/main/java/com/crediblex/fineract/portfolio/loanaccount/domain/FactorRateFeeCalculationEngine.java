package com.crediblex.fineract.portfolio.loanaccount.domain;

import com.crediblex.fineract.portfolio.loanaccount.data.FactorRateCalculationResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class FactorRateFeeCalculationEngine {

    private final MathContext mc = new MathContext(18, RoundingMode.HALF_UP);
    private final int currencyScale;

    public FactorRateFeeCalculationEngine(int currencyScale) {
        this.currencyScale = currencyScale;
    }

    /**
     * Calculates the fee, VAT, and related amounts for a loan based on the factor rate model. The calculation
     * determines the total fee by multiplying the principal by the factor rate, subtracting the principal, and then
     * computes VAT and net fee amounts. It also calculates the net disbursed amount and daily breakdowns for principal,
     * fee, VAT, and net fee.
     *
     * @param principal
     *            the principal loan amount (must be non-null, positive, and in currency units)
     * @param factorRate
     *            the factor rate to apply (must be &gt;= 1.0)
     * @param vatPercent
     *            the VAT percentage as a decimal (e.g., 0.16 for 16%); if null, treated as 0
     * @param termDays
     *            the loan term in days (must be &gt; 0)
     * @return a {@link FactorRateCalculationResult} containing the calculated amounts: total fee, VAT, net fee, net
     *         disbursed, and daily breakdowns
     * @throws IllegalArgumentException
     *             if {@code termDays} &lt;= 0 or {@code factorRate} &lt; 1.0
     */
    public FactorRateCalculationResult calculate(BigDecimal principal, BigDecimal factorRate, BigDecimal vatPercent, int termDays) {

        if (termDays <= 0) {
            throw new IllegalArgumentException("termDays must be > 0");
        }
        if (factorRate.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("factorRate must be >= 1.0");
        }

        BigDecimal vatRate = vatPercent == null ? BigDecimal.ZERO : vatPercent;

        BigDecimal totalFactorAmount = principal.multiply(factorRate, mc);
        BigDecimal totalFee = totalFactorAmount.subtract(principal, mc);
        if (totalFee.signum() < 0) {
            totalFee = BigDecimal.ZERO;
        }

        BigDecimal totalVat = totalFee.multiply(vatRate, mc);
        BigDecimal totalNetFee = totalFee.subtract(totalVat, mc);
        BigDecimal netDisbursed = principal.subtract(totalFee, mc);

        BigDecimal dailyPrincipal = netDisbursed.divide(BigDecimal.valueOf(termDays), mc);
        BigDecimal dailyGrossFee = totalFee.divide(BigDecimal.valueOf(termDays), mc);
        BigDecimal dailyVat = totalVat.divide(BigDecimal.valueOf(termDays), mc);
        BigDecimal dailyNetFee = totalNetFee.divide(BigDecimal.valueOf(termDays), mc);

        return new FactorRateCalculationResult(principal.setScale(currencyScale, RoundingMode.HALF_UP), factorRate, termDays,
                totalFee.setScale(currencyScale, RoundingMode.HALF_UP), totalVat.setScale(currencyScale, RoundingMode.HALF_UP),
                totalNetFee.setScale(currencyScale, RoundingMode.HALF_UP), netDisbursed.setScale(currencyScale, RoundingMode.HALF_UP),
                dailyPrincipal.setScale(currencyScale, RoundingMode.HALF_UP), dailyGrossFee.setScale(currencyScale, RoundingMode.HALF_UP),
                dailyVat.setScale(currencyScale, RoundingMode.HALF_UP), dailyNetFee.setScale(currencyScale, RoundingMode.HALF_UP));
    }
}
