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
package com.crediblex.fineract.portfolio.loanaccount.data;

import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;

public final class DpdStrategySwitchConstants {

    /**
     * Product-level opt-in. Aliased to the loan product API parameter so the two can never drift; if they did, the
     * product command payload would be rejected as an unsupported parameter before it reached the write service.
     */
    public static final String ENABLE_DPD_STRATEGY_SWITCH = LoanProductConstants.ENABLE_DPD_STRATEGY_SWITCH_PARAM_NAME;

    /** Global configuration holding the DPD threshold in days, in {@code c_configuration.value}. */
    public static final String GLOBAL_CONFIG_THRESHOLD = "dpd-strategy-switch-threshold";

    public static final int DEFAULT_THRESHOLD_DAYS = 60;

    // Keys published on the loan GET under additionalProperties.
    public static final String SWITCH_ACTIVE = "dpdStrategySwitchActive";
    public static final String SWITCH_THRESHOLD = "dpdStrategySwitchThreshold";
    public static final String SWITCH_MAX_DPD = "dpdStrategySwitchMaxDpd";
    public static final String SWITCH_ORIGINAL_STRATEGY_NAME = "dpdStrategySwitchOriginalStrategyName";
    public static final String SWITCH_ON_DATE = "dpdStrategySwitchedOnDate";

    private DpdStrategySwitchConstants() {}
}
