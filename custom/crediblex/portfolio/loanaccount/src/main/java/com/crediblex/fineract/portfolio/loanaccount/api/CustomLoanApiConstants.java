package com.crediblex.fineract.portfolio.loanaccount.api;

/**
 * Custom API parameter and resource constants for CredibleX loan account customizations.
 */
public final class CustomLoanApiConstants {

    private CustomLoanApiConstants() {}

    /**
     * Optional flag indicating whether the backend should automatically withdraw from the destination savings account
     * immediately after a successful loan disbursement to savings.
     */
    public static final String AUTO_WITHDRAW_FROM_SAVINGS_PARAM = "autoWithdrawFromSavings";

    /**
     * Optional override for the amount to withdraw from the destination savings account after disbursement.
     */
    public static final String WITHDRAWAL_AMOUNT_PARAM = "withdrawalAmount";

}
