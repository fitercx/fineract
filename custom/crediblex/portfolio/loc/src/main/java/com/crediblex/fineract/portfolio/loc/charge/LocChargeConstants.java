package com.crediblex.fineract.portfolio.loc.charge;

import java.util.EnumSet;
import java.util.Set;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;

public final class LocChargeConstants {

    private LocChargeConstants() {

    }

    public static final Set<ChargeTimeType> SUPPORTED_CHARGE_TIME_TYPES = EnumSet.of(ChargeTimeType.LINE_OF_CREDIT_ACTIVATION);

    /**
     * Simple helper to query support. Wrapper kept to centralize any future alias mapping.
     *
     * @param type
     *            charge time type
     * @return true if supported for LOC
     */
    public static boolean isSupportedChargeTime(ChargeTimeType type) {
        return type != null && SUPPORTED_CHARGE_TIME_TYPES.contains(type);
    }

}
