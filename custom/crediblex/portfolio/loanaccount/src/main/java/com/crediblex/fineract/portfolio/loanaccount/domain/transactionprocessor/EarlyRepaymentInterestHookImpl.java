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

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanInstallmentInterestSnapshot;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanInstallmentInterestSnapshotRepository;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.EarlyRepaymentInterestHook;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.EarlyRepaymentInterestHookRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * CredibleX implementation of {@link EarlyRepaymentInterestHook}: reduces an installment's {@code interestCharged} to
 * {@code scheduledInterest x daysUsed / periodDays} when a payment is made ahead of the due date, applied
 * strategy-agnostically (mifos-standard, creocore, pro-rata, incl. Factor Rate) for all products without any change to
 * product configuration.
 * <p>
 * The pre-reduction amount is persisted in a CredibleX-owned table (see {@link LoanInstallmentInterestSnapshot}) rather
 * than on the core installment entity, so reprocessing/undo can restore the schedule interest and re-apply the
 * reduction only when an early payment is replayed.
 */
@Component
public class EarlyRepaymentInterestHookImpl implements EarlyRepaymentInterestHook {

    private final LoanInstallmentInterestSnapshotRepository snapshotRepository;

    @Autowired
    public EarlyRepaymentInterestHookImpl(final LoanInstallmentInterestSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    @PostConstruct
    public void register() {
        EarlyRepaymentInterestHookRegistry.register(this);
    }

    @Override
    public void restoreBeforeReprocessing(final LoanRepaymentScheduleInstallment installment) {
        if (installment == null || installment.getId() == null) {
            return;
        }
        snapshotRepository.findById(installment.getId()).ifPresent(snapshot -> {
            installment.updateInterestCharged(snapshot.getInterestChargedOriginal());
            snapshotRepository.delete(snapshot);
            // Flush the delete immediately instead of leaving it queued in the persistence context.
            // AbstractLoanRepaymentScheduleTransactionProcessor#processTransactions() calls this restore for every
            // installment first, then later - within the SAME reprocessing pass/transaction - calls
            // reduceForEarlyPayment() again for any installment that still has an early-payment transaction, which
            // re-saves a NEW snapshot row with the SAME assigned id (installment.getId()). Without flushing here,
            // the JPA provider still tracks the just-deleted entity by that id in this persistence context, and the
            // later save() collides with it ("cannot merge/insert an entity that has been removed"). That exception
            // previously escaped from CustomApplyChargeToOverdueLoanInstallmentTasklet (Job 12) and rolled back its
            // entire 50-loan batch - see BUG_REPORT.md Finding #0. This flush() does push the whole persistence
            // context's pending changes to the DB (not just this delete), but everything pending at this point in
            // reprocessing is already-decided, valid state (e.g. charge paid-amount resets, earlier installments'
            // restored/derived fields) that would be written out at end-of-transaction anyway - flushing it a few
            // statements earlier does not change the final result, only the timing within the same DB transaction.
            snapshotRepository.flush();
        });
    }

    @Override
    public void reduceForEarlyPayment(final LoanRepaymentScheduleInstallment installment, final LocalDate paymentDate,
            final MonetaryCurrency currency) {
        if (installment == null || installment.getId() == null || paymentDate == null || currency == null
                || installment.getDueDate() == null) {
            return;
        }
        if (!paymentDate.isBefore(installment.getDueDate())) {
            return;
        }

        final BigDecimal currentChargedAmount = installment.getInterestCharged(currency).getAmount();
        if (currentChargedAmount == null || currentChargedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        final LoanInstallmentInterestSnapshot existingSnapshot = snapshotRepository.findById(installment.getId()).orElse(null);
        final BigDecimal baseAmount = existingSnapshot != null ? existingSnapshot.getInterestChargedOriginal() : currentChargedAmount;
        if (existingSnapshot == null) {
            snapshotRepository.save(new LoanInstallmentInterestSnapshot(installment.getId(), baseAmount));
        }

        BigDecimal prorated = EarlyRepaymentInterestCalculator.calculateProRatedInterest(baseAmount, installment.getFromDate(),
                installment.getDueDate(), paymentDate);
        if (prorated == null) {
            return;
        }

        final BigDecimal floor = installment.getInterestPaid(currency).getAmount().add(installment.getInterestWaived(currency).getAmount())
                .add(installment.getInterestWrittenOff(currency).getAmount());
        if (prorated.compareTo(floor) < 0) {
            prorated = floor;
        }

        final int digits = currency.getDigitsAfterDecimal();
        prorated = prorated.setScale(digits, RoundingMode.HALF_UP);

        if (prorated.compareTo(currentChargedAmount) < 0) {
            installment.updateInterestCharged(prorated);
        }
    }
}
