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
package org.apache.fineract.portfolio.loanaccount.domain;

import java.util.Arrays;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

@Getter
public enum CustomLoanStatus {


    // Note: 0 is reserved for INVALID to represent null/unmapped values from the DB or client input.
    // Custom loan statuses are assigned codes in the 9000+ range to avoid clashing with core loan
    // status codes defined elsewhere in the system and persisted in the database.
    INVALID(0), PAST_DUE(9000), PAST_MATURITY(9001), EARLY_CLOSURE(9002), FORCED_CLOSURE(9003);

    private final int value;

    CustomLoanStatus(int value) {
        this.value = value;
    }

    public boolean isPastDue() {
        return this == PAST_DUE;
    }

    public boolean isPastMaturity() {
        return this == PAST_MATURITY;
    }

    public boolean isEarlyClosure() {
        return this == EARLY_CLOSURE;
    }

    public boolean isForcedClosure() {
        return this == FORCED_CLOSURE;
    }

    public static CustomLoanStatus fromInt(Integer value) {
        if (value == null) {
            return CustomLoanStatus.INVALID; // allow nulls from DB without failing
        }

        return switch (value) {
            case 9000 -> CustomLoanStatus.PAST_DUE;
            case 9001 -> CustomLoanStatus.PAST_MATURITY;
            case 9002 -> CustomLoanStatus.EARLY_CLOSURE;
            case 9003 -> CustomLoanStatus.FORCED_CLOSURE;
            default -> CustomLoanStatus.INVALID;
        };
    }
}
