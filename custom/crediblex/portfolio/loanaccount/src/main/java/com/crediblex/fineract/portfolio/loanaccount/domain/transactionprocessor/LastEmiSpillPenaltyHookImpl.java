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

package com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor;

import com.crediblex.fineract.portfolio.loanaccount.util.InstallmentPenaltySyncUtils;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.LastEmiSpillPenaltyHook;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.LastEmiSpillPenaltyHookRegistry;
import org.springframework.stereotype.Component;

/**
 * Puts unpaid LPI that the schedule stores on a later row onto the EMI a repayment will actually pay, immediately
 * before allocation. Runs for every strategy. Foreclosure never calls the registry.
 */
@Component
public class LastEmiSpillPenaltyHookImpl implements LastEmiSpillPenaltyHook {

    @PostConstruct
    public void register() {
        LastEmiSpillPenaltyHookRegistry.register(this);
    }

    @Override
    public void foldOntoLastEmi(final Loan loan, final LocalDate transactionDate) {
        if (loan == null || loan.getCurrency() == null) {
            return;
        }
        InstallmentPenaltySyncUtils.foldSpillPenaltyOntoLastEmi(loan, loan.getCurrency());
        InstallmentPenaltySyncUtils.foldPreValueDatePenaltyOntoDueEmi(loan, transactionDate);
    }
}
