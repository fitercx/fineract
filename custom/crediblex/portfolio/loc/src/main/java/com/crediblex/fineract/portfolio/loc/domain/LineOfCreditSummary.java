package com.crediblex.fineract.portfolio.loc.domain;

import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class LineOfCreditSummary {
    private final Long id;
    private final String name;
    private final LocProductType productType;
}
