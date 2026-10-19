package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PenaltyWaiveUnrecognizedIncomeTest {

    @Mock
    private LoanCharge loanCharge;

    @Mock
    private Money unrecognized;

    @Mock
    private Money zero;

    @Mock
    private LoanTransaction waiveTransaction;

    @Test
    void penaltyWaivePassesZeroAsFactoryTaxSlot() {
        when(loanCharge.isPenaltyCharge()).thenReturn(true);
        when(unrecognized.zero()).thenReturn(zero);

        assertSame(zero, PenaltyWaiveUnrecognizedIncome.thirdArgumentForWaiveFactory(loanCharge, unrecognized));
    }

    @Test
    void feeWaiveKeepsUnrecognizedInFactoryTaxSlot() {
        when(loanCharge.isPenaltyCharge()).thenReturn(false);

        assertSame(unrecognized, PenaltyWaiveUnrecognizedIncome.thirdArgumentForWaiveFactory(loanCharge, unrecognized));
    }

    @Test
    void recordsUnrecognizedIncomeOnlyForPenaltyWaive() {
        when(loanCharge.isPenaltyCharge()).thenReturn(true);
        when(unrecognized.isGreaterThanZero()).thenReturn(true);
        when(unrecognized.zero()).thenReturn(zero);

        PenaltyWaiveUnrecognizedIncome.recordOnPenaltyWaive(waiveTransaction, loanCharge, unrecognized);

        verify(waiveTransaction).updateUnrecognizedChargesComponents(zero, zero, unrecognized);
    }

    @Test
    void doesNotTouchFeeWaiveUnrecognizedPath() {
        when(loanCharge.isPenaltyCharge()).thenReturn(false);

        PenaltyWaiveUnrecognizedIncome.recordOnPenaltyWaive(waiveTransaction, loanCharge, unrecognized);

        verify(waiveTransaction, never()).updateUnrecognizedChargesComponents(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
