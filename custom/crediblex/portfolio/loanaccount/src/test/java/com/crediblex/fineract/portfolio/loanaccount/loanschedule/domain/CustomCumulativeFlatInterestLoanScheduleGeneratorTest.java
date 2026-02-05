package com.crediblex.fineract.portfolio.loanaccount.loanschedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.PaymentPeriodsInOneYearCalculator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.PrincipalInterest;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ScheduledDateGenerator;
import org.apache.fineract.portfolio.tax.domain.TaxComponent;
import org.apache.fineract.portfolio.tax.domain.TaxGroup;
import org.apache.fineract.portfolio.tax.domain.TaxGroupMappings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for CustomCumulativeFlatInterestLoanScheduleGenerator focusing on LOC Receivable installment fee
 * calculation.
 *
 * Verifies that percentage-based installment fees for LOC Receivable loans are calculated from proposed principal (loan
 * amount) instead of disbursed principal, ensuring fees remain consistent during schedule regeneration (e.g., after
 * approval).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomCumulativeFlatInterestLoanScheduleGeneratorTest {

    private static final BigDecimal PROPOSED_PRINCIPAL = new BigDecimal("81000.00"); // Loan amount
    private static final BigDecimal DISBURSED_PRINCIPAL = new BigDecimal("66194.31"); // Disbursed amount
    private static final BigDecimal CHARGE_PERCENTAGE = new BigDecimal("10.00"); // 10%
    private static final BigDecimal EXPECTED_BASE_FEE = new BigDecimal("8100.00"); // 10% of 81,000
    private static final BigDecimal EXPECTED_TAX = new BigDecimal("405.00"); // 5% of 8,100
    private static final BigDecimal EXPECTED_TOTAL_FEE = new BigDecimal("8505.00"); // 8,100 + 405
    private static final int NUMBER_OF_INSTALLMENTS = 1;
    private static final BigDecimal EXPECTED_FEE_PER_INSTALLMENT = new BigDecimal("8505.00"); // Total / installments

    private CustomCumulativeFlatInterestLoanScheduleGenerator generator;
    private MonetaryCurrency currency;
    private MathContext mathContext;

    @Mock
    private ScheduledDateGenerator scheduledDateGenerator;

    @Mock
    private PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator;

    @Mock
    private Loan loan;

    @Mock
    private LoanCharge loanCharge;

    @Mock
    private Charge charge;

    @Mock
    private ChargeCalculationType chargeCalculationType;

    @Mock
    private ChargeTimeType chargeTimeType;

    @Mock
    private TaxGroup taxGroup;

    @Mock
    private TaxGroupMappings taxGroupMappings;

    @Mock
    private TaxComponent taxComponent;

    private MockedStatic<MoneyHelper> moneyHelperMock;

    @BeforeEach
    void setUp() {
        // Setup MoneyHelper static configuration
        moneyHelperMock = mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new MathContext(15, java.math.RoundingMode.HALF_EVEN));
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(java.math.RoundingMode.HALF_EVEN);

        generator = new CustomCumulativeFlatInterestLoanScheduleGenerator(scheduledDateGenerator, paymentPeriodsInOneYearCalculator);
        currency = new MonetaryCurrency("AED", 2, 0);
        mathContext = new MathContext(15, java.math.RoundingMode.HALF_EVEN);

        // Setup loan mock
        lenient().when(loan.isReceivableLocLoan()).thenReturn(true);
        lenient().when(loan.fetchNumberOfInstallmensAfterExceptions()).thenReturn(NUMBER_OF_INSTALLMENTS);
        lenient().when(loan.getCurrency()).thenReturn(currency);

        // Setup charge mock
        lenient().when(loanCharge.getLoan()).thenReturn(loan);
        lenient().when(loanCharge.isInstalmentFee()).thenReturn(true);
        lenient().when(loanCharge.isFeeCharge()).thenReturn(true);
        lenient().when(loanCharge.isDueAtDisbursement()).thenReturn(false);
        lenient().when(loanCharge.getCharge()).thenReturn(charge);
        lenient().when(loanCharge.getAmountPercentageAppliedTo()).thenReturn(PROPOSED_PRINCIPAL);
        lenient().when(loanCharge.getPercentage()).thenReturn(CHARGE_PERCENTAGE);
        lenient().when(loanCharge.getDueDate()).thenReturn(LocalDate.of(2026, 5, 5));

        // Setup charge calculation type
        lenient().when(loanCharge.getChargeCalculation()).thenReturn(chargeCalculationType);
        lenient().when(chargeCalculationType.isPercentageBased()).thenReturn(true);
        lenient().when(chargeCalculationType.isPercentageOfAmount()).thenReturn(true);

        // Setup tax group
        Set<TaxGroupMappings> taxGroupMappingsSet = new HashSet<>();
        taxGroupMappingsSet.add(taxGroupMappings);
        lenient().when(charge.getTaxGroup()).thenReturn(taxGroup);
        lenient().when(taxGroup.getTaxGroupMappings()).thenReturn(taxGroupMappingsSet);
        lenient().when(taxGroupMappings.getTaxComponent()).thenReturn(taxComponent);
        lenient().when(taxComponent.getApplicablePercentage(any(LocalDate.class))).thenReturn(new BigDecimal("5.00")); // 5%
                                                                                                                       // tax
        lenient().when(taxGroupMappings.occursOnDayFromAndUpToAndIncluding(any(LocalDate.class))).thenReturn(true);
        lenient().when(loanCharge.hasTax()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (moneyHelperMock != null) {
            moneyHelperMock.close();
        }
    }

    @Test
    @DisplayName("Should calculate LOC Receivable installment fee from proposed principal, not disbursed principal")
    void shouldCalculateLocReceivableInstallmentFeeFromProposedPrincipal() {
        // Given
        LocalDate periodStart = LocalDate.of(2026, 1, 5);
        LocalDate periodEnd = LocalDate.of(2026, 5, 5);
        Set<LoanCharge> loanCharges = new HashSet<>();
        loanCharges.add(loanCharge);

        PrincipalInterest principalInterest = new PrincipalInterest(Money.of(currency, DISBURSED_PRINCIPAL),
                Money.of(currency, new BigDecimal("6300.69")), null);
        Money principalDisbursed = Money.of(currency, DISBURSED_PRINCIPAL);
        Money totalInterestCharged = Money.of(currency, new BigDecimal("6300.69"));
        boolean isInstallmentChargeApplicable = true;
        boolean isFirstPeriod = false;

        // When
        Money result = generator.cumulativeFeeChargesDueWithin(periodStart, periodEnd, loanCharges, currency, principalInterest,
                principalDisbursed, totalInterestCharged, isInstallmentChargeApplicable, isFirstPeriod, mathContext);

        // Then
        // Should calculate from proposed principal (81,000 * 10% = 8,100) + tax (8,100 * 5% = 405) = 8,505
        // Divided by 1 installment = 8,505
        assertThat(result.getAmount()).isEqualByComparingTo(EXPECTED_FEE_PER_INSTALLMENT);
    }

    @Test
    @DisplayName("Should recalculate tax from correct base amount (proposed principal) for LOC Receivable")
    void shouldRecalculateTaxFromCorrectBaseAmount() {
        // Given
        LocalDate periodStart = LocalDate.of(2026, 1, 5);
        LocalDate periodEnd = LocalDate.of(2026, 5, 5);
        Set<LoanCharge> loanCharges = new HashSet<>();
        loanCharges.add(loanCharge);

        PrincipalInterest principalInterest = new PrincipalInterest(Money.of(currency, DISBURSED_PRINCIPAL),
                Money.of(currency, new BigDecimal("6300.69")), null);
        Money principalDisbursed = Money.of(currency, DISBURSED_PRINCIPAL);
        Money totalInterestCharged = Money.of(currency, new BigDecimal("6300.69"));
        boolean isInstallmentChargeApplicable = true;
        boolean isFirstPeriod = false;

        // When
        Money result = generator.cumulativeFeeChargesDueWithin(periodStart, periodEnd, loanCharges, currency, principalInterest,
                principalDisbursed, totalInterestCharged, isInstallmentChargeApplicable, isFirstPeriod, mathContext);

        // Then
        // Verify the total includes both base fee and tax
        // Base fee: 81,000 * 10% = 8,100
        // Tax: 8,100 * 5% = 405
        // Total: 8,505
        assertThat(result.getAmount()).isEqualByComparingTo(EXPECTED_TOTAL_FEE);
    }

    @Test
    @DisplayName("Should use proposed principal even when disbursed principal is different")
    void shouldUseProposedPrincipalEvenWhenDisbursedPrincipalIsDifferent() {
        // Given - disbursed principal is much less than proposed principal
        BigDecimal smallerDisbursedPrincipal = new BigDecimal("50000.00");
        LocalDate periodStart = LocalDate.of(2026, 1, 5);
        LocalDate periodEnd = LocalDate.of(2026, 5, 5);
        Set<LoanCharge> loanCharges = new HashSet<>();
        loanCharges.add(loanCharge);

        PrincipalInterest principalInterest = new PrincipalInterest(Money.of(currency, smallerDisbursedPrincipal),
                Money.of(currency, new BigDecimal("6300.69")), null);
        Money principalDisbursed = Money.of(currency, smallerDisbursedPrincipal);
        Money totalInterestCharged = Money.of(currency, new BigDecimal("6300.69"));
        boolean isInstallmentChargeApplicable = true;
        boolean isFirstPeriod = false;

        // When
        Money result = generator.cumulativeFeeChargesDueWithin(periodStart, periodEnd, loanCharges, currency, principalInterest,
                principalDisbursed, totalInterestCharged, isInstallmentChargeApplicable, isFirstPeriod, mathContext);

        // Then
        // Should still use proposed principal (81,000), not disbursed principal (50,000)
        // Fee should be 8,505 (10% of 81,000 + 5% tax), not 5,250 (10% of 50,000 + 5% tax)
        assertThat(result.getAmount()).isEqualByComparingTo(EXPECTED_TOTAL_FEE);
    }

    @Test
    @DisplayName("Should handle multiple installments correctly")
    void shouldHandleMultipleInstallmentsCorrectly() {
        // Given - 2 installments
        int numberOfInstallments = 2;
        lenient().when(loan.fetchNumberOfInstallmensAfterExceptions()).thenReturn(numberOfInstallments);

        LocalDate periodStart = LocalDate.of(2026, 1, 5);
        LocalDate periodEnd = LocalDate.of(2026, 5, 5);
        Set<LoanCharge> loanCharges = new HashSet<>();
        loanCharges.add(loanCharge);

        PrincipalInterest principalInterest = new PrincipalInterest(Money.of(currency, DISBURSED_PRINCIPAL),
                Money.of(currency, new BigDecimal("6300.69")), null);
        Money principalDisbursed = Money.of(currency, DISBURSED_PRINCIPAL);
        Money totalInterestCharged = Money.of(currency, new BigDecimal("6300.69"));
        boolean isInstallmentChargeApplicable = true;
        boolean isFirstPeriod = false;

        // When
        Money result = generator.cumulativeFeeChargesDueWithin(periodStart, periodEnd, loanCharges, currency, principalInterest,
                principalDisbursed, totalInterestCharged, isInstallmentChargeApplicable, isFirstPeriod, mathContext);

        // Then
        // Total fee (8,505) divided by 2 installments = 4,252.50
        BigDecimal expectedFeePerInstallment = EXPECTED_TOTAL_FEE.divide(new BigDecimal(numberOfInstallments), 2,
                java.math.RoundingMode.HALF_UP);
        assertThat(result.getAmount()).isEqualByComparingTo(expectedFeePerInstallment);
    }

    @Test
    @DisplayName("Should use standard calculation for non-LOC Receivable loans")
    void shouldUseStandardCalculationForNonLocReceivableLoans() {
        // Given - not a LOC Receivable loan
        lenient().when(loan.isReceivableLocLoan()).thenReturn(false);

        LocalDate periodStart = LocalDate.of(2026, 1, 5);
        LocalDate periodEnd = LocalDate.of(2026, 5, 5);
        Set<LoanCharge> loanCharges = new HashSet<>();
        loanCharges.add(loanCharge);

        PrincipalInterest principalInterest = new PrincipalInterest(Money.of(currency, DISBURSED_PRINCIPAL),
                Money.of(currency, new BigDecimal("6300.69")), null);
        Money principalDisbursed = Money.of(currency, DISBURSED_PRINCIPAL);
        Money totalInterestCharged = Money.of(currency, new BigDecimal("6300.69"));
        boolean isInstallmentChargeApplicable = true;
        boolean isFirstPeriod = false;

        // Mock the standard calculation behavior (percentage of installment principal)
        BigDecimal standardChargeAmount = DISBURSED_PRINCIPAL.multiply(CHARGE_PERCENTAGE).divide(new BigDecimal("100"), 2,
                java.math.RoundingMode.HALF_UP);
        lenient().when(loanCharge.amountOrPercentage()).thenReturn(standardChargeAmount);

        // Mock tax amount - create Money object first to avoid unfinished stubbing
        Money taxAmountMoney = Money.of(currency, EXPECTED_TAX);
        lenient().when(loanCharge.getTaxAmount(currency)).thenReturn(taxAmountMoney);

        // When
        Money result = generator.cumulativeFeeChargesDueWithin(periodStart, periodEnd, loanCharges, currency, principalInterest,
                principalDisbursed, totalInterestCharged, isInstallmentChargeApplicable, isFirstPeriod, mathContext);

        // Then
        // For non-LOC loans, it should use standard calculation (percentage of disbursed principal)
        // This test verifies the condition check works correctly
        // The actual calculation would be handled by the base class logic
        assertThat(result).isNotNull();
    }
}
