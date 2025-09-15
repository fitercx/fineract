package com.crediblex.fineract.portfolio.loc.data;

import lombok.Getter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

public enum LocStatus {

    ACTIVE(200,"status.active"), INACTIVE(300,"status.inactive"),
    SUSPENDED(400,"status.suspended"), CLOSED(500,"status.closed");

    @Getter
    final Integer value;
    @Getter
    final String code;

    LocStatus(Integer value, String code) {
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
