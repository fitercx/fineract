package com.crediblex.fineract.portfolio.dpdrepayment.data;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DpdRepaymentStatusData {

    private final int maxDpd;
    private final boolean dpdPrincipalOnlyActive;
    private final String effectiveRepaymentStrategyCode;
    private final String effectiveRepaymentStrategyName;
    private final String baseRepaymentStrategyCode;
    private final int dpdThreshold;
    private final boolean productFeatureEnabled;
}
