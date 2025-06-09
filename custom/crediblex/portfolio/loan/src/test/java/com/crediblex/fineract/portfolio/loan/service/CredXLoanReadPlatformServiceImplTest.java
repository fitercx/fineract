package com.crediblex.fineract.portfolio.loan.service;

import com.crediblex.fineract.portfolio.loan.data.ExtendedLoanSchedulePeriodData;
import com.crediblex.fineract.portfolio.loan.queries.LoanQueries.RapaymentStatusQuery;
import com.crediblex.fineract.portfolio.loan.repository.CredXLoanTransactionRepository;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;

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
        when(configurationDomainService.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", configurationDomainService);
        moneyHelper.initialize();


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
        // Given: Mock CurrencyData
        CurrencyData currency = new CurrencyData("USD", "US Dollar", 2, 1, "$", "currency.USD");
        LocalDate dueDate = LocalDate.now().plusDays(3);

        // Status: PAID
        LoanSchedulePeriodData paidPeriod = LoanSchedulePeriodData.repaymentOnlyPeriod(1, null, dueDate, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(paidPeriod.getComplete()).thenReturn(true);

        assertEquals(ExtendedLoanSchedulePeriodData.Status.PAID, credXLoanReadPlatformService.resolvePeriodStatus(currency, paidPeriod));

        // Status: LATE_FEE_APPLIED
        LoanSchedulePeriodData lateFeePeriod = LoanSchedulePeriodData.repaymentOnlyPeriod(2, null, dueDate, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO);
        assertEquals(ExtendedLoanSchedulePeriodData.Status.LATE_FEE_APPLIED, credXLoanReadPlatformService.resolvePeriodStatus(currency, lateFeePeriod));

        // Status: PARTIAL_PAID
        LoanSchedulePeriodData partialPaidPeriod = LoanSchedulePeriodData.repaymentOnlyPeriod(3, null, dueDate, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(partialPaidPeriod.totalOutstandingForPeriod()).thenReturn(BigDecimal.TEN);
        when(partialPaidPeriod.getTotalPaidForPeriod()).thenReturn(BigDecimal.ONE);

        assertEquals(ExtendedLoanSchedulePeriodData.Status.PARTIAL_PAID, credXLoanReadPlatformService.resolvePeriodStatus(currency, partialPaidPeriod));

        // Status: OVERDUE
        LoanSchedulePeriodData overduePeriod = LoanSchedulePeriodData.repaymentOnlyPeriod(4, null, LocalDate.now().minusDays(1),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(ExtendedLoanSchedulePeriodData.Status.OVERDUE, credXLoanReadPlatformService.resolvePeriodStatus(currency, overduePeriod));

        // Status: DUE
        LoanSchedulePeriodData duePeriod = LoanSchedulePeriodData.repaymentOnlyPeriod(5, null, LocalDate.now(), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(ExtendedLoanSchedulePeriodData.Status.DUE, credXLoanReadPlatformService.resolvePeriodStatus(currency, duePeriod));

        // Status: SCHEDULED
        LoanSchedulePeriodData scheduledPeriod = LoanSchedulePeriodData.repaymentOnlyPeriod(6, null, LocalDate.now().plusDays(5),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(ExtendedLoanSchedulePeriodData.Status.SCHEDULED, credXLoanReadPlatformService.resolvePeriodStatus(currency, scheduledPeriod));
    }

}
