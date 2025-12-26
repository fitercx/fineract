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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTrancheDisbursementCharge;

/**
 * Utility class for custom loan charge calculations in the CredibleX module.
 */
public class LoanChargeCalculationUtil {

    /**
     * Calculates the derived amount for a charge, with custom logic for multi-disbursement loans.
     * For multi-disbursement loans with DISBURSEMENT charges, calculates proportionally per tranche.
     *
     * @param loan
     *            the loan
     * @param loanCharge
     *            the loan charge
     * @return the derived amount for the charge
     */
    public static BigDecimal getDerivedAmountForCharge(final Loan loan, final LoanCharge loanCharge) {
        BigDecimal amount = BigDecimal.ZERO;
        if (loan.isMultiDisburmentLoan()
                && loanCharge.getCharge().getChargeTimeType().equals(ChargeTimeType.DISBURSEMENT.getValue())) {
            // For multi-disbursement loans, calculate fee proportionally per tranche
            // Check if charge is associated with a specific tranche
            LoanTrancheDisbursementCharge trancheCharge = loanCharge.getTrancheDisbursementCharge();
            if (trancheCharge != null) {
                // Charge is linked to a specific tranche - use that tranche's principal
                amount = trancheCharge.getLoanDisbursementDetails().principal();
            } else {
                // Charge not linked to tranche - find tranche by disbursement date and calculate proportionally
                LocalDate actualDisbursementDate = loan.getActualDisbursementDate(loanCharge);
                if (actualDisbursementDate != null) {
                    // Find the tranche that matches this disbursement date
                    Optional<LoanDisbursementDetails> matchingTranche = loan.getDisbursementDetails().stream()
                            .filter(detail -> actualDisbursementDate.equals(detail.actualDisbursementDate())).findFirst();

                    if (matchingTranche.isPresent()) {
                        // Use the matching tranche's principal for proportional calculation
                        // The fee percentage will be applied to this tranche amount, ensuring
                        // proportional allocation: (trancheAmount / sanctionedAmount) * totalFee
                        amount = matchingTranche.get().principal();
                    } else {
                        // Fallback: if no matching tranche found, use approved principal
                        // This should not happen in normal flow, but provides safety
                        amount = loan.getApprovedPrincipal();
                    }
                } else {
                    // No disbursement date yet - use approved principal as fallback
                    amount = loan.getApprovedPrincipal();
                }
            }
        } else {
            // If charge type is specified due date and loan is multi disburment loan.
            // Then we need to get as of this loan charge due date how much amount disbursed.
            if (loanCharge.isSpecifiedDueDate() && loan.isMultiDisburmentLoan()) {
                for (final LoanDisbursementDetails loanDisbursementDetails : loan.getDisbursementDetails()) {
                    if (!DateUtils.isAfter(loanDisbursementDetails.expectedDisbursementDate(), loanCharge.getDueDate())) {
                        amount = amount.add(loanDisbursementDetails.principal());
                    }
                }
            } else {
                amount = loan.getPrincipal().getAmount();
            }
        }
        return amount;
    }

    /**
     * Calculates the amount percentage applied to for a charge, with custom logic for multi-disbursement loans.
     * This is a custom implementation that extends the core logic.
     *
     * @param loan
     *            the loan
     * @param loanCharge
     *            the loan charge
     * @return the amount percentage applied to
     */
    public static BigDecimal calculateAmountPercentageAppliedTo(final Loan loan, final LoanCharge loanCharge) {
        if (loanCharge.isOverdueInstallmentCharge()) {
            return loanCharge.getAmountPercentageAppliedTo();
        }

        return switch (loanCharge.getChargeCalculation()) {
            case PERCENT_OF_AMOUNT -> getDerivedAmountForCharge(loan, loanCharge);
            case PERCENT_OF_AMOUNT_AND_INTEREST -> {
                final BigDecimal totalInterestCharged = loan.getTotalInterest();
                if (loan.isMultiDisburmentLoan() && loanCharge.isDisbursementCharge()) {
                    // For multi-disbursement loans, use tranche-specific amount if available
                    LoanTrancheDisbursementCharge trancheCharge = loanCharge.getTrancheDisbursementCharge();
                    if (trancheCharge != null) {
                        // Charge is linked to a specific tranche - use that tranche's principal
                        yield trancheCharge.getLoanDisbursementDetails().principal().add(totalInterestCharged);
                    } else {
                        // Find tranche by disbursement date for proportional calculation
                        LocalDate actualDisbursementDate = loan.getActualDisbursementDate(loanCharge);
                        if (actualDisbursementDate != null) {
                            Optional<LoanDisbursementDetails> matchingTranche = loan.getDisbursementDetails().stream()
                                    .filter(detail -> actualDisbursementDate.equals(detail.actualDisbursementDate())).findFirst();
                            if (matchingTranche.isPresent()) {
                                yield matchingTranche.get().principal().add(totalInterestCharged);
                            }
                        }
                        // Fallback to total of all tranches (for backward compatibility)
                        yield getTotalAllTrancheDisbursementAmount(loan).add(totalInterestCharged);
                    }
                } else {
                    yield loan.getPrincipal().getAmount().add(totalInterestCharged);
                }
            }
            case PERCENT_OF_INTEREST -> loan.getTotalInterest();
            case PERCENT_OF_DISBURSEMENT_AMOUNT -> {
                if (loanCharge.getTrancheDisbursementCharge() != null) {
                    yield loanCharge.getTrancheDisbursementCharge().getLoanDisbursementDetails().principal();
                } else {
                    yield loan.getPrincipal().getAmount();
                }
            }
            case INVALID, FLAT -> BigDecimal.ZERO;
        };
    }

    /**
     * Calculates the total of all tranche disbursement amounts.
     *
     * @param loan
     *            the loan
     * @return the total amount of all tranches
     */
    private static BigDecimal getTotalAllTrancheDisbursementAmount(final Loan loan) {
        BigDecimal amount = BigDecimal.ZERO;
        if (loan.isMultiDisburmentLoan()) {
            for (final LoanDisbursementDetails loanDisbursementDetail : loan.getDisbursementDetails()) {
                amount = amount.add(loanDisbursementDetail.principal());
            }
        }
        return amount;
    }
}

