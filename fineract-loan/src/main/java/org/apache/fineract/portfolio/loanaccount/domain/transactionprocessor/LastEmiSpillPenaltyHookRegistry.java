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
package org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor;

import java.time.LocalDate;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;

/**
 * Static holder for an optional {@link LastEmiSpillPenaltyHook}. Transaction processors are not Spring beans; the
 * custom module registers the implementation at startup. With no hook, allocation is unchanged.
 */
public final class LastEmiSpillPenaltyHookRegistry {

    private static volatile LastEmiSpillPenaltyHook hook;

    private LastEmiSpillPenaltyHookRegistry() {}

    public static void register(final LastEmiSpillPenaltyHook hookImpl) {
        hook = hookImpl;
    }

    public static void foldOntoLastEmi(final Loan loan, final LocalDate transactionDate) {
        final LastEmiSpillPenaltyHook current = hook;
        if (current != null && loan != null) {
            current.foldOntoLastEmi(loan, transactionDate);
        }
    }
}
