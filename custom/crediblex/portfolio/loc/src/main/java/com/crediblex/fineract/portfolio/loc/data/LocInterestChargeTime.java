/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loc.data;

import java.util.Objects;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

@Getter
public enum LocInterestChargeTime {

    UPFRONT(1, "locInterestChargeTime.upfront", "Upfront"), POST_DISBURSEMENT(2, "locInterestChargeTime.postdisbursement",
            "Post Disbursement");

    private final Integer value;
    private final String code;
    private final String description;

    LocInterestChargeTime(int value, String code, String description) {
        this.description = description;
        this.value = value;
        this.code = code;
    }

    public static LocInterestChargeTime fromInt(Integer value) {
        if (value == null) {
            return null;
        }
        for (LocInterestChargeTime type : LocInterestChargeTime.values()) {
            if (Objects.equals(type.getValue(), value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid interest charge time: " + value);
    }

    public EnumOptionData getEnumOptionsData() {
        return new EnumOptionData(this.value.longValue(), this.name(), this.description);
    }
}
