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
package com.crediblex.fineract.portfolio.loanaccount.cob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.crediblex.fineract.portfolio.loanaccount.service.DpdStrategySwitchService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DpdStrategySwitchBusinessStepTest {

    private static final LocalDate COB_DATE = LocalDate.of(2026, 9, 14);

    private final DpdStrategySwitchService switchService = mock(DpdStrategySwitchService.class);
    private final DpdStrategySwitchBusinessStep underTest = new DpdStrategySwitchBusinessStep(switchService);

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void reevaluatesTheLoanAgainstTheCobBusinessDateAndPassesItDownTheChain() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, COB_DATE)));
        final Loan loan = mock(Loan.class);

        assertThat(underTest.execute(loan)).isSameAs(loan);
        // Must use the in-memory measure: earlier COB steps change the schedule without flushing.
        verify(switchService).reevaluate(loan, COB_DATE);
        assertThat(DateUtils.getBusinessLocalDate()).isEqualTo(COB_DATE);
    }

    @Test
    void isRegisteredUnderTheNameTheChangelogInserts() {
        assertThat(underTest.getEnumStyledName()).isEqualTo("DPD_STRATEGY_SWITCH");
        assertThat(underTest.getHumanReadableName()).isNotBlank();
    }
}
