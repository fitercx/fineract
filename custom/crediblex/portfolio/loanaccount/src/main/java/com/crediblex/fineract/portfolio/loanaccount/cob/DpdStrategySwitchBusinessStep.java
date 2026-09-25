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

import com.crediblex.fineract.portfolio.loanaccount.service.DpdStrategySwitchService;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.cob.loan.LoanCOBBusinessStep;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.springframework.stereotype.Component;

/**
 * Re-evaluates the DPD repayment strategy switch once a day for every active loan (LMS-139).
 *
 * <p>
 * The transaction processor only evaluates the switch when money moves, which leaves two blind spots this step closes:
 * a delinquent loan that stops paying never switches <em>to</em> principal-first, and a loan that was brought current
 * keeps a switched strategy until its next transaction. Running here means both the stored strategy and the
 * "Auto-switched (DPD)" badge are truthful every morning.
 *
 * <p>
 * Registered last in {@code LOAN_CLOSE_OF_BUSINESS} so that everything which can still move the loan's overdue position
 * that day - notably overdue charge application and arrears ageing - has already run. COB persists the loan after the
 * step chain, so updating the strategy in place here is enough.
 */
@Component
@RequiredArgsConstructor
public class DpdStrategySwitchBusinessStep implements LoanCOBBusinessStep {

    public static final String ENUM_STYLED_NAME = "DPD_STRATEGY_SWITCH";

    private static final String HUMAN_READABLE_NAME = "DPD Repayment Strategy Switch";

    private final DpdStrategySwitchService dpdStrategySwitchService;

    @Override
    public Loan execute(final Loan loan) {
        dpdStrategySwitchService.reevaluate(loan, DateUtils.getBusinessLocalDate());
        return loan;
    }

    @Override
    public String getEnumStyledName() {
        return ENUM_STYLED_NAME;
    }

    @Override
    public String getHumanReadableName() {
        return HUMAN_READABLE_NAME;
    }
}
