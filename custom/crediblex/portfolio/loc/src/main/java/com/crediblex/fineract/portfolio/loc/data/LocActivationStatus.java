package com.crediblex.fineract.portfolio.loc.data;

import lombok.Getter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

public enum LocActivationStatus {

    ACTIVE(200,"locactivationstatus.active"), INACTIVE(300,"locactivationstatus.inactive"),
    SUSPENDED(400,"locactivationstatus.suspended"), CLOSED(500,"locactivationstatus.closed");

    @Getter
    final Integer value;
    @Getter
    final String code;

    LocActivationStatus(Integer value, String code) {
        this.value = value;
        this.code = code;
    }


    public EnumOptionData getEnumOptionData() {
        return switch (
                this) {
            case ACTIVE -> new EnumOptionData(this.value.longValue(), this.code, "Active");
            case INACTIVE -> new EnumOptionData(this.value.longValue(), this.code, "Inactive");
            case SUSPENDED -> new EnumOptionData(this.value.longValue(), this.code, "Suspended");
            case CLOSED -> new EnumOptionData(this.value.longValue(), this.code, "Closed");
            default ->
                    new EnumOptionData(0L, "error.msg.invalid.loc.activation.status", "Invalid Loc Activation Status");
        };
    }
}
