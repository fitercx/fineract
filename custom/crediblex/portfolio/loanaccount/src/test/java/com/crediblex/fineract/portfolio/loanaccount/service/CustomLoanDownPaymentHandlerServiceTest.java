package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.junit.jupiter.api.Test;

class CustomLoanDownPaymentHandlerServiceTest {

    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 10, 8);

    @Test
    void laterAccrualDoesNotMakeABackdatedRepaymentNonLatest() {
        final Loan loan = loanWith(disbursement(LocalDate.of(2026, 8, 16)), repayment(1L, LocalDate.of(2026, 10, 1)),
                accrual(LocalDate.of(2026, 10, 14)));

        assertThat(CustomLoanDownPaymentHandlerService.isChronologicallyLatestIgnoringAccruals(loan, newRepayment())).isTrue();
    }

    @Test
    void laterAccrualAdjustmentDoesNotMakeABackdatedRepaymentNonLatest() {
        final Loan loan = loanWith(repayment(1L, LocalDate.of(2026, 10, 1)), accrualAdjustment(LocalDate.of(2026, 10, 14)));

        assertThat(CustomLoanDownPaymentHandlerService.isChronologicallyLatestIgnoringAccruals(loan, newRepayment())).isTrue();
    }

    @Test
    void laterRepaymentStillForcesReplay() {
        final Loan loan = loanWith(accrual(LocalDate.of(2026, 10, 14)), repayment(1L, LocalDate.of(2026, 10, 10)));

        assertThat(CustomLoanDownPaymentHandlerService.isChronologicallyLatestIgnoringAccruals(loan, newRepayment())).isFalse();
    }

    @Test
    void laterWaiverStillForcesReplay() {
        final LoanTransaction waiver = mock(LoanTransaction.class);
        when(waiver.getTransactionDate()).thenReturn(LocalDate.of(2026, 10, 12));
        when(waiver.getId()).thenReturn(5L);

        assertThat(CustomLoanDownPaymentHandlerService.isChronologicallyLatestIgnoringAccruals(loanWith(waiver), newRepayment())).isFalse();
    }

    @Test
    void reversedLaterRepaymentIsIgnored() {
        final LoanTransaction reversed = repayment(1L, LocalDate.of(2026, 10, 10));
        when(reversed.isReversed()).thenReturn(true);

        assertThat(CustomLoanDownPaymentHandlerService.isChronologicallyLatestIgnoringAccruals(loanWith(reversed), newRepayment()))
                .isTrue();
    }

    @Test
    void savedRepaymentOnTheSameDateIsTreatedAsEarlier() {
        final Loan loan = loanWith(repayment(1L, PAYMENT_DATE));

        assertThat(CustomLoanDownPaymentHandlerService.isChronologicallyLatestIgnoringAccruals(loan, newRepayment())).isTrue();
    }

    @Test
    void unsavedRepaymentOnTheSameDateForcesReplay() {
        final Loan loan = loanWith(repayment(null, PAYMENT_DATE));

        assertThat(CustomLoanDownPaymentHandlerService.isChronologicallyLatestIgnoringAccruals(loan, newRepayment())).isFalse();
    }

    @Test
    void interestRecalculationLoanKeepsTheCoreRule() {
        final Loan loan = loanWith(accrual(LocalDate.of(2026, 10, 14)));
        when(loan.isInterestBearingAndInterestRecalculationEnabled()).thenReturn(true);
        final LoanTransaction payment = newRepayment();
        when(loan.isChronologicallyLatestRepaymentOrWaiver(payment)).thenReturn(false);

        assertThat(CustomLoanDownPaymentHandlerService.isChronologicallyLatestIgnoringAccruals(loan, payment)).isFalse();
        verify(loan).isChronologicallyLatestRepaymentOrWaiver(payment);
    }

    private static Loan loanWith(final LoanTransaction... transactions) {
        final Loan loan = mock(Loan.class);
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>(List.of(transactions)));
        return loan;
    }

    private static LoanTransaction newRepayment() {
        final LoanTransaction transaction = mock(LoanTransaction.class);
        when(transaction.getTransactionDate()).thenReturn(PAYMENT_DATE);
        return transaction;
    }

    private static LoanTransaction disbursement(final LocalDate date) {
        final LoanTransaction transaction = mock(LoanTransaction.class);
        when(transaction.getTransactionDate()).thenReturn(date);
        when(transaction.isDisbursement()).thenReturn(true);
        return transaction;
    }

    private static LoanTransaction repayment(final Long id, final LocalDate date) {
        final LoanTransaction transaction = mock(LoanTransaction.class);
        when(transaction.getTransactionDate()).thenReturn(date);
        when(transaction.getId()).thenReturn(id);
        return transaction;
    }

    private static LoanTransaction accrual(final LocalDate date) {
        final LoanTransaction transaction = repayment(9L, date);
        when(transaction.isAccrual()).thenReturn(true);
        return transaction;
    }

    private static LoanTransaction accrualAdjustment(final LocalDate date) {
        final LoanTransaction transaction = repayment(10L, date);
        when(transaction.isAccrualAdjustment()).thenReturn(true);
        return transaction;
    }
}
