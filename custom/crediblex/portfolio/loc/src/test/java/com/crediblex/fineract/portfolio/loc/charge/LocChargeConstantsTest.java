package com.crediblex.fineract.portfolio.loc.charge;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocChargeConstantsTest {

    @Test
    @DisplayName("Supported charge time types are recognized")
    void supportedTypes() {
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.SPECIFIED_DUE_DATE)).isTrue();
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.ANNUAL_FEE)).isTrue();
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.MONTHLY_FEE)).isTrue();
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.WEEKLY_FEE)).isTrue();
    }

    @Test
    @DisplayName("Unsupported charge time types are rejected")
    void unsupportedTypes() {
        // Loan-only types
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.DISBURSEMENT)).isFalse();
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.INSTALMENT_FEE)).isFalse();
        // Savings-only extras not in scope
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.WITHDRAWAL_FEE)).isFalse();
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.OVERDRAFT_FEE)).isFalse();
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.SAVINGS_NOACTIVITY_FEE)).isFalse();
        // Share-related
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.SHARE_PURCHASE)).isFalse();
        assertThat(LocChargeConstants.isSupportedChargeTime(ChargeTimeType.SHARE_REDEEM)).isFalse();
        // Null safety
        assertThat(LocChargeConstants.isSupportedChargeTime(null)).isFalse();
    }
}

