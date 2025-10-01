package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class FactorRateCalculationResult {

    private final BigDecimal principal;
    private final BigDecimal factorRate;
    private final int termDays;

    private final BigDecimal totalFee;
    private final BigDecimal totalVat;
    private final BigDecimal totalNetFee;
    private final BigDecimal netDisbursed;

    private final BigDecimal dailyPrincipal;
    private final BigDecimal dailyGrossFee;
    private final BigDecimal dailyVat;
    private final BigDecimal dailyNetFee;

    public FactorRateCalculationResult(BigDecimal principal, BigDecimal factorRate, int termDays, BigDecimal totalFee, BigDecimal totalVat,
            BigDecimal totalNetFee, BigDecimal netDisbursed, BigDecimal dailyPrincipal, BigDecimal dailyGrossFee, BigDecimal dailyVat,
            BigDecimal dailyNetFee) {
        this.principal = principal;
        this.factorRate = factorRate;
        this.termDays = termDays;
        this.totalFee = totalFee;
        this.totalVat = totalVat;
        this.totalNetFee = totalNetFee;
        this.netDisbursed = netDisbursed;
        this.dailyPrincipal = dailyPrincipal;
        this.dailyGrossFee = dailyGrossFee;
        this.dailyVat = dailyVat;
        this.dailyNetFee = dailyNetFee;
    }
}
