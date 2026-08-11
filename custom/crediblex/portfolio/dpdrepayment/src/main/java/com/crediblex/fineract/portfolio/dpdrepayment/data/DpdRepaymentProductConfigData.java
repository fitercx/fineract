package com.crediblex.fineract.portfolio.dpdrepayment.data;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DpdRepaymentProductConfigData {

    private final Long loanProductId;
    private final boolean enableDpdPrincipalOnlyRepayment;
    private final int dpdPrincipalOnlyThreshold;
}
