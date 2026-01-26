/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.data;

import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import com.crediblex.fineract.portfolio.loc.domain.CustomLocStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class LocStatusAggregationData {

    private LocStatus defaultLocStatus;
    private CustomLocStatus oldLocCustomStatus;
    private CustomLocStatus newLocCustomStatus;

    public LocStatusAggregationData(LocStatus defaultLocStatus, CustomLocStatus oldLocCustomStatus, CustomLocStatus newLocCustomStatus) {
        this.defaultLocStatus = defaultLocStatus;
        this.oldLocCustomStatus = oldLocCustomStatus;
        this.newLocCustomStatus = newLocCustomStatus;
    }

    public static LocStatusAggregationData build(LocStatus defaultLocStatus, CustomLocStatus oldLocCustomStatus,
            CustomLocStatus newLocCustomStatus) {
        return new LocStatusAggregationData(defaultLocStatus, oldLocCustomStatus, newLocCustomStatus);
    }
}
