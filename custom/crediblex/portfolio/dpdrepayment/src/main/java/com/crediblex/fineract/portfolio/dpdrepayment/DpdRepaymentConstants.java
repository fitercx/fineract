package com.crediblex.fineract.portfolio.dpdrepayment;

import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;

public final class DpdRepaymentConstants {

    /**
     * Must stay identical to the loan product API parameter name, otherwise the product command payload is rejected as
     * an unsupported parameter before {@code DpdRepaymentProductConfigService} ever sees it.
     */
    public static final String ENABLE_DPD_PRINCIPAL_ONLY_REPAYMENT = LoanProductConstants.ENABLE_DPD_PRINCIPAL_ONLY_REPAYMENT_PARAM_NAME;
    public static final String DPD_PRINCIPAL_ONLY_THRESHOLD = "dpdPrincipalOnlyThreshold";

    /** {@code c_configuration.name} for fleet-wide DPD principal-only threshold (days). */
    public static final String GLOBAL_CONFIG_DPD_PRINCIPAL_ONLY_THRESHOLD = "dpd-principal-only-threshold";

    public static final String EFFECTIVE_REPAYMENT_STRATEGY_CODE = "effectiveRepaymentStrategyCode";
    public static final String EFFECTIVE_REPAYMENT_STRATEGY_NAME = "effectiveRepaymentStrategyName";
    public static final String DPD_PRINCIPAL_ONLY_ACTIVE = "dpdPrincipalOnlyActive";
    public static final String MAX_DPD = "maxDpd";

    public static final int DEFAULT_DPD_THRESHOLD = 60;

    private DpdRepaymentConstants() {}
}
