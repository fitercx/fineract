package com.crediblex.fineract.portfolio.savings.data;

import com.crediblex.fineract.portfolio.savings.domain.CredXSavingsTransactionSubType;
import java.io.Serializable;
import lombok.Getter;

@Getter
public class CredXSavingsTransactionSubTypeData implements Serializable {

    private final Integer value;
    private final String code;
    private final String displayName;

    public CredXSavingsTransactionSubTypeData(final CredXSavingsTransactionSubType subType) {
        this.value = subType == null ? null : subType.getValue();
        this.code = subType == null ? null : subType.getCode();
        this.displayName = subType == null ? null : subType.getDisplayName();
    }
}
