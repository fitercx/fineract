package com.crediblex.fineract.portfolio.dpdrepayment.service;

/**
 * Shared SQL fragments for overdue installment outstanding balances (aligned with CredibleX overdue APIs and LMS-113
 * penalty-job filters).
 */
public final class OverdueInstallmentBalanceSql {

    private OverdueInstallmentBalanceSql() {}

    public static String principalOutstanding(final String alias) {
        return "(coalesce(" + alias + ".principal_amount, 0) - coalesce(" + alias + ".principal_completed_derived, 0) - coalesce(" + alias
                + ".principal_writtenoff_derived, 0))";
    }

    public static String interestOutstanding(final String alias) {
        return "(coalesce(" + alias + ".interest_amount, 0) - coalesce(" + alias + ".interest_completed_derived, 0) - coalesce(" + alias
                + ".interest_waived_derived, 0) - coalesce(" + alias + ".interest_writtenoff_derived, 0))";
    }

    public static String lpiOutstanding(final String alias) {
        return "(coalesce(" + alias + ".penalty_charges_amount, 0) - coalesce(" + alias
                + ".penalty_charges_completed_derived, 0) - coalesce(" + alias + ".penalty_charges_waived_derived, 0) - coalesce(" + alias
                + ".penalty_charges_writtenoff_derived, 0))";
    }

    public static String totalOverdueOutstanding(final String alias) {
        return "(" + principalOutstanding(alias) + " + " + interestOutstanding(alias) + " + " + lpiOutstanding(alias) + ")";
    }

    public static String hasChargeableOutstanding(final String alias) {
        return "(" + principalOutstanding(alias) + " > 0 or " + interestOutstanding(alias) + " > 0 or " + lpiOutstanding(alias) + " > 0)";
    }
}
