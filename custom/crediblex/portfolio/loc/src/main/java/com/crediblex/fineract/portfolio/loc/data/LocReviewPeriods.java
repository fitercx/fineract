package com.crediblex.fineract.portfolio.loc.data;

import org.apache.fineract.infrastructure.core.data.EnumOptionData;

public enum LocReviewPeriods {

    THREE_MONTHS(3, "locreviewperiod.3months"), SIX_MONTHS(6, "locreviewperiod.6months"), TWELVE_MONTHS(12, "locreviewperiod.12months");

    private final int value;
    private final String code;

    LocReviewPeriods(int value, String code) {
        this.value = value;
        this.code = code;
    }

    public EnumOptionData getEnumOptionsData() {
        return new EnumOptionData((long) this.value, this.code, this.name());
    }
}
