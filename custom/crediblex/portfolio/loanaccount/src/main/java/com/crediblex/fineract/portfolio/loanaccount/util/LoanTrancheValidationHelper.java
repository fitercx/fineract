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

import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;

/**
 * Helper utility to determine if a loan actually has multiple tranches
 * or pending undisbursed tranches, regardless of product setting.
 *
 * This fixes the issue where single-tranche loans under multi-tranche products
 * are incorrectly treated as multi-tranche loans for validation purposes.
 */
@Slf4j
public class LoanTrancheValidationHelper {

    /**
     * Checks if the loan actually has multiple tranches or pending undisbursed tranches.
     *
     * For repayment validation purposes, we should only apply multi-tranche validation
     * when the loan actually has multiple tranches or pending tranches, not just when
     * the product allows multi-tranche.
     *
     * @param loan The loan to check
     * @return true if the loan has multiple tranches or pending tranches, false otherwise
     */
    public static boolean hasActualMultipleTranches(Loan loan) {
        // If product doesn't allow multi-tranche, definitely not a multi-tranche loan
        if (!loan.getLoanProduct().isMultiDisburseLoan()) {
            return false;
        }

        // Check if loan has multiple disbursement details (actual multiple tranches)
        long disbursementDetailsCount = loan.getDisbursementDetails().size();
        if (disbursementDetailsCount > 1) {
            log.debug("Loan {} has {} disbursement details - treating as multi-tranche", loan.getId(),
                    disbursementDetailsCount);
            return true;
        }

        // Check if there are pending undisbursed tranches
        boolean hasPendingTranches = loan.getDisbursementDetails().stream()
                .anyMatch(detail -> detail.actualDisbursementDate() == null);

        if (hasPendingTranches) {
            log.debug("Loan {} has pending undisbursed tranches - treating as multi-tranche", loan.getId());
            return true;
        }

        // Single tranche, fully disbursed - not a multi-tranche loan for validation purposes
        log.debug("Loan {} is single-tranche (fully disbursed) - skipping multi-tranche validation", loan.getId());
        return false;
    }
}

