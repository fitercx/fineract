package com.crediblex.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.crediblex.fineract.portfolio.loanaccount.data.ExtendedLoanSchedulePeriodData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CredibleXLoanPenaltyCalculator focusing on early repayment functionality for drawdown loans.
 */
class CredibleXLoanPenaltyCalculatorTest {

    private List<ExtendedLoanSchedulePeriodData> loanInstallments;
    private List<LoanChargeData> loanCharges;
    private long penaltyWaitPeriodValue;

    @BeforeEach
    void setUp() {
        penaltyWaitPeriodValue = 0L;
        loanCharges = new ArrayList<>();
        loanInstallments = new ArrayList<>();
    }

    @Test
    void testGetPrincipalDueForTransaction_ForDrawdownLoan_WithEarlyRepayment_ReturnsFirstInstallmentPrincipal() {
        // Given: A drawdown loan with installments and a transaction date before the first installment due date
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 5);
        LocalDate transactionDate = LocalDate.of(2025, 12, 23); // Before first installment
        BigDecimal expectedPrincipal = BigDecimal.valueOf(1000.00);

        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, expectedPrincipal,
                BigDecimal.valueOf(100.00), ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        loanInstallments.add(firstInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, true); // isDrawdownLoan = true

        // When: Getting principal due for transaction (indirectly tests resolveInstallmentByTransactionDate)
        BigDecimal result = calculator.getPrincipalDueForTransaction(transactionDate);

        // Then: Should return the first installment's principal
        assertEquals(expectedPrincipal, result);
    }

    @Test
    void testGetPrincipalDueForTransaction_ForNonDrawdownLoan_WithEarlyRepayment_ThrowsException() {
        // Given: A non-drawdown loan with installments and a transaction date before the first installment due date
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 5);
        LocalDate transactionDate = LocalDate.of(2025, 12, 23); // Before first installment

        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, BigDecimal.valueOf(1000.00),
                BigDecimal.valueOf(100.00), ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        loanInstallments.add(firstInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, false); // isDrawdownLoan = false

        // When/Then: Should throw exception for early repayment
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> calculator.getPrincipalDueForTransaction(transactionDate));

        assertNotNull(exception);
    }

    @Test
    void testGetInterestDueForTransaction_ForDrawdownLoan_WithEarlyRepayment_ReturnsFirstInstallmentInterest() {
        // Given: A drawdown loan with installments and a transaction date before the first installment due date
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 5);
        LocalDate transactionDate = LocalDate.of(2025, 12, 23); // Before first installment
        BigDecimal expectedInterest = BigDecimal.valueOf(100.00);

        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, BigDecimal.valueOf(1000.00),
                expectedInterest, ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        loanInstallments.add(firstInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, true); // isDrawdownLoan = true

        // When: Getting interest due for transaction
        BigDecimal result = calculator.getInterestDueForTransaction(transactionDate);

        // Then: Should return interest from the first installment
        assertEquals(expectedInterest, result);
    }

    @Test
    void testGetInterestDueForTransaction_ForNonDrawdownLoan_WithEarlyRepayment_ThrowsException() {
        // Given: A non-drawdown loan with installments and a transaction date before the first installment due date
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 5);
        LocalDate transactionDate = LocalDate.of(2025, 12, 23); // Before first installment

        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, BigDecimal.valueOf(1000.00),
                BigDecimal.valueOf(100.00), ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        loanInstallments.add(firstInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, false); // isDrawdownLoan = false

        // When/Then: Should throw exception for early repayment
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> calculator.getInterestDueForTransaction(transactionDate));

        assertNotNull(exception);
    }

    @Test
    void testCalculatePenaltySum_ForDrawdownLoan_WithEarlyRepayment_AllowsEarlyRepayment() {
        // Given: A drawdown loan with a transaction date before the first pending installment
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 5);
        LocalDate transactionDate = LocalDate.of(2025, 12, 23); // Before first installment

        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, BigDecimal.valueOf(1000.00),
                BigDecimal.valueOf(100.00), ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        loanInstallments.add(firstInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, true); // isDrawdownLoan = true

        // When: Calculating penalty sum
        BigDecimal result = calculator.calculatePenaltySum(transactionDate);

        // Then: Should not throw exception and return zero (no penalties)
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void testCalculatePenaltySum_ForNonDrawdownLoan_WithEarlyRepayment_ThrowsException() {
        // Given: A non-drawdown loan with a transaction date before the first pending installment
        // Use OVERDUE status so it's not filtered out by getFirstPendingInstallmentDate
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 5);
        LocalDate transactionDate = LocalDate.of(2025, 12, 23); // Before first installment

        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, BigDecimal.valueOf(1000.00),
                BigDecimal.valueOf(100.00), ExtendedLoanSchedulePeriodData.Status.OVERDUE);
        loanInstallments.add(firstInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, false); // isDrawdownLoan = false

        // When/Then: Should throw exception for early repayment
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> calculator.calculatePenaltySum(transactionDate));

        assertNotNull(exception);
    }

    @Test
    void testCalculateTotalOutstandingPrincipal_ForDrawdownLoan_WithEarlyRepayment_UsesFirstInstallmentDueDate() {
        // Given: A drawdown loan with installments and a transaction date before the first installment due date
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 5);
        LocalDate transactionDate = LocalDate.of(2025, 12, 23); // Before first installment
        BigDecimal expectedPrincipal = BigDecimal.valueOf(1000.00);

        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, expectedPrincipal,
                BigDecimal.valueOf(100.00), ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        loanInstallments.add(firstInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, true); // isDrawdownLoan = true

        // When: Calculating total outstanding principal
        BigDecimal result = calculator.calculateTotalOutstandingPrincipal(transactionDate);

        // Then: Should return principal from the first installment
        assertEquals(expectedPrincipal, result);
    }

    @Test
    void testCalculateTotalOutstandingInterest_ForDrawdownLoan_WithEarlyRepayment_UsesFirstInstallmentDueDate() {
        // Given: A drawdown loan with installments and a transaction date before the first installment due date
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 5);
        LocalDate transactionDate = LocalDate.of(2025, 12, 23); // Before first installment
        BigDecimal expectedInterest = BigDecimal.valueOf(100.00);

        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, BigDecimal.valueOf(1000.00),
                expectedInterest, ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        loanInstallments.add(firstInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, true); // isDrawdownLoan = true

        // When: Calculating total outstanding interest
        BigDecimal result = calculator.calculateTotalOutstandingInterest(transactionDate);

        // Then: Should return interest from the first installment
        assertEquals(expectedInterest, result);
    }

    @Test
    void testGetPrincipalDueForTransaction_ForDrawdownLoan_WithNormalRepayment_ReturnsCorrectInstallmentPrincipal() {
        // Given: A drawdown loan with multiple installments and a transaction date after the first installment
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 5);
        LocalDate secondInstallmentDueDate = LocalDate.of(2026, 2, 5);
        LocalDate transactionDate = LocalDate.of(2026, 1, 15); // Between first and second
        BigDecimal expectedPrincipal = BigDecimal.valueOf(1000.00);

        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, expectedPrincipal,
                BigDecimal.valueOf(100.00), ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        ExtendedLoanSchedulePeriodData secondInstallment = createInstallment(2, secondInstallmentDueDate, BigDecimal.valueOf(2000.00),
                BigDecimal.valueOf(200.00), ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        loanInstallments.add(firstInstallment);
        loanInstallments.add(secondInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, true); // isDrawdownLoan = true

        // When: Getting principal due for transaction (indirectly tests resolveInstallmentByTransactionDate)
        BigDecimal result = calculator.getPrincipalDueForTransaction(transactionDate);

        // Then: Should return the first installment's principal (transaction date is between first and second)
        assertEquals(expectedPrincipal, result);
    }

    @Test
    void testSequentialEarlyRepayments_ForDrawdownLoan_WithMultipleInstallments_ResolvesCorrectInstallment() {
        // Given: A drawdown loan with 3 installments
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 15);
        LocalDate secondInstallmentDueDate = LocalDate.of(2026, 2, 15);
        LocalDate thirdInstallmentDueDate = LocalDate.of(2026, 3, 15);

        BigDecimal firstInstallmentPrincipal = BigDecimal.valueOf(1000.00);
        BigDecimal firstInstallmentInterest = BigDecimal.valueOf(100.00);
        BigDecimal secondInstallmentPrincipal = BigDecimal.valueOf(2000.00);
        BigDecimal secondInstallmentInterest = BigDecimal.valueOf(200.00);
        BigDecimal thirdInstallmentPrincipal = BigDecimal.valueOf(3000.00);
        BigDecimal thirdInstallmentInterest = BigDecimal.valueOf(300.00);

        // Create installments - first one is PAID (after first early repayment)
        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, firstInstallmentPrincipal,
                firstInstallmentInterest, ExtendedLoanSchedulePeriodData.Status.PAID);
        ExtendedLoanSchedulePeriodData secondInstallment = createInstallment(2, secondInstallmentDueDate, secondInstallmentPrincipal,
                secondInstallmentInterest, ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        ExtendedLoanSchedulePeriodData thirdInstallment = createInstallment(3, thirdInstallmentDueDate, thirdInstallmentPrincipal,
                thirdInstallmentInterest, ExtendedLoanSchedulePeriodData.Status.SCHEDULED);

        loanInstallments.add(firstInstallment);
        loanInstallments.add(secondInstallment);
        loanInstallments.add(thirdInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, true); // isDrawdownLoan = true (all loans now)

        // When: Making an early repayment for Installment 2 (before its due date, after Installment 1 is paid)
        LocalDate secondEarlyRepaymentDate = LocalDate.of(2026, 1, 20); // After Installment 1 due date, before
                                                                        // Installment 2 due date

        BigDecimal principalResult = calculator.getPrincipalDueForTransaction(secondEarlyRepaymentDate);
        BigDecimal interestResult = calculator.getInterestDueForTransaction(secondEarlyRepaymentDate);

        // Then: Should return Installment 2's principal and interest (not Installment 1, even though date is in
        // Installment 1's period)
        assertEquals(secondInstallmentPrincipal, principalResult,
                "Should return Installment 2's principal, not Installment 1's (which is already PAID)");
        assertEquals(secondInstallmentInterest, interestResult,
                "Should return Installment 2's interest, not Installment 1's (which is already PAID)");
    }

    @Test
    void testSequentialEarlyRepayments_ForDrawdownLoan_WithThreeInstallments_AllowsThirdEarlyRepayment() {
        // Given: A drawdown loan with 3 installments, first two are PAID
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 15);
        LocalDate secondInstallmentDueDate = LocalDate.of(2026, 2, 15);
        LocalDate thirdInstallmentDueDate = LocalDate.of(2026, 3, 15);

        BigDecimal thirdInstallmentPrincipal = BigDecimal.valueOf(3000.00);
        BigDecimal thirdInstallmentInterest = BigDecimal.valueOf(300.00);

        // Create installments - first two are PAID
        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, BigDecimal.valueOf(1000.00),
                BigDecimal.valueOf(100.00), ExtendedLoanSchedulePeriodData.Status.PAID);
        ExtendedLoanSchedulePeriodData secondInstallment = createInstallment(2, secondInstallmentDueDate, BigDecimal.valueOf(2000.00),
                BigDecimal.valueOf(200.00), ExtendedLoanSchedulePeriodData.Status.PAID);
        ExtendedLoanSchedulePeriodData thirdInstallment = createInstallment(3, thirdInstallmentDueDate, thirdInstallmentPrincipal,
                thirdInstallmentInterest, ExtendedLoanSchedulePeriodData.Status.SCHEDULED);

        loanInstallments.add(firstInstallment);
        loanInstallments.add(secondInstallment);
        loanInstallments.add(thirdInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, true); // isDrawdownLoan = true (all loans now)

        // When: Making an early repayment for Installment 3 (before its due date, after Installments 1 & 2 are paid)
        LocalDate thirdEarlyRepaymentDate = LocalDate.of(2026, 2, 20); // After Installment 2 due date, before
                                                                       // Installment 3 due date

        BigDecimal principalResult = calculator.getPrincipalDueForTransaction(thirdEarlyRepaymentDate);
        BigDecimal interestResult = calculator.getInterestDueForTransaction(thirdEarlyRepaymentDate);

        // Then: Should return Installment 3's principal and interest
        assertEquals(thirdInstallmentPrincipal, principalResult,
                "Should return Installment 3's principal, skipping PAID Installments 1 & 2");
        assertEquals(thirdInstallmentInterest, interestResult, "Should return Installment 3's interest, skipping PAID Installments 1 & 2");
    }

    @Test
    void testEarlyRepayment_BeforeFirstInstallment_ReturnsFirstInstallment() {
        // Given: A drawdown loan with 3 installments, all SCHEDULED
        LocalDate firstInstallmentDueDate = LocalDate.of(2026, 1, 15);
        LocalDate secondInstallmentDueDate = LocalDate.of(2026, 2, 15);
        LocalDate thirdInstallmentDueDate = LocalDate.of(2026, 3, 15);

        BigDecimal firstInstallmentPrincipal = BigDecimal.valueOf(1000.00);
        BigDecimal firstInstallmentInterest = BigDecimal.valueOf(100.00);

        ExtendedLoanSchedulePeriodData firstInstallment = createInstallment(1, firstInstallmentDueDate, firstInstallmentPrincipal,
                firstInstallmentInterest, ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        ExtendedLoanSchedulePeriodData secondInstallment = createInstallment(2, secondInstallmentDueDate, BigDecimal.valueOf(2000.00),
                BigDecimal.valueOf(200.00), ExtendedLoanSchedulePeriodData.Status.SCHEDULED);
        ExtendedLoanSchedulePeriodData thirdInstallment = createInstallment(3, thirdInstallmentDueDate, BigDecimal.valueOf(3000.00),
                BigDecimal.valueOf(300.00), ExtendedLoanSchedulePeriodData.Status.SCHEDULED);

        loanInstallments.add(firstInstallment);
        loanInstallments.add(secondInstallment);
        loanInstallments.add(thirdInstallment);

        CredibleXLoanPenaltyCalculator calculator = new CredibleXLoanPenaltyCalculator(loanInstallments, loanCharges,
                penaltyWaitPeriodValue, true); // isDrawdownLoan = true (all loans now)

        // When: Making an early repayment before the first installment due date
        LocalDate earlyRepaymentDate = LocalDate.of(2026, 1, 10); // Before first installment due date

        BigDecimal principalResult = calculator.getPrincipalDueForTransaction(earlyRepaymentDate);
        BigDecimal interestResult = calculator.getInterestDueForTransaction(earlyRepaymentDate);

        // Then: Should return the first installment's principal and interest
        assertEquals(firstInstallmentPrincipal, principalResult,
                "Should return first installment's principal for early repayment before first due date");
        assertEquals(firstInstallmentInterest, interestResult,
                "Should return first installment's interest for early repayment before first due date");
    }

    /**
     * Helper method to create an ExtendedLoanSchedulePeriodData for testing.
     */
    private ExtendedLoanSchedulePeriodData createInstallment(Integer period, LocalDate dueDate, BigDecimal principalDue,
            BigDecimal interestOutstanding, ExtendedLoanSchedulePeriodData.Status status) {
        LoanSchedulePeriodData periodData = LoanSchedulePeriodData.builder().period(period).dueDate(dueDate).principalDue(principalDue)
                .interestOutstanding(interestOutstanding).principalOutstanding(principalDue).build();
        return new ExtendedLoanSchedulePeriodData(periodData, status);
    }
}
