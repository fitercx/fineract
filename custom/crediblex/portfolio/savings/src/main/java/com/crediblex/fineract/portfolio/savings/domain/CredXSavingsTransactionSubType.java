package com.crediblex.fineract.portfolio.savings.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum CredXSavingsTransactionSubType {

    DISBURSAL(1, "savingsAccountTransactionSubType.disbursal", "Disbursal"),
    REFUND(2, "savingsAccountTransactionSubType.refund", "Refund"),
    EMI_TRANSFER(3, "savingsAccountTransactionSubType.emiTransfer", "EMI Transfer");

    private static final Map<Integer, CredXSavingsTransactionSubType> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toMap(CredXSavingsTransactionSubType::getValue, value -> value));

    private final int value;
    private final String code;
    private final String displayName;

    CredXSavingsTransactionSubType(final int value, final String code, final String displayName) {
        this.value = value;
        this.code = code;
        this.displayName = displayName;
    }

    public int getValue() {
        return this.value;
    }

    public String getCode() {
        return this.code;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public static CredXSavingsTransactionSubType fromValue(final Integer value) {
        return value == null ? null : BY_VALUE.get(value);
    }
}
