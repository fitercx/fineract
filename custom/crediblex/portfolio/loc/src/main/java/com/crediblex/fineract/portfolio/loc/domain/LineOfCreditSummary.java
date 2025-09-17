package com.crediblex.fineract.portfolio.loc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    public static LineOfCreditSummary getInitialState() {
        return new LineOfCreditSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

}
