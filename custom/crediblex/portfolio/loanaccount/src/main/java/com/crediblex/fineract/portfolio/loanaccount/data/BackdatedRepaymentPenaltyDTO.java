package com.crediblex.fineract.portfolio.loanaccount.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@RequiredArgsConstructor
public class BackdatedRepaymentPenaltyDTO {
    private final BigDecimal penaltyAmount;
}
