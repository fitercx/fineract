package com.crediblex.fineract.portfolio.loc.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class LineOfCreditSummary {
    private final Long id;
    private final String externalId;
    private final LocProductType productType;
}
