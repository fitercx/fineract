package com.crediblex.fineract.portfolio.loanaccount.data;

import java.util.Collection;
import java.util.List;

public final class LoanAccountAdditionalProperties {

    private LoanAccountAdditionalProperties() {

    }

    public static String IS_FORCED_CLOSURE = "isForcedClosure";
    public static String IS_RESTRUCTURED = "isRestructured";
    public static String LOAN_INTEREST_VARIATIONS = "loanInterestVariations";
    // Added invoice related parameters
    public static String INVOICE_NO = "invoiceNo";
    public static String INVOICE_DATE = "invoiceDate";
    public static String INVOICE_DUE_DATE = "invoiceDueDate";
    public static String INVOICE_CURRENCY = "invoiceCurrency";
    public static String INVOICE_AMOUNT = "invoiceAmount";

    // New LOC-related parameters
    public static String DISAPPROVED_AMOUNT = "disapprovedAmount";
    public static String APPROVED_RECEIVABLE_AMOUNT = "approvedReceivableAmount";
    public static String ADVANCE_PERCENTAGE = "advancePercentage";
    public static String AMOUNT_AFTER_ADVANCE = "amountAfterAdvance";
    public static String BUYER_DETAILS = "buyerDetails";

    // Additional LOC-related parameters
    public static String EXCHANGE_RATE = "exchangeRate";
    public static String MARKUP = "markup";
    public static String AMOUNT_IN_FACILITY_CURRENCY = "amountInFacilityCurrency";
    public static String APPROVED_PAYABLE_AMOUNT = "approvedPayableAmount";
    public static String SUPPLIER_DETAILS = "supplierDetails";

    public static String LINE_OF_CREDIT_ID = "lineOfCreditId";
    public static String LOC_TYPE = "locType";
    public static String FACTOR_RATE_ENABLED = "factorRateEnabled";

    public static Collection<String> getAllLoanAccountAdditionalPropertiesParameters() {
        return List.of(INVOICE_NO, INVOICE_DATE, INVOICE_AMOUNT, INVOICE_DUE_DATE, INVOICE_CURRENCY, DISAPPROVED_AMOUNT,
                APPROVED_RECEIVABLE_AMOUNT, ADVANCE_PERCENTAGE, AMOUNT_AFTER_ADVANCE, BUYER_DETAILS, EXCHANGE_RATE, MARKUP,
                AMOUNT_IN_FACILITY_CURRENCY, APPROVED_PAYABLE_AMOUNT, SUPPLIER_DETAILS, LINE_OF_CREDIT_ID, LOC_TYPE, FACTOR_RATE_ENABLED);
    }

}
