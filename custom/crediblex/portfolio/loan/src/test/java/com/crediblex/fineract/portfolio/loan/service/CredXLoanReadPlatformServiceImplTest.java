package com.crediblex.fineract.portfolio.loan.service;

import com.crediblex.fineract.portfolio.loan.data.ExtendedLoanSchedulePeriodData;
import com.crediblex.fineract.portfolio.loan.queries.LoanQueries.RapaymentStatusQuery;
import com.crediblex.fineract.portfolio.loan.repository.CredXLoanTransactionRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CredXLoanReadPlatformServiceImplTest {


    @Mock
    private CredXLoanTransactionRepository credXLoanTransactionRepository;

    @Mock
    private ConfigurationDomainService configurationDomainService;


    @InjectMocks
    private CredXLoanReadPlatformServiceImpl credXLoanReadPlatformService;

    private Long loanId;
    private RapaymentStatusQuery.Result mockResult;
    private LocalDate transactionDate;

    @BeforeEach
    public void setup() {

        MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", configurationDomainService);
        moneyHelper.initialize();

        // Initialize business dates
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.now());
        ThreadLocalContextUtil.setBusinessDates(businessDates);


        loanId = 1L;
        transactionDate = LocalDate.of(2025, 6, 7); // Using current date

        // Create a mock result object that simulates the repository's response
        mockResult = new RapaymentStatusQuery.Result();
        mockResult.setField("transactionDate", Date.valueOf(transactionDate));
        mockResult.setField("currencyCode", "USD");
        mockResult.setField("currencyName", "US Dollar");
        mockResult.setField("currencyDigits", 2);
        mockResult.setField("inMultiplesOf", 1);
        mockResult.setField("currencyDisplaySymbol", "$");
        mockResult.setField("currencyNameCode", "currency.USD");
        mockResult.setField("principalDue", BigDecimal.valueOf(100.00));
        mockResult.setField("interestDue", BigDecimal.valueOf(10.00));
        mockResult.setField("feeDue", BigDecimal.valueOf(5.00));
        mockResult.setField("penaltyDue", BigDecimal.valueOf(2.00));
        mockResult.setField("netDisbursalAmount", BigDecimal.valueOf(500.00));

    }

    @Test
    public void testRetrieveLoanTransactionTemplate() {
        // Given
        when(credXLoanTransactionRepository.retrieveLoanRepaymentTemplate(loanId)).thenReturn(mockResult);

        // When
        LoanTransactionData result = credXLoanReadPlatformService.retrieveLoanTransactionTemplate(loanId);

        // Then
        Assertions.assertNotNull(result);

        // Verify transaction details
        assertEquals(LoanEnumerations.transactionType(LoanTransactionType.REPAYMENT).getId(), result.getType().getId());
        assertEquals(transactionDate, result.getDate());

        // Verify currency details
        CurrencyData currencyData = result.getCurrency();
        assertEquals("USD", currencyData.getCode());
        assertEquals("US Dollar", currencyData.getName());
        assertEquals(Integer.valueOf(2), currencyData.getDecimalPlaces());
        assertEquals("$", currencyData.getDisplaySymbol());
        assertEquals("currency.USD", currencyData.getNameCode());

        // Verify amount details
        assertEquals(BigDecimal.valueOf(100.00), result.getPrincipalPortion());
        assertEquals(BigDecimal.valueOf(10.00), result.getInterestPortion());
        assertEquals(BigDecimal.valueOf(5.00), result.getFeeChargesPortion());
        assertEquals(BigDecimal.valueOf(2.00), result.getPenaltyChargesPortion());

        // Calculated total should be the sum of principal, interest, fee, and penalty
        BigDecimal expectedTotal = BigDecimal.valueOf(117.00); // 100 + 10 + 5 + 2
        assertEquals(expectedTotal, result.getAmount());

        // Verify loan ID
        assertEquals(loanId, result.getLoanId());

        // Verify other properties
        assertEquals(BigDecimal.valueOf(500.00), result.getNetDisbursalAmount());
        Assertions.assertFalse(result.isManuallyReversed());
        assertEquals(ExternalId.empty(), result.getExternalId());
    }

    @Test
    public void testResolvePeriodStatus() {
        when(configurationDomainService.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);

        // Setup tenant context
        FineractPlatformTenant tenant = new FineractPlatformTenant(1L, "default", "default", "UTC", null);
        ThreadLocalContextUtil.setTenant(tenant);

        // Common setup
        CurrencyData currency = new CurrencyData("USD", "US Dollar", 2, 1, "$", "currency.USD");
        LocalDate today = LocalDate.now();
        LocalDate dueDate = today.plusDays(3);
        LocalDate fromDate = dueDate.minusDays(30);

        // Test case: PAID status
        {
            LoanSchedulePeriodData paidPeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    1,                  // period number
                    fromDate,           // from date
                    dueDate,            // due date
                    BigDecimal.TEN,     // principal amount
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.TEN      // penalty amount
            ));
            Mockito.doReturn(true).when(paidPeriod).getComplete();

            assertEquals(ExtendedLoanSchedulePeriodData.Status.PAID,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, paidPeriod));
        }

        // Test case: LATE_FEE_APPLIED status
        {
            BigDecimal principalAmount = BigDecimal.TEN;
            BigDecimal penaltyAmount = BigDecimal.valueOf(2);

            LoanSchedulePeriodData lateFeePeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    2,                  // period number
                    fromDate,           // from date
                    dueDate,            // due date
                    principalAmount,    // principal amount
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    penaltyAmount       // penalty amount (positive value = late fee applied)
            ));

            assertEquals(ExtendedLoanSchedulePeriodData.Status.LATE_FEE_APPLIED,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, lateFeePeriod));
        }

        // Test case: PARTIAL_PAID status
        {
            BigDecimal principalAmount = BigDecimal.valueOf(2);
            BigDecimal partialPaymentAmount = BigDecimal.valueOf(2);

            LoanSchedulePeriodData partialPaidPeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    3,                  // period number
                    fromDate,           // from date
                    dueDate,            // due date
                    principalAmount,    // principal amount
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO     // penalty amount
            ));
            Mockito.doReturn(partialPaymentAmount).when(partialPaidPeriod).getTotalPaidForPeriod();

            assertEquals(ExtendedLoanSchedulePeriodData.Status.PARTIAL_PAID,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, partialPaidPeriod));
        }

        // Test case: OVERDUE status
        {
            LocalDate pastDueDate = today.minusDays(1);

            LoanSchedulePeriodData overduePeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    4,                  // period number
                    fromDate,           // from date
                    pastDueDate,        // due date in the past
                    BigDecimal.ZERO,    // principal amount
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO     // penalty amount
            ));

            assertEquals(ExtendedLoanSchedulePeriodData.Status.OVERDUE,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, overduePeriod));
        }

        // Test case: DUE status
        {
            LoanSchedulePeriodData duePeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    5,                  // period number
                    fromDate,           // from date
                    today,              // due date is today
                    BigDecimal.ZERO,    // principal amount
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO     // penalty amount
            ));

            assertEquals(ExtendedLoanSchedulePeriodData.Status.DUE,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, duePeriod));
        }

        // Test case: SCHEDULED status
        {
            LocalDate futureDueDate = today.plusDays(5);

            LoanSchedulePeriodData scheduledPeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    6,                  // period number
                    fromDate,           // from date
                    futureDueDate,      // due date in the future
                    BigDecimal.ZERO,    // principal amount
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO     // penalty amount
            ));

            assertEquals(ExtendedLoanSchedulePeriodData.Status.SCHEDULED,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, scheduledPeriod));
        }
    }
}