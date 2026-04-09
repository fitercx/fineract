package com.crediblex.fineract.portfolio.loc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class LineOfCreditSummary {

    @Column(name = "total_of_fees_derived")
    private BigDecimal totalOfFeesDerived;

    @Column(name = "total_draw_down_count_derived")
    private BigDecimal totalDrawDownCountDerived;

    @Column(name = "net_outstanding_amount_derived")
    private BigDecimal netOutstandingAmount;

    @Column(name = "available_balance", precision = 19, scale = 6, nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "consumed_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal consumedAmount;

    /**
     * Administrative blocked amount: a portion of the credit limit reserved by ops/credit teams (e.g., pending reviews,
     * compliance holds). Borrowers cannot draw down against this reserved portion.
     * <p>
     * Available Amount = Credit Limit - Blocked Amount - Consumed Amount
     */
    @Column(name = "blocked_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal blockedAmount;

    public LineOfCreditSummary(BigDecimal totalOfFeesDerived, BigDecimal totalDrawDownCountDerived, BigDecimal netOutstandingAmount,
            BigDecimal availableBalance, BigDecimal consumedAmount) {
        this(totalOfFeesDerived, totalDrawDownCountDerived, netOutstandingAmount, availableBalance, consumedAmount, BigDecimal.ZERO);
    }

    public LineOfCreditSummary(BigDecimal totalOfFeesDerived, BigDecimal totalDrawDownCountDerived, BigDecimal netOutstandingAmount,
            BigDecimal availableBalance, BigDecimal consumedAmount, BigDecimal blockedAmount) {
        this.totalOfFeesDerived = totalOfFeesDerived;
        this.totalDrawDownCountDerived = totalDrawDownCountDerived;
        this.netOutstandingAmount = netOutstandingAmount;
        this.availableBalance = availableBalance;
        this.consumedAmount = consumedAmount;
        this.blockedAmount = blockedAmount != null ? blockedAmount : BigDecimal.ZERO;
    }

    public static LineOfCreditSummary getInitialState() {
        return new LineOfCreditSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

}
