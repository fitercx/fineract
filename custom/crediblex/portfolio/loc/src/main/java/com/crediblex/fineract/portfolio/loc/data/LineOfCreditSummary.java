package com.crediblex.fineract.portfolio.loc.data;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class LineOfCreditSummary {

    private final Long id;
    private final String externalId;
    private final LocProductType productType;
    private final BigDecimal interestRate;
    private final BigDecimal availableBalance;
    private final BigDecimal advancePercentage;

}
