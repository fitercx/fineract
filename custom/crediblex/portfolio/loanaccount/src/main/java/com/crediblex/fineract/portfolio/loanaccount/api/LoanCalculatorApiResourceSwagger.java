package com.crediblex.fineract.portfolio.loanaccount.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Created for Swagger Documentation
 */
final class LoanCalculatorApiResourceSwagger {
    private LoanCalculatorApiResourceSwagger() {}

    @Schema(description = "PostLoanCalculatorRequest")
    public static final class PostLoanCalculatorRequest {

        @Schema(example = "1000000")
        public BigDecimal principal;

        @Schema(example = "12")
        public Integer loanTermFrequency;

        @Schema(example = "2", description = "2=Months, 0=Days, 1=Weeks, 3=Years")
        public Integer loanTermFrequencyType;

        @Schema(example = "12")
        public Integer numberOfRepayments;

        @Schema(example = "25.0")
        public BigDecimal interestRatePerPeriod;

        @Schema(example = "1", description = "0=Declining Balance, 1=Flat")
        public Integer interestType;

        @Schema(example = "1", description = "1=Equal installments, 0=Equal principal payments")
        public Integer amortizationType;

        @Schema(example = "1", description = "1=Same as repayment period, 0=Daily")
        public Integer interestCalculationPeriodType;

        @Schema(example = "3", description = "3=Per annum, 2=Per month, 1=Per week, 0=Per day")
        public Integer interestRateFrequencyType;

        @Schema(example = "19 May 2025")
        public String expectedDisbursementDate;

        @Schema(example = "mifos-standard-strategy")
        public String transactionProcessingStrategyCode;
    }

    @Schema(description = "PostLoanCalculatorResponse")
    public static final class PostLoanCalculatorResponse {

        @Schema(example = "91666.67")
        public BigDecimal installmentAmount;

        @Schema(example = "100000.00")
        public BigDecimal interestAmount;

        @Schema(example = "1100000.00")
        public BigDecimal repaymentAmount;
    }
}
