package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class BackdatedRepaymentPenaltyDTO {

    private final BigDecimal penaltyAmountDue;
    private final BigDecimal principalOutstanding;
    private final BigDecimal interestOutstanding;
}
