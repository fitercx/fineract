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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;

/**
 * Shared utility class for extracting affected installments from loan transactions. This utility provides common
 * functionality used by both UI repayment services and standing instruction processing to ensure consistent webhook
 * payload structure.
 */
public final class LoanTransactionInstallmentUtils {

    /**
     * Installment status enum matching the status types used in CredibleX loan schedule display
     */
    public enum InstallmentStatus {
        DISBURSEMENT, SCHEDULED, DUE, OVERDUE, LATE_FEE_APPLIED, PAID, PARTIAL_PAID
    }

    private LoanTransactionInstallmentUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Utility method to extract affected installments from a loan transaction. This method provides detailed
     * information about which installments were affected by a transaction and what amounts were applied to each
     * component.
     *
     * Used by both UI repayments and standing instruction webhooks to ensure consistent payload structure across
     * different repayment flows.
     *
     * @param loan
     *            The loan entity
     * @param transaction
     *            The loan transaction with repayment schedule mappings
     * @return List of maps containing detailed installment information
     */
    public static List<Map<String, Object>> extractAffectedInstallments(Loan loan, LoanTransaction transaction) {
        List<Map<String, Object>> affectedInstallments = new ArrayList<>();

        if (transaction == null || transaction.getLoanTransactionToRepaymentScheduleMappings() == null) {
            return affectedInstallments;
        }

        // Use the transaction mappings to get exactly which installments were affected by this transaction
        transaction.getLoanTransactionToRepaymentScheduleMappings().forEach(mapping -> {
            LoanRepaymentScheduleInstallment installment = mapping.getLoanRepaymentScheduleInstallment();

            // Only include installments that had actual amounts applied in this transaction
            if (mapping.getAmount() != null && mapping.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> installmentData = new HashMap<>();
                installmentData.put("installmentNumber", installment.getInstallmentNumber());
                installmentData.put("dueDate", installment.getDueDate());

                // Current installment state (after this transaction)
                installmentData.put("principalDue", installment.getPrincipal(loan.getCurrency()).getAmount());
                installmentData.put("principalPaid", installment.getPrincipalCompleted(loan.getCurrency()).getAmount());
                installmentData.put("principalOutstanding", installment.getPrincipalOutstanding(loan.getCurrency()).getAmount());
                installmentData.put("interestDue", installment.getInterestCharged(loan.getCurrency()).getAmount());
                installmentData.put("interestPaid", installment.getInterestPaid(loan.getCurrency()).getAmount());
                installmentData.put("interestOutstanding", installment.getInterestOutstanding(loan.getCurrency()).getAmount());
                installmentData.put("feeChargesDue", installment.getFeeChargesCharged(loan.getCurrency()).getAmount());
                installmentData.put("feeChargesPaid", installment.getFeeChargesPaid(loan.getCurrency()).getAmount());
                installmentData.put("feeChargesOutstanding", installment.getFeeChargesOutstanding(loan.getCurrency()).getAmount());
                installmentData.put("penaltyChargesDue", installment.getPenaltyChargesCharged(loan.getCurrency()).getAmount());
                installmentData.put("penaltyChargesPaid", installment.getPenaltyChargesPaid(loan.getCurrency()).getAmount());
                installmentData.put("penaltyChargesOutstanding", installment.getPenaltyChargesOutstanding(loan.getCurrency()).getAmount());
                installmentData.put("totalOutstanding", installment.getTotalOutstanding(loan.getCurrency()).getAmount());
                installmentData.put("completed", installment.isObligationsMet());

                // Add the amounts that were specifically applied in this transaction
                installmentData.put("thisTransactionPrincipalPortion", mapping.getPrincipalPortion());
                installmentData.put("thisTransactionInterestPortion", mapping.getInterestPortion());
                installmentData.put("thisTransactionFeeChargesPortion", mapping.getFeeChargesPortion());
                installmentData.put("thisTransactionPenaltyChargesPortion", mapping.getPenaltyChargesPortion());
                installmentData.put("thisTransactionTotalAmount", mapping.getAmount());

                // Add installment status
                InstallmentStatus status = resolveInstallmentStatus(installment, loan);
                installmentData.put("status", status.name());

                affectedInstallments.add(installmentData);
            }
        });

        return affectedInstallments;
    }

    /**
     * Resolve the current status of an installment based on its payment state and due date. This logic matches the
     * status resolution used in CredibleX loan schedule display.
     *
     * @param installment
     *            The loan repayment schedule installment
     * @param loan
     *            The loan entity (for currency information)
     * @return The current status of the installment
     */
    private static InstallmentStatus resolveInstallmentStatus(LoanRepaymentScheduleInstallment installment, Loan loan) {
        // Delegate to generic resolver for consistency
        LoanStatusAggregationUtils.InstallmentStatus status = LoanStatusAggregationUtils.resolveInstallmentStatus(installment, loan);
        // Map resolver enum to local enum (same names)
        return InstallmentStatus.valueOf(status.name());
    }

    /**
     * Compute aggregate custom loan status from ALL installments of the loan.
     * This accounts for installments not affected by the current transaction.
     * Precedence:
     * - If any installment is LATE_FEE_APPLIED => PAST_MATURITY
     * - Else if any installment is OVERDUE => PAST_DUE
     * - Else => INVALID
     */
    public static CustomLoanStatus computeCustomLoanStatusForLoan(Loan loan) {
        // Delegate to generic resolver
        return LoanStatusAggregationUtils.computeCustomLoanStatusForLoan(loan);
    }
}
