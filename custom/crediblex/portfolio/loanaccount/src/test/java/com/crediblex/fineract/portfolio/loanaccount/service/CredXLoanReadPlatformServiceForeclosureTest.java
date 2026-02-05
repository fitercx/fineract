package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.account.domain.AccountAssociationsRepository;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanForeclosureValidator;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for foreclosure template retrieval for Factor Rate loans.
 *
 * Verifies that fees and taxes are correctly included in foreclosure template when they exist in loan summary but not
 * in installment schedule.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CredXLoanReadPlatformServiceForeclosureTest {

    private static final Long LOAN_ID = 1882L;
    private static final LocalDate FORECLOSURE_DATE = LocalDate.of(2026, 1, 6);
    private static final String CURRENCY_CODE = "AED";
    private static final BigDecimal PRINCIPAL_OUTSTANDING = new BigDecimal("83533.34");
    private static final BigDecimal FEE_CHARGES_OUTSTANDING = new BigDecimal("9333.24");
    private static final BigDecimal TAX_CHARGES_OUTSTANDING = new BigDecimal("466.76");
    private static final BigDecimal INTEREST_OUTSTANDING = BigDecimal.ZERO;
    private static final BigDecimal PENALTY_OUTSTANDING = BigDecimal.ZERO;

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;

    @Mock
    private ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;

    @Mock
    private LoanForeclosureValidator loanForeclosureValidator;

    @Mock
    private LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

    @Mock
    private AccountAssociationsRepository accountAssociationsRepository;

    @Mock
    private PaymentTypeReadPlatformService paymentTypeReadPlatformService;

    @Mock
    private ApplicationCurrency applicationCurrency;

    @Mock
    private ConfigurationDomainService configurationDomainService;

    @Mock
    private Loan loan;

    @Mock
    private LoanSummary loanSummary;

    @Mock
    private LoanRepaymentScheduleInstallment foreclosureDetail;

    @Mock
    private MonetaryCurrency currency;

    @InjectMocks
    private CredXLoanReadPlatformServiceImpl credXLoanReadPlatformService;

    @BeforeEach
    void setUp() {
        // Initialize business dates
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.now());
        ThreadLocalContextUtil.setBusinessDates(businessDates);

        // Set up tenant context
        FineractPlatformTenant tenant = new FineractPlatformTenant(1L, "default", "default", "UTC", null);
        ThreadLocalContextUtil.setTenant(tenant);

        // Initialize MoneyHelper first
        MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", configurationDomainService);
        moneyHelper.initialize();

        // Mock configuration domain service for rounding mode
        when(configurationDomainService.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);

        // Setup currency with all required methods
        when(currency.getCode()).thenReturn(CURRENCY_CODE);
        when(currency.getDigitsAfterDecimal()).thenReturn(2);
        when(currency.getCurrencyInMultiplesOf()).thenReturn(0);
        CurrencyData currencyData = new CurrencyData(CURRENCY_CODE, "UAE Dirham", 2, 0, "UAE Dirham [AED]", "currency.AED");
        when(currency.toData()).thenReturn(currencyData);

        // Setup loan
        when(loan.getId()).thenReturn(LOAN_ID);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.isFactorRateEnabled()).thenReturn(true);
        when(loan.getNetDisbursalAmount()).thenReturn(new BigDecimal("89500.00"));
        when(loan.getSummary()).thenReturn(loanSummary);
        when(loan.getExternalId()).thenReturn(org.apache.fineract.infrastructure.core.domain.ExternalId.empty());

        // Setup loan summary with outstanding fees and taxes
        when(loanSummary.getTotalFeeChargesOutstanding()).thenReturn(FEE_CHARGES_OUTSTANDING);
        when(loanSummary.getTotalTaxChargesOutstanding()).thenReturn(TAX_CHARGES_OUTSTANDING);

        // Create Money objects after MoneyHelper is initialized
        Money principalOutstandingMoney = Money.of(currency, PRINCIPAL_OUTSTANDING);
        Money interestOutstandingMoney = Money.of(currency, INTEREST_OUTSTANDING);
        Money zeroMoney = Money.of(currency, BigDecimal.ZERO);
        Money penaltyOutstandingMoney = Money.of(currency, PENALTY_OUTSTANDING);
        Money totalOutstandingMoney = Money.of(currency, PRINCIPAL_OUTSTANDING.add(FEE_CHARGES_OUTSTANDING).add(TAX_CHARGES_OUTSTANDING));

        // Setup foreclosure detail installment
        when(foreclosureDetail.getPrincipalOutstanding(currency)).thenReturn(principalOutstandingMoney);
        when(foreclosureDetail.getInterestOutstanding(currency)).thenReturn(interestOutstandingMoney);
        when(foreclosureDetail.getFeeChargesOutstanding(currency)).thenReturn(zeroMoney); // Zero in installment
        when(foreclosureDetail.getTaxChargesOutstanding(currency)).thenReturn(zeroMoney); // Zero in installment
        when(foreclosureDetail.getPenaltyChargesOutstanding(currency)).thenReturn(penaltyOutstandingMoney);
        when(foreclosureDetail.getFeeChargesCharged(currency)).thenReturn(zeroMoney); // Zero in installment
        when(foreclosureDetail.getTaxChargesCharged(currency)).thenReturn(zeroMoney); // Zero in installment
        when(foreclosureDetail.getTotalOutstanding(currency)).thenReturn(totalOutstandingMoney);

        // Setup loan to return foreclosure detail
        when(loan.fetchLoanForeclosureDetail(FORECLOSURE_DATE)).thenReturn(foreclosureDetail);

        // Setup repository mocks
        when(loanRepositoryWrapper.findOneWithNotFoundDetection(LOAN_ID, true)).thenReturn(loan);
        when(loanLineOfCreditParamsRepository.findByLoanId(LOAN_ID)).thenReturn(Optional.empty());
        when(accountAssociationsRepository.findByLoanIdAndType(anyLong(), any())).thenReturn(null);
        when(paymentTypeReadPlatformService.retrieveAllPaymentTypes()).thenReturn(Collections.emptyList());

        // Mock application currency repository
        when(applicationCurrencyRepository.findOneWithNotFoundDetection(currency)).thenReturn(applicationCurrency);
        when(applicationCurrency.toData()).thenReturn(currencyData);

        // Mock context
        when(context.authenticatedUser()).thenReturn(null);
    }

    @Test
    @DisplayName("Foreclosure template should include fees and taxes from loan summary for Factor Rate loans")
    void testForeclosureTemplateIncludesFeesAndTaxesForFactorRateLoans() {
        // When
        LoanTransactionData result = credXLoanReadPlatformService.retrieveLoanForeclosureTemplate(LOAN_ID, FORECLOSURE_DATE);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPrincipalPortion()).isEqualByComparingTo(PRINCIPAL_OUTSTANDING);
        assertThat(result.getFeeChargesPortion()).as("Fee charges should be included from loan summary")
                .isEqualByComparingTo(FEE_CHARGES_OUTSTANDING);
        assertThat(result.getTaxChargesPortion()).as("Tax charges should be included from loan summary")
                .isEqualByComparingTo(TAX_CHARGES_OUTSTANDING);
        assertThat(result.getInterestPortion()).isEqualByComparingTo(INTEREST_OUTSTANDING);
        assertThat(result.getPenaltyChargesPortion()).isEqualByComparingTo(PENALTY_OUTSTANDING);

        // Verify total includes fees and taxes
        BigDecimal expectedTotal = PRINCIPAL_OUTSTANDING.add(FEE_CHARGES_OUTSTANDING).add(TAX_CHARGES_OUTSTANDING).add(INTEREST_OUTSTANDING)
                .add(PENALTY_OUTSTANDING);
        assertThat(result.getAmount()).as("Total amount should include fees and taxes").isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("Foreclosure template should use installment amounts when they are non-zero")
    void testForeclosureTemplateUsesInstallmentAmountsWhenAvailable() {
        // Given - installment has fees and taxes
        BigDecimal installmentFee = new BigDecimal("5000.00");
        BigDecimal installmentTax = new BigDecimal("250.00");
        Money installmentFeeMoney = Money.of(currency, installmentFee);
        Money installmentTaxMoney = Money.of(currency, installmentTax);
        when(foreclosureDetail.getFeeChargesCharged(currency)).thenReturn(installmentFeeMoney);
        when(foreclosureDetail.getTaxChargesCharged(currency)).thenReturn(installmentTaxMoney);

        // When
        LoanTransactionData result = credXLoanReadPlatformService.retrieveLoanForeclosureTemplate(LOAN_ID, FORECLOSURE_DATE);

        // Then - should use installment amounts, not loan summary
        assertThat(result.getFeeChargesPortion()).as("Should use installment fee amount when available")
                .isEqualByComparingTo(installmentFee);
        assertThat(result.getTaxChargesPortion()).as("Should use installment tax amount when available")
                .isEqualByComparingTo(installmentTax);
    }

    @Test
    @DisplayName("Foreclosure template should handle zero fees and taxes in loan summary")
    void testForeclosureTemplateHandlesZeroFeesAndTaxes() {
        // Given - loan summary has zero outstanding fees and taxes
        when(loanSummary.getTotalFeeChargesOutstanding())
                .thenReturn(BigDecimal.ZERO);
        when(loanSummary.getTotalTaxChargesOutstanding())
                .thenReturn(BigDecimal.ZERO);

        // When
        LoanTransactionData result = credXLoanReadPlatformService.retrieveLoanForeclosureTemplate(LOAN_ID, FORECLOSURE_DATE);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFeeChargesPortion()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTaxChargesPortion()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
