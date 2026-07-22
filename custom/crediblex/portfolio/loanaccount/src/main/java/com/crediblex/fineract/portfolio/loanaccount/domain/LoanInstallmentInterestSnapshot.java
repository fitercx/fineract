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

package com.crediblex.fineract.portfolio.loanaccount.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Snapshot of a {@code m_loan_repayment_schedule} installment's {@code interest_amount} before an early-repayment
 * pro-rata reduction is applied (see {@code EarlyRepaymentInterestHookImpl}).
 * <p>
 * Keyed 1:1 by the installment id (no separate generated id, no FK constraint added to the core table) so the original
 * charged amount can be restored when transactions are reprocessed/undone, and the row removed once the reduction no
 * longer applies.
 */
@Getter
@Entity
@Table(name = "crediblex_loan_installment_interest_snapshot")
@NoArgsConstructor
public class LoanInstallmentInterestSnapshot {

    @Id
    @Column(name = "loan_repayment_schedule_id")
    private Long loanRepaymentScheduleId;

    @Column(name = "interest_charged_original", scale = 6, precision = 19, nullable = false)
    private BigDecimal interestChargedOriginal;

    public LoanInstallmentInterestSnapshot(final Long loanRepaymentScheduleId, final BigDecimal interestChargedOriginal) {
        this.loanRepaymentScheduleId = loanRepaymentScheduleId;
        this.interestChargedOriginal = interestChargedOriginal;
    }
}
