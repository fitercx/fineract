package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.Test;

class OverdueChargeScheduleAllocationUtilsTest {

    @Test
    void keepsLpiEffectiveOnDueDateInThatEmi() {
        final LoanRepaymentScheduleInstallment june = installment(1, LocalDate.of(2026, 5, 21), LocalDate.of(2026, 6, 22));
        final LoanRepaymentScheduleInstallment july = installment(2, LocalDate.of(2026, 6, 22), LocalDate.of(2026, 7, 21));

        assertThat(OverdueChargeScheduleAllocationUtils.resolveInstallmentNumber(LocalDate.of(2026, 6, 22), List.of(june, july)))
                .isEqualTo(1);
    }

    @Test
    void movesLaterDailyLpiIntoTheNextEmiWindow() {
        final LoanRepaymentScheduleInstallment june = installment(1, LocalDate.of(2026, 5, 21), LocalDate.of(2026, 6, 22));
        final LoanRepaymentScheduleInstallment july = installment(2, LocalDate.of(2026, 6, 22), LocalDate.of(2026, 7, 21));

        assertThat(OverdueChargeScheduleAllocationUtils.resolveInstallmentNumber(LocalDate.of(2026, 6, 23), List.of(june, july)))
                .isEqualTo(2);
        assertThat(OverdueChargeScheduleAllocationUtils.resolveInstallmentNumber(LocalDate.of(2026, 7, 21), List.of(june, july)))
                .isEqualTo(2);
    }

    @Test
    void usesGeneratedPostMaturityInstallmentForLaterLpi() {
        final LoanRepaymentScheduleInstallment maturity = installment(1, LocalDate.of(2026, 2, 27), LocalDate.of(2026, 7, 27));
        final LoanRepaymentScheduleInstallment generated = installment(2, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 23));

        assertThat(OverdueChargeScheduleAllocationUtils.resolveInstallmentNumber(LocalDate.of(2026, 7, 27), List.of(maturity, generated)))
                .isEqualTo(1);
        assertThat(OverdueChargeScheduleAllocationUtils.resolveInstallmentNumber(LocalDate.of(2026, 7, 28), List.of(maturity, generated)))
                .isEqualTo(2);
    }

    @Test
    void leavesDateBeforeFirstScheduleWindowUnresolved() {
        final LoanRepaymentScheduleInstallment june = installment(1, LocalDate.of(2026, 5, 21), LocalDate.of(2026, 6, 22));

        assertThat(OverdueChargeScheduleAllocationUtils.resolveInstallmentNumber(LocalDate.of(2026, 5, 20), List.of(june))).isNull();
    }

    private LoanRepaymentScheduleInstallment installment(final int number, final LocalDate fromDate, final LocalDate dueDate) {
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getInstallmentNumber()).thenReturn(number);
        when(installment.getFromDate()).thenReturn(fromDate);
        when(installment.getDueDate()).thenReturn(dueDate);
        return installment;
    }
}
