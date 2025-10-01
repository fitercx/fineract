package com.crediblex.fineract.portfolio.loc.data;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
@Builder
public class LineOfCreditSummary {

    private final Long id;
    private final String externalId;
    private final LocProductType productType;
    private final BigDecimal interestRate;
    private final BigDecimal availableBalance;
    private final BigDecimal advancePercentage;
    private final Integer tenorDays;
    private final Long loanOfficerId;
    private final List<ApprovedBuyerOrSeller> approvedBuyersOrSellers;

    public record ApprovedBuyerOrSeller(Long id, String name) {
    }
}
