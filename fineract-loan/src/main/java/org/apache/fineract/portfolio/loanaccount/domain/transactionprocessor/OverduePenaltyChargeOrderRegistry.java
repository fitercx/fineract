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

import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;

/**
 * Static holder for an optional {@link OverduePenaltyChargeOrderHook}. Transaction processors are not Spring beans; the
 * custom module registers the implementation at startup. With no hook, penalty charges are paid by earliest due date.
 */
public final class OverduePenaltyChargeOrderRegistry {

    private static volatile OverduePenaltyChargeOrderHook hook;

    private OverduePenaltyChargeOrderRegistry() {}

    public static void register(final OverduePenaltyChargeOrderHook hookImpl) {
        hook = hookImpl;
    }

    public static LoanCharge nextUnpaid(final Set<LoanCharge> charges, final MonetaryCurrency currency) {
        final OverduePenaltyChargeOrderHook current = hook;
        if (current == null || charges == null || charges.isEmpty() || currency == null) {
            return null;
        }
        return current.nextUnpaid(charges, currency);
    }
}
