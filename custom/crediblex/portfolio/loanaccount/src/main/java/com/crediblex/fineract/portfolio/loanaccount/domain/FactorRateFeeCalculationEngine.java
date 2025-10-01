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
