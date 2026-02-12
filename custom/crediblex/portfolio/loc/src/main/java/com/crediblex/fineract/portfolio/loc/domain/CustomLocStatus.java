/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.crediblex.fineract.portfolio.loc.domain;

public enum CustomLocStatus {

    INVALID(0), PAST_DUE(9000), PAST_MATURITY(9001), EARLY_CLOSURE(9002), FORCED_CLOSURE(9003), INACTIVE(9004), CLOSED(9005), EXPIRED(9006);

    private final int value;

    CustomLocStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
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

    public boolean isInactive() {
        return this == INACTIVE;
    }

    public boolean isClosed() {
        return this == CLOSED;
    }

    public boolean isExpired() {
        return this == EXPIRED;
    }

    public static CustomLocStatus fromInt(Integer value) {
        if (value == null) {
            return CustomLocStatus.INVALID;
        }
        return switch (value) {
            case 9000 -> CustomLocStatus.PAST_DUE;
            case 9001 -> CustomLocStatus.PAST_MATURITY;
            case 9002 -> CustomLocStatus.EARLY_CLOSURE;
            case 9003 -> CustomLocStatus.FORCED_CLOSURE;
            case 9004 -> CustomLocStatus.INACTIVE;
            case 9005 -> CustomLocStatus.CLOSED;
            case 9006 -> CustomLocStatus.EXPIRED;
            default -> CustomLocStatus.INVALID;
        };
    }
}
