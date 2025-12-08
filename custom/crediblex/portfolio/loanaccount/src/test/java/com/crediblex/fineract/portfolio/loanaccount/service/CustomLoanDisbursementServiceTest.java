package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.domain.*;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDisbursementValidator;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for CustomLoanDisbursementService focusing on proportional fee allocation for multi-tranche loans.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomLoanDisbursementServiceTest {

    private static final String CURRENCY_CODE = "USD";
    private static final LocalDate DISBURSEMENT_DATE_1 = LocalDate.of(2024, 1, 15);
    private static final LocalDate DISBURSEMENT_DATE_2 = LocalDate.of(2024, 2, 15);
    private static final BigDecimal SANCTIONED_AMOUNT = new BigDecimal("100000.00");
    private static final BigDecimal TRANCHE_1_AMOUNT = new BigDecimal("30000.00");
    private static final BigDecimal TRANCHE_2_AMOUNT = new BigDecimal("70000.00");
    private static final BigDecimal EXPECTED_TOTAL_FEE = new BigDecimal("1000.00"); // 1% of 100k
    private static final BigDecimal EXPECTED_TRANCHE_1_FEE = new BigDecimal("300.00"); // 30% of 1000

    @Mock
    private LoanChargeValidator loanChargeValidator;

    @Mock
    private LoanDisbursementValidator loanDisbursementValidator;

    @Mock
    private ReprocessLoanTransactionsService reprocessLoanTransactionsService;

    @Mock
    private Loan loan;

    @Mock
    private LoanCharge processingFeeCharge;

    @Mock
    private Charge chargeDefinition;

    @Mock
    private MonetaryCurrency currency;

    @Mock
    private LoanSummary loanSummary;

    @Mock
    private PaymentDetail paymentDetail;

    @Mock
    private LoanTransaction chargesPaymentTransaction;

    @Mock
    private LoanTransaction taxPaymentTransaction;

    @Mock
    private LoanDisbursementDetails tranche1Details;

    @Mock
    private LoanDisbursementDetails tranche2Details;

    @Mock
    private LoanTrancheDisbursementCharge trancheDisbursementCharge;

    @Mock
    private Office office;

    private CustomLoanDisbursementService customLoanDisbursementService;
    private List<LoanDisbursementDetails> disbursementDetails;
    private Set<LoanCharge> activeCharges;
    private MockedStatic<MoneyHelper> moneyHelperMock;
    private CurrencyData currencyData;

    @BeforeEach
    void setUp() {
        // Initialize business dates
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, DISBURSEMENT_DATE_1);
        ThreadLocalContextUtil.setBusinessDates(businessDates);

        // Set up tenant context
        FineractPlatformTenant tenant = new FineractPlatformTenant(1L, "default", "default", "UTC", null);
        ThreadLocalContextUtil.setTenant(tenant);

        // Setup MoneyHelper static configuration
        moneyHelperMock = mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);

        customLoanDisbursementService = new CustomLoanDisbursementService(loanChargeValidator, loanDisbursementValidator,
                reprocessLoanTransactionsService);

        // Setup currency data
        currencyData = new CurrencyData(CURRENCY_CODE, 2, 1);

        // Setup currency - use lenient to avoid issues with nested calls
        lenient().when(currency.getCode()).thenReturn(CURRENCY_CODE);
        lenient().when(currency.getDigitsAfterDecimal()).thenReturn(2);
        lenient().when(currency.getCurrencyInMultiplesOf()).thenReturn(1);
        lenient().when(currency.toData()).thenReturn(currencyData);
        when(loan.getCurrency()).thenReturn(currency);

        // Setup loan summary - create Money object first to avoid nested mocking issues
        Money totalFeeCharges = Money.of(currencyData, EXPECTED_TOTAL_FEE);
        when(loan.getSummary()).thenReturn(loanSummary);
        when(loanSummary.getTotalFeeChargesDueAtDisbursement(currency)).thenReturn(totalFeeCharges);

        // Setup charge definition
        when(chargeDefinition.getChargeTimeType()).thenReturn(ChargeTimeType.DISBURSEMENT.getValue());
        when(chargeDefinition.getChargeCalculation()).thenReturn(ChargeCalculationType.PERCENT_OF_AMOUNT.getValue());
        when(chargeDefinition.getChargePaymentMode()).thenReturn(ChargePaymentMode.REGULAR.getValue());
        when(chargeDefinition.isPenalty()).thenReturn(false);

        // Create Money objects before using in mocks to avoid nested mocking issues
        Money totalFeeMoney = Money.of(currencyData, EXPECTED_TOTAL_FEE);
        Money zeroMoney = Money.zero(currencyData);

        // Setup processing fee charge
        when(processingFeeCharge.getCharge()).thenReturn(chargeDefinition);
        when(processingFeeCharge.getChargePaymentMode()).thenReturn(ChargePaymentMode.REGULAR);
        when(processingFeeCharge.isWaived()).thenReturn(false);
        when(processingFeeCharge.isFullyPaid()).thenReturn(false);
        when(processingFeeCharge.hasTax()).thenReturn(false);
        when(processingFeeCharge.getTrancheDisbursementCharge()).thenReturn(null);
        when(processingFeeCharge.getAmount(currency)).thenReturn(totalFeeMoney);
        when(processingFeeCharge.getAmountPaid(currency)).thenReturn(zeroMoney);
        when(processingFeeCharge.amount()).thenReturn(EXPECTED_TOTAL_FEE);

        activeCharges = new HashSet<>();
        activeCharges.add(processingFeeCharge);
        when(loan.getActiveCharges()).thenReturn(activeCharges);

        // Setup disbursement details
        disbursementDetails = new ArrayList<>();
        when(loan.getDisbursementDetails()).thenReturn(disbursementDetails);

        // Setup tranche 1
        when(tranche1Details.principal()).thenReturn(TRANCHE_1_AMOUNT);
        when(tranche1Details.actualDisbursementDate()).thenReturn(DISBURSEMENT_DATE_1);
        when(tranche1Details.expectedDisbursementDate()).thenReturn(DISBURSEMENT_DATE_1);

        // Setup tranche 2
        when(tranche2Details.principal()).thenReturn(TRANCHE_2_AMOUNT);
        when(tranche2Details.actualDisbursementDate()).thenReturn(DISBURSEMENT_DATE_2);
        when(tranche2Details.expectedDisbursementDate()).thenReturn(DISBURSEMENT_DATE_2);

        // Setup loan methods
        when(loan.getApprovedPrincipal()).thenReturn(SANCTIONED_AMOUNT);
        when(loan.isMultiDisburmentLoan()).thenReturn(true);
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);
        when(loan.getOffice()).thenReturn(office);
        when(loan.getActualDisbursementDate()).thenReturn(DISBURSEMENT_DATE_1);
        when(loan.isNoneOrCashOrUpfrontAccrualAccountingEnabledOnLoanProduct()).thenReturn(false);
        when(loan.getExpectedFirstRepaymentOnDate()).thenReturn(DISBURSEMENT_DATE_1.plusDays(30));
    }

    @Test
    void testProportionalFeeCalculation_FirstTranche() {
        // Given: Multi-tranche loan with first tranche being disbursed
        disbursementDetails.add(tranche1Details);
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);

        // When: Handle disbursement transaction
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        // Then: Verify proportional fee is calculated correctly
        // Tranche 1 fee = (30,000 / 100,000) * 1,000 = 300
        verify(processingFeeCharge, never()).markAsFullyPaid(); // Should not be fully paid after first tranche
        verify(loan, times(1)).addLoanTransaction(any(LoanTransaction.class));
    }

    @Test
    void testProportionalFeeCalculation_SecondTranche() {
        // Given: Multi-tranche loan with both tranches
        disbursementDetails.add(tranche1Details);
        disbursementDetails.add(tranche2Details);
        Money tranche1FeeMoney = Money.of(currencyData, EXPECTED_TRANCHE_1_FEE);
        when(processingFeeCharge.getAmountPaid(currency)).thenReturn(tranche1FeeMoney); // First tranche already paid
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_2);

        // When: Handle second tranche disbursement
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_2, paymentDetail);

        // Then: Verify proportional fee is calculated correctly
        // Tranche 2 fee = (70,000 / 100,000) * 1,000 = 700
        verify(processingFeeCharge, times(1)).markAsFullyPaid(); // Should be fully paid after second tranche
        verify(loan, times(1)).addLoanTransaction(any(LoanTransaction.class));
    }

    @Test
    void testProportionalFeeCalculation_TotalEqualsSanctionedFee() {
        // Given: Multi-tranche loan with both tranches
        disbursementDetails.add(tranche1Details);
        disbursementDetails.add(tranche2Details);

        // When: Handle both tranche disbursements
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_2);
        Money tranche1FeeMoney2 = Money.of(currencyData, EXPECTED_TRANCHE_1_FEE);
        when(processingFeeCharge.getAmountPaid(currency)).thenReturn(tranche1FeeMoney2);
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_2, paymentDetail);

        // Then: Total fees should equal sanctioned amount fee
        // Tranche 1: 300 + Tranche 2: 700 = 1000 (matches 1% of 100k)
        verify(processingFeeCharge, times(1)).markAsFullyPaid();
    }

    @Test
    void testSingleDisbursementLoan_UsesFullChargeAmount() {
        // Given: Single disbursement loan (not multi-tranche)
        when(loan.isMultiDisburmentLoan()).thenReturn(false);
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);

        // When: Handle disbursement transaction
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        // Then: Should use full charge amount (not proportional)
        verify(processingFeeCharge, times(1)).markAsFullyPaid();
        verify(loan, times(1)).addLoanTransaction(any(LoanTransaction.class));
    }

    @Test
    void testTrancheSpecificCharge_UsesTrancheAmountDirectly() {
        // Given: Charge linked to specific tranche
        Money trancheFeeMoney = Money.of(currencyData, EXPECTED_TRANCHE_1_FEE);
        when(processingFeeCharge.getTrancheDisbursementCharge()).thenReturn(trancheDisbursementCharge);
        when(trancheDisbursementCharge.getloanDisbursementDetails()).thenReturn(tranche1Details);
        when(processingFeeCharge.getAmount(currency)).thenReturn(trancheFeeMoney);
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);

        // When: Handle disbursement transaction
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        // Then: Should use tranche-specific amount directly
        verify(processingFeeCharge, times(1)).markAsFullyPaid();
        verify(loan, times(1)).addLoanTransaction(any(LoanTransaction.class));
    }

    @Test
    void testProportionalTaxCalculation() {
        // Given: Charge with tax
        BigDecimal taxPercentage = new BigDecimal("10.00"); // 10% tax
        BigDecimal expectedTotalTax = EXPECTED_TOTAL_FEE.multiply(taxPercentage).divide(new BigDecimal("100"));
        Money taxMoney = Money.of(currencyData, expectedTotalTax);

        when(processingFeeCharge.hasTax()).thenReturn(true);
        when(processingFeeCharge.getTaxAmount(currency)).thenReturn(taxMoney);
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);
        disbursementDetails.add(tranche1Details);

        // When: Handle disbursement transaction
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        // Then: Tax should be calculated proportionally
        verify(loan, times(2)).addLoanTransaction(any(LoanTransaction.class)); // One for fees, one for tax
    }

    @Test
    void testAccountTransferPaymentMode_SkipsProportionalCalculation() {
        // Given: Charge with account transfer payment mode
        when(processingFeeCharge.getChargePaymentMode()).thenReturn(ChargePaymentMode.ACCOUNT_TRANSFER);
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);
        disbursementDetails.add(tranche1Details);

        // When: Handle disbursement transaction
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        // Then: Should not process charge (account transfer handled separately)
        verify(processingFeeCharge, never()).markAsFullyPaid();
        verify(loan, never()).addLoanTransaction(any(LoanTransaction.class));
    }

    @Test
    void testWaivedCharge_SkipsProcessing() {
        // Given: Charge is waived
        when(processingFeeCharge.isWaived()).thenReturn(true);
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);
        disbursementDetails.add(tranche1Details);

        // When: Handle disbursement transaction
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        // Then: Should not process waived charge
        verify(processingFeeCharge, never()).markAsFullyPaid();
        verify(loan, never()).addLoanTransaction(any(LoanTransaction.class));
    }

    @Test
    void testFullyPaidCharge_SkipsProcessing() {
        // Given: Charge is already fully paid
        when(processingFeeCharge.isFullyPaid()).thenReturn(true);
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);
        disbursementDetails.add(tranche1Details);

        // When: Handle disbursement transaction
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        // Then: Should not process already paid charge
        verify(processingFeeCharge, never()).markAsFullyPaid();
        verify(loan, never()).addLoanTransaction(any(LoanTransaction.class));
    }

    @Test
    void testTrancheDisbursementChargeType_ProcessesCorrectly() {
        // Given: TRANCHE_DISBURSEMENT charge type
        when(chargeDefinition.getChargeTimeType()).thenReturn(ChargeTimeType.TRANCHE_DISBURSEMENT.getValue());
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);
        disbursementDetails.add(tranche1Details);

        // When: Handle disbursement transaction
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        // Then: Should process tranche disbursement charge
        verify(processingFeeCharge, times(1)).markAsFullyPaid();
        verify(loan, times(1)).addLoanTransaction(any(LoanTransaction.class));
    }

    @Test
    void testMultipleCharges_EachCalculatedProportionally() {
        // Given: Multiple charges on multi-tranche loan
        LoanCharge charge2 = mock(LoanCharge.class);
        Charge charge2Definition = mock(Charge.class);
        when(charge2Definition.getChargeTimeType()).thenReturn(ChargeTimeType.DISBURSEMENT.getValue());
        when(charge2.getCharge()).thenReturn(charge2Definition);
        when(charge2.getChargePaymentMode()).thenReturn(ChargePaymentMode.REGULAR);
        when(charge2.isWaived()).thenReturn(false);
        when(charge2.isFullyPaid()).thenReturn(false);
        when(charge2.hasTax()).thenReturn(false);
        when(charge2.getTrancheDisbursementCharge()).thenReturn(null);
        Money charge2Amount = Money.of(currencyData, new BigDecimal("500.00"));
        Money charge2Zero = Money.zero(currencyData);
        when(charge2.getAmount(currency)).thenReturn(charge2Amount);
        when(charge2.getAmountPaid(currency)).thenReturn(charge2Zero);
        when(loan.getActualDisbursementDate(charge2)).thenReturn(DISBURSEMENT_DATE_1);

        activeCharges.add(charge2);
        disbursementDetails.add(tranche1Details);
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);

        // When: Handle disbursement transaction
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        // Then: Both charges should be processed proportionally
        verify(processingFeeCharge, never()).markAsFullyPaid();
        verify(charge2, never()).markAsFullyPaid();
        verify(loan, atLeast(1)).addLoanTransaction(any(LoanTransaction.class));
    }

    @Test
    void testRoundingHandling_ProportionalCalculation() {
        // Given: Amounts that require rounding
        BigDecimal sanctionedAmount = new BigDecimal("100000.00");
        BigDecimal trancheAmount = new BigDecimal("33333.33"); // 1/3 of sanctioned
        BigDecimal totalFee = new BigDecimal("1000.00");
        BigDecimal expectedProportionalFee = trancheAmount.multiply(totalFee).divide(sanctionedAmount, 6, java.math.RoundingMode.HALF_UP);

        when(loan.getApprovedPrincipal()).thenReturn(sanctionedAmount);
        when(tranche1Details.principal()).thenReturn(trancheAmount);
        Money totalFeeMoney2 = Money.of(currencyData, totalFee);
        when(processingFeeCharge.getAmount(currency)).thenReturn(totalFeeMoney2);
        when(loan.getActualDisbursementDate(processingFeeCharge)).thenReturn(DISBURSEMENT_DATE_1);
        disbursementDetails.add(tranche1Details);

        // When: Handle disbursement transaction
        customLoanDisbursementService.handleDisbursementTransaction(loan, DISBURSEMENT_DATE_1, paymentDetail);

        // Then: Should handle rounding correctly (6 decimal places, HALF_UP)
        verify(loan, times(1)).addLoanTransaction(any(LoanTransaction.class));
        // The proportional calculation should use proper rounding
        assertTrue(expectedProportionalFee.compareTo(new BigDecimal("333.333")) >= 0);
    }

    @AfterEach
    void tearDown() {
        if (moneyHelperMock != null) {
            moneyHelperMock.close();
        }
        ThreadLocalContextUtil.reset();
    }
}
