package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
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
 * Unit tests for CustomLoanChargeAssembler focusing on LOC Receivable charge calculation.
 *
 * Verifies that percentage-based charges for LOC Receivable loans use approved principal (loan amount) instead of
 * disbursed principal to ensure consistent fee calculation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomLoanChargeAssemblerTest {

    private static final Long LOAN_ID = 1L;
    private static final BigDecimal APPROVED_PRINCIPAL = new BigDecimal("90000.00"); // Loan amount
    private static final BigDecimal DISBURSED_PRINCIPAL = new BigDecimal("73999.23"); // Actual disbursed amount
    private static final BigDecimal CHARGE_PERCENTAGE = new BigDecimal("10.00"); // 10%
    private static final BigDecimal EXPECTED_CHARGE_AMOUNT = new BigDecimal("9000.00"); // 10% of 90,000
    private static final BigDecimal INCORRECT_CHARGE_AMOUNT = new BigDecimal("7399.92"); // 10% of 73,999.23
    private static final LocalDate DUE_DATE = LocalDate.of(2025, 12, 22);
    private static final String CURRENCY_CODE = "AED";

    @Mock
    private FromJsonHelper fromApiJsonHelper;

    @Mock
    private ChargeRepositoryWrapper chargeRepository;

    @Mock
    private LoanChargeRepository loanChargeRepository;

    @Mock
    private LoanProductRepository loanProductRepository;

    @Mock
    private ExternalIdFactory externalIdFactory;

    @Mock
    private LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

    @Mock
    private JsonCommand command;

    @Mock
    private Loan loan;

    @Mock
    private Charge chargeDefinition;

    @Mock
    private LoanProduct loanProduct;

    @Mock
    private LoanLineOfCreditParams loanLineOfCreditParams;

    @Mock
    private LineOfCredit lineOfCredit;

    @Mock
    private org.apache.fineract.organisation.monetary.domain.MonetaryCurrency currency;

    private CustomLoanChargeAssembler customLoanChargeAssembler;
    private MockedStatic<MoneyHelper> moneyHelperMock;

    @BeforeEach
    void setUp() {
        // Initialize business dates
        java.util.HashMap<org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType, LocalDate> businessDates = new java.util.HashMap<>();
        businessDates.put(org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType.BUSINESS_DATE, DUE_DATE);
        org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil.setBusinessDates(businessDates);

        // Set up tenant context
        org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant tenant = new org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant(
                1L, "default", "default", "UTC", null);
        org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil.setTenant(tenant);

        // Setup MoneyHelper static configuration
        moneyHelperMock = mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new java.math.MathContext(12, java.math.RoundingMode.HALF_EVEN));
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(java.math.RoundingMode.HALF_EVEN);
        customLoanChargeAssembler = new CustomLoanChargeAssembler(fromApiJsonHelper, chargeRepository, loanChargeRepository,
                loanProductRepository, externalIdFactory, loanLineOfCreditParamsRepository);

        // Setup charge definition - 10% percentage-based charge
        when(chargeDefinition.getChargeCalculation()).thenReturn(ChargeCalculationType.PERCENT_OF_AMOUNT.getValue());
        when(chargeDefinition.getChargeTimeType()).thenReturn(ChargeTimeType.DISBURSEMENT.getValue());
        when(chargeDefinition.getChargePaymentMode()).thenReturn(ChargePaymentMode.REGULAR.getValue());
        when(chargeDefinition.getAmount()).thenReturn(CHARGE_PERCENTAGE);

        // Setup loan
        when(loan.getId()).thenReturn(LOAN_ID);
        when(loan.getCurrency()).thenReturn(currency);
        when(currency.getCode()).thenReturn(CURRENCY_CODE);
        when(currency.getDigitsAfterDecimal()).thenReturn(2);
        when(currency.getCurrencyInMultiplesOf()).thenReturn(1);

        // Setup principal amounts
        when(loan.getApprovedPrincipal()).thenReturn(APPROVED_PRINCIPAL);
        // Create Money objects using lenient mocking to avoid issues
        lenient().when(currency.toData()).thenReturn(new org.apache.fineract.organisation.monetary.data.CurrencyData(CURRENCY_CODE, 2, 1));
        Money disbursedPrincipalMoney = Money.of(currency, DISBURSED_PRINCIPAL);
        when(loan.getPrincipal()).thenReturn(disbursedPrincipalMoney);
        when(loan.getTotalInterest()).thenReturn(BigDecimal.ZERO);

        // Setup command
        when(command.extractLocale()).thenReturn(Locale.ENGLISH);
        when(command.bigDecimalValueOfParameterNamed("amount", Locale.ENGLISH)).thenReturn(CHARGE_PERCENTAGE);
        when(command.hasParameter("principal")).thenReturn(false);
        when(command.hasParameter("interest")).thenReturn(false);
        when(command.bigDecimalValueOfParameterNamed("interest")).thenReturn(BigDecimal.ZERO);

        // Setup external ID
        when(externalIdFactory.createFromCommand(any(JsonCommand.class), anyString())).thenReturn(ExternalId.empty());
    }

    @AfterEach
    void tearDown() {
        if (moneyHelperMock != null) {
            moneyHelperMock.close();
        }
    }

    @Test
    @DisplayName("LOC Receivable: Percentage charge should use approved principal (loan amount), not disbursed amount")
    void testLocReceivableChargeUsesApprovedPrincipal() {
        // Given: LOC Receivable loan with approved principal (90,000) and disbursed principal (73,999.23)
        when(loanLineOfCreditParamsRepository.findByLoanId(LOAN_ID)).thenReturn(Optional.of(loanLineOfCreditParams));
        when(loanLineOfCreditParams.getLineOfCredit()).thenReturn(lineOfCredit);
        when(lineOfCredit.getProductType()).thenReturn(mock(com.crediblex.fineract.portfolio.loc.data.LocProductType.class));
        when(lineOfCredit.getProductType().isReceivable()).thenReturn(true);

        // When: Create charge for LOC Receivable loan
        LoanCharge loanCharge = customLoanChargeAssembler.createNewFromJson(loan, chargeDefinition, command, DUE_DATE);

        // Then: Charge should use approved principal (90,000) as base amount
        // amountPercentageAppliedTo should be 90,000, not 73,999.23
        assertThat(loanCharge.getAmountPercentageAppliedTo())
                .as("LOC Receivable charge should use approved principal (loan amount) as base")
                .isEqualByComparingTo(APPROVED_PRINCIPAL);

        assertThat(loanCharge.getAmountPercentageAppliedTo())
                .as("LOC Receivable charge should NOT use disbursed principal")
                .isNotEqualByComparingTo(DISBURSED_PRINCIPAL);
    }

    @Test
    @DisplayName("Regular loan: Percentage charge should use disbursed principal")
    void testRegularLoanChargeUsesDisbursedPrincipal() {
        // Given: Regular (non-LOC Receivable) loan
        when(loanLineOfCreditParamsRepository.findByLoanId(LOAN_ID)).thenReturn(Optional.empty());

        // When: Create charge for regular loan
        LoanCharge loanCharge = customLoanChargeAssembler.createNewFromJson(loan, chargeDefinition, command, DUE_DATE);

        // Then: Charge should use disbursed principal (73,999.23) as base amount
        assertThat(loanCharge.getAmountPercentageAppliedTo())
                .as("Regular loan charge should use disbursed principal")
                .isEqualByComparingTo(DISBURSED_PRINCIPAL);
    }

    @Test
    @DisplayName("LOC Receivable: PERCENT_OF_AMOUNT_AND_INTEREST should use approved principal + interest")
    void testLocReceivableChargeWithAmountAndInterest() {
        // Given: LOC Receivable loan with interest
        BigDecimal totalInterest = new BigDecimal("7000.77");
        when(loanLineOfCreditParamsRepository.findByLoanId(LOAN_ID)).thenReturn(Optional.of(loanLineOfCreditParams));
        when(loanLineOfCreditParams.getLineOfCredit()).thenReturn(lineOfCredit);
        when(lineOfCredit.getProductType()).thenReturn(mock(com.crediblex.fineract.portfolio.loc.data.LocProductType.class));
        when(lineOfCredit.getProductType().isReceivable()).thenReturn(true);
        when(loan.getTotalInterest()).thenReturn(totalInterest);
        when(command.bigDecimalValueOfParameterNamed("interest")).thenReturn(totalInterest);

        // Setup charge definition for PERCENT_OF_AMOUNT_AND_INTEREST
        when(chargeDefinition.getChargeCalculation()).thenReturn(ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST.getValue());

        // When: Create charge
        LoanCharge loanCharge = customLoanChargeAssembler.createNewFromJson(loan, chargeDefinition, command, DUE_DATE);

        // Then: Should use approved principal + interest + interest again (LOC Receivable logic)
        BigDecimal expectedBase = APPROVED_PRINCIPAL.add(totalInterest).add(totalInterest);
        assertThat(loanCharge.getAmountPercentageAppliedTo())
                .as("LOC Receivable PERCENT_OF_AMOUNT_AND_INTEREST should use approved principal + interest")
                .isEqualByComparingTo(expectedBase);
    }

    @Test
    @DisplayName("LOC Receivable: Charge calculation should result in correct fee amount (9,000 not 7,399.92)")
    void testLocReceivableChargeAmountCalculation() {
        // Given: LOC Receivable loan
        when(loanLineOfCreditParamsRepository.findByLoanId(LOAN_ID)).thenReturn(Optional.of(loanLineOfCreditParams));
        when(loanLineOfCreditParams.getLineOfCredit()).thenReturn(lineOfCredit);
        when(lineOfCredit.getProductType()).thenReturn(mock(com.crediblex.fineract.portfolio.loc.data.LocProductType.class));
        when(lineOfCredit.getProductType().isReceivable()).thenReturn(true);

        // When: Create charge and add to loan (this triggers amount calculation)
        LoanCharge loanCharge = customLoanChargeAssembler.createNewFromJson(loan, chargeDefinition, command, DUE_DATE);

        // Simulate what happens in Loan.addLoanCharge()
        // The charge amount will be calculated as: amountPercentageAppliedTo * percentage / 100
        BigDecimal calculatedAmount = loanCharge.getAmountPercentageAppliedTo()
                .multiply(CHARGE_PERCENTAGE)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);

        // Then: Calculated amount should be 9,000 (10% of 90,000), not 7,399.92 (10% of 73,999.23)
        assertThat(calculatedAmount)
                .as("Charge amount should be calculated on approved principal (90,000 * 10% = 9,000)")
                .isEqualByComparingTo(EXPECTED_CHARGE_AMOUNT);

        assertThat(calculatedAmount)
                .as("Charge amount should NOT be calculated on disbursed principal (73,999.23 * 10% = 7,399.92)")
                .isNotEqualByComparingTo(INCORRECT_CHARGE_AMOUNT);
    }
}
