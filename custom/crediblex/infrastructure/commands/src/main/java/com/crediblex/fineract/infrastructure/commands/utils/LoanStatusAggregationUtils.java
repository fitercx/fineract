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
package com.crediblex.fineract.infrastructure.commands.utils;

import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * LoanStatusAggregationUtils: - resolveInstallmentStatus: derives the status for a single installment (used by
 * LoanTransactionInstallmentUtils when building affectedInstallments payloads). - computeCustomLoanStatusForLoan:
 * aggregates all installments of a loan to derive a loan-level custom status (used by repayment and disbursement flows
 * in CustomLoanWritePlatformServiceJpaRepositoryImpl). This utility is transaction-agnostic and safe to use anywhere
 * the schedule exists (repayment, disbursement, schedule updates).
 */
public final class LoanStatusAggregationUtils {

    private LoanStatusAggregationUtils() {}

    public enum InstallmentStatus {
        DISBURSEMENT, SCHEDULED, DUE, OVERDUE, LATE_FEE_APPLIED, PAID, PARTIAL_PAID
    }

    /**
     * Resolve installment status based on payment state and dates.
     */
    public static InstallmentStatus resolveInstallmentStatus(LoanRepaymentScheduleInstallment installment, Loan loan) {
        if (installment.isObligationsMet()) {
            return InstallmentStatus.PAID;
        }
        if (installment.getPenaltyChargesOutstanding(loan.getCurrency()).isGreaterThanZero()) {
            return InstallmentStatus.LATE_FEE_APPLIED;
        }
        boolean hasOutstanding = installment.getTotalOutstanding(loan.getCurrency()).isGreaterThanZero();
        boolean hasPaidAmount = installment.getPrincipalCompleted(loan.getCurrency()).isGreaterThanZero()
                || installment.getInterestPaid(loan.getCurrency()).isGreaterThanZero()
                || installment.getFeeChargesPaid(loan.getCurrency()).isGreaterThanZero();
        LocalDate currentDate = DateUtils.getLocalDateOfTenant();
        if (installment.getDueDate().isBefore(currentDate)) {
            return InstallmentStatus.OVERDUE;
        }
        if (hasOutstanding && hasPaidAmount) {
            return InstallmentStatus.PARTIAL_PAID;
        }
        if (installment.getDueDate().equals(currentDate)) {
            return InstallmentStatus.DUE;
        }
        return InstallmentStatus.SCHEDULED;
    }

    /**
     * Compute aggregate custom loan status from ALL installments of the loan which are due on or before today. Future
     * installments (due date after today) are ignored to avoid false PAST_MATURITY from future penalties. Precedence: -
     * If any installment is LATE_FEE_APPLIED => PAST_MATURITY - Else if any installment is OVERDUE => PAST_DUE - Else
     * => INVALID
     */
    public static CustomLoanStatus computeCustomLoanStatusForLoan(Loan loan) {
        if (loan == null || loan.getRepaymentScheduleInstallments() == null) {
            return CustomLoanStatus.INVALID;
        }
        LocalDate today = DateUtils.getLocalDateOfTenant();
        boolean hasOverdue = false;
        for (LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            // Only consider installments due on or before today
            if (installment.getDueDate().isAfter(today)) {
                continue;
            }
            InstallmentStatus status = resolveInstallmentStatus(installment, loan);
            if (status == InstallmentStatus.LATE_FEE_APPLIED) {
                return CustomLoanStatus.PAST_MATURITY;
            }
            if (status == InstallmentStatus.OVERDUE) {
                hasOverdue = true;
            }
        }
        return hasOverdue ? CustomLoanStatus.PAST_DUE : CustomLoanStatus.INVALID;
    }
}
