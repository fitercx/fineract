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
package com.crediblex.fineract.portfolio.loanaccount.util;

import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanEvent;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;

public final class LoanChargeSettlementUtils {

    private LoanChargeSettlementUtils() {}

    public static boolean closeIfFullySettled(final Loan loan, final LocalDate transactionDate,
            final LoanLifecycleStateMachine loanLifecycleStateMachine) {
        if (loan.getStatus().isActive() && loan.getSummary().isRepaidInFull(loan.getCurrency()) && hasNoPayableChargesRemaining(loan)) {
            loan.setClosedOnDate(transactionDate);
            loan.setActualMaturityDate(transactionDate);
            loanLifecycleStateMachine.transition(LoanEvent.REPAID_IN_FULL, loan);
            return true;
        }
        return false;
    }

    /**
     * Recompute the loan's derived summary/status and then close it as obligations-met if it is fully settled.
     *
     * <p>
     * This is the single "settle then close" entry point every custom charge-settlement path (bulk waive, overdue
     * deactivate, window waive, reverse-paid) must use instead of a bare {@link Loan#updateLoanSummaryAndStatus()}.
     * {@code updateLoanSummaryAndStatus()} alone routes to base {@code Loan.handleLoanRepaymentInFull()}, which decides
     * "all charges paid" from each charge's paid/waived <b>flag</b>. When a settlement path drives a charge to zero
     * outstanding without flipping that flag, a fully-repaid loan matches neither the "all charges paid" nor the
     * "overpaid" branch and is silently left ACTIVE. {@link #closeIfFullySettled} additionally treats any
     * zero-outstanding charge as settled (amount-based), so it closes such a loan correctly. Calling both keeps every
     * settlement path consistent with the single-charge waive path, which already does this.
     */
    public static boolean refreshSummaryStatusAndCloseIfSettled(final Loan loan, final LocalDate transactionDate,
            final LoanLifecycleStateMachine loanLifecycleStateMachine) {
        loan.updateLoanSummaryAndStatus();
        return closeIfFullySettled(loan, transactionDate, loanLifecycleStateMachine);
    }

    public static boolean hasNoPayableChargesRemaining(final Loan loan) {
        return loan.getCharges().stream().allMatch(LoanChargeSettlementUtils::isSettled);
    }

    private static boolean isSettled(final LoanCharge charge) {
        return !charge.isActive() || MathUtil.isEmpty(charge.amount()) || charge.isPaid() || charge.isWaived()
                || (MathUtil.isEmpty(charge.amountOutstanding()) && MathUtil.isEmpty(charge.getTaxAmountOutstanding()));
    }
}
