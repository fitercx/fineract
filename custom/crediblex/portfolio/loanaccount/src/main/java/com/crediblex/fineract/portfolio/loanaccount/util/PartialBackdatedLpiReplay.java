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
import java.util.function.Supplier;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;

/**
 * Keeps the partial-backdated LPI align alive inside {@code reprocessTransactions}.
 * <p>
 * Aligning the schedule before {@code makeRepayment} is enough when that repayment is the latest transaction, because
 * Fineract then runs {@code processLatestTransaction} on the aligned schedule. A second partial dated earlier than an
 * existing repayment is not the latest transaction, so Fineract replays history. That replay rebuilds installment
 * penalty from {@code isDueInPeriod} and drops the align before the new repayment is allocated. While this window is
 * open, each repayment on or after the new value date is aligned to its own date immediately before allocation.
 */
public final class PartialBackdatedLpiReplay {

    private static final ThreadLocal<LocalDate> ALIGN_FROM = new ThreadLocal<>();

    private PartialBackdatedLpiReplay() {}

    public static <T> T aligningFrom(final LocalDate valueDate, final Supplier<T> work) {
        if (valueDate == null) {
            return work.get();
        }
        final LocalDate previous = ALIGN_FROM.get();
        ALIGN_FROM.set(valueDate);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                ALIGN_FROM.remove();
            } else {
                ALIGN_FROM.set(previous);
            }
        }
    }

    public static void realignIfReplaying(final Loan loan, final LocalDate transactionDate) {
        final LocalDate from = ALIGN_FROM.get();
        if (from == null || transactionDate == null || transactionDate.isBefore(from)) {
            return;
        }
        InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(loan, transactionDate);
    }
}
