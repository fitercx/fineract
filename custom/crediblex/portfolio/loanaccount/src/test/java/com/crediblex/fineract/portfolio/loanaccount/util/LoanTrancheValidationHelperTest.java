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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for LoanTrancheValidationHelper.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanTrancheValidationHelperTest {

    @Mock
    private Loan loan;

    @Mock
    private LoanProduct loanProduct;

    @Mock
    private LoanDisbursementDetails disbursementDetail1;

    @Mock
    private LoanDisbursementDetails disbursementDetail2;

    @Mock
    private LoanDisbursementDetails pendingDisbursementDetail;

    @BeforeEach
    void setUp() {
        when(loan.getLoanProduct()).thenReturn(loanProduct);
        when(loan.getId()).thenReturn(1L);
    }

    @Test
    void testHasActualMultipleTranches_SingleTrancheProduct_ReturnsFalse() {
        // Setup: Product doesn't allow multi-tranche
        when(loanProduct.isMultiDisburseLoan()).thenReturn(false);
        when(loan.getDisbursementDetails()).thenReturn(new ArrayList<>());

        // Execute
        boolean result = LoanTrancheValidationHelper.hasActualMultipleTranches(loan);

        // Verify
        assertFalse(result, "Single-tranche product should return false");
    }

    @Test
    void testHasActualMultipleTranches_SingleTrancheLoanUnderMultiTrancheProduct_ReturnsFalse() {
        // Setup: Product allows multi-tranche, but loan has only one fully disbursed tranche
        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        when(disbursementDetail1.actualDisbursementDate()).thenReturn(LocalDate.now());
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetail1));

        // Execute
        boolean result = LoanTrancheValidationHelper.hasActualMultipleTranches(loan);

        // Verify
        assertFalse(result, "Single-tranche loan under multi-tranche product should return false");
    }

    @Test
    void testHasActualMultipleTranches_MultipleTranches_ReturnsTrue() {
        // Setup: Product allows multi-tranche, loan has multiple disbursed tranches
        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        when(disbursementDetail1.actualDisbursementDate()).thenReturn(LocalDate.now());
        when(disbursementDetail2.actualDisbursementDate()).thenReturn(LocalDate.now().plusDays(30));
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetail1, disbursementDetail2));

        // Execute
        boolean result = LoanTrancheValidationHelper.hasActualMultipleTranches(loan);

        // Verify
        assertTrue(result, "Loan with multiple tranches should return true");
    }

    @Test
    void testHasActualMultipleTranches_PendingTranche_ReturnsTrue() {
        // Setup: Product allows multi-tranche, loan has one disbursed and one pending tranche
        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        when(disbursementDetail1.actualDisbursementDate()).thenReturn(LocalDate.now());
        when(pendingDisbursementDetail.actualDisbursementDate()).thenReturn(null); // Pending
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetail1, pendingDisbursementDetail));

        // Execute
        boolean result = LoanTrancheValidationHelper.hasActualMultipleTranches(loan);

        // Verify
        assertTrue(result, "Loan with pending tranche should return true");
    }

    @Test
    void testHasActualMultipleTranches_OnlyPendingTranche_ReturnsTrue() {
        // Setup: Product allows multi-tranche, loan has only pending tranche (not yet disbursed)
        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        when(pendingDisbursementDetail.actualDisbursementDate()).thenReturn(null); // Pending
        when(loan.getDisbursementDetails()).thenReturn(List.of(pendingDisbursementDetail));

        // Execute
        boolean result = LoanTrancheValidationHelper.hasActualMultipleTranches(loan);

        // Verify
        assertTrue(result, "Loan with pending tranche should return true");
    }

    @Test
    void testHasActualMultipleTranches_EmptyDisbursementDetails_ReturnsFalse() {
        // Setup: Product allows multi-tranche, but no disbursement details
        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        when(loan.getDisbursementDetails()).thenReturn(new ArrayList<>());

        // Execute
        boolean result = LoanTrancheValidationHelper.hasActualMultipleTranches(loan);

        // Verify
        assertFalse(result, "Loan with no disbursement details should return false");
    }

    @Test
    void testHasActualMultipleTranches_ThreeTranches_ReturnsTrue() {
        // Setup: Product allows multi-tranche, loan has three tranches
        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        LoanDisbursementDetails tranche3 = mock(LoanDisbursementDetails.class);
        when(disbursementDetail1.actualDisbursementDate()).thenReturn(LocalDate.now());
        when(disbursementDetail2.actualDisbursementDate()).thenReturn(LocalDate.now().plusDays(30));
        when(tranche3.actualDisbursementDate()).thenReturn(LocalDate.now().plusDays(60));
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetail1, disbursementDetail2, tranche3));

        // Execute
        boolean result = LoanTrancheValidationHelper.hasActualMultipleTranches(loan);

        // Verify
        assertTrue(result, "Loan with three tranches should return true");
    }

    @Test
    void testHasActualMultipleTranches_MixedDisbursedAndPending_ReturnsTrue() {
        // Setup: Product allows multi-tranche, loan has multiple tranches (some disbursed, some pending)
        when(loanProduct.isMultiDisburseLoan()).thenReturn(true);
        when(disbursementDetail1.actualDisbursementDate()).thenReturn(LocalDate.now());
        when(disbursementDetail2.actualDisbursementDate()).thenReturn(LocalDate.now().plusDays(30));
        when(pendingDisbursementDetail.actualDisbursementDate()).thenReturn(null); // Pending
        when(loan.getDisbursementDetails())
                .thenReturn(List.of(disbursementDetail1, disbursementDetail2, pendingDisbursementDetail));

        // Execute
        boolean result = LoanTrancheValidationHelper.hasActualMultipleTranches(loan);

        // Verify
        assertTrue(result, "Loan with mixed disbursed and pending tranches should return true");
    }
}
