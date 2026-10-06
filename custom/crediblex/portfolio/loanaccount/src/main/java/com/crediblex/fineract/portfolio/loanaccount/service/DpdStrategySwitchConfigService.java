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
package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.data.DpdStrategySwitchConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationProperty;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepository;
import org.springframework.stereotype.Service;

/**
 * Reads the fleet-wide settings for the DPD strategy auto-switch from {@code c_configuration}.
 *
 * <p>
 * The {@code enabled} flag acts as a kill switch: while it is off the feature behaves as if no product had opted in,
 * which means already-switched loans are reverted to their original strategy rather than left stranded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DpdStrategySwitchConfigService {

    private final GlobalConfigurationRepository globalConfigurationRepository;

    /** Fleet-wide kill switch. Defaults to disabled when the configuration row is missing. */
    public boolean isGloballyEnabled() {
        final GlobalConfigurationProperty property = findProperty();
        return property != null && property.isEnabled();
    }

    /**
     * DPD threshold in days. Falls back to {@link DpdStrategySwitchConstants#DEFAULT_THRESHOLD_DAYS} when the
     * configuration row is missing or holds a non-positive value, so a mis-set value can never switch every loan.
     */
    public int getThresholdDays() {
        final GlobalConfigurationProperty property = findProperty();
        if (property == null || property.getValue() == null || property.getValue() <= 0) {
            return DpdStrategySwitchConstants.DEFAULT_THRESHOLD_DAYS;
        }
        return property.getValue().intValue();
    }

    private GlobalConfigurationProperty findProperty() {
        return globalConfigurationRepository.findOneByName(DpdStrategySwitchConstants.GLOBAL_CONFIG_THRESHOLD);
    }
}
