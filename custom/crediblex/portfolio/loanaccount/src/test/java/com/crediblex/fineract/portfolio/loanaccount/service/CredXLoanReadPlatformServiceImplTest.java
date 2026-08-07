package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loanaccount.data.CredXOverdueClientData;
import com.crediblex.fineract.portfolio.loanaccount.data.CredXOverdueCollectedData;
import com.crediblex.fineract.portfolio.loanaccount.data.CredXOverdueCollectedSummaryData;
import com.crediblex.fineract.portfolio.loanaccount.data.CredXOverdueLoanData;
import com.crediblex.fineract.portfolio.loanaccount.data.CredXOverdueLoansSummaryData;
import com.crediblex.fineract.portfolio.loanaccount.data.ExtendedLoanSchedulePeriodData;
import com.crediblex.fineract.portfolio.loanaccount.queries.LoanQueries.RapaymentStatusQuery;
import com.crediblex.fineract.portfolio.loanaccount.repository.CredXLoanTransactionRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class CredXLoanReadPlatformServiceImplTest {

    @Mock
    private CredXLoanTransactionRepository credXLoanTransactionRepository;

    @Mock
    private ConfigurationDomainService configurationDomainService;

    @Mock
    private PaymentTypeReadPlatformService paymentTypeReadPlatformService;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Mock
    private org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator sqlGenerator;

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

        // Mock payment type options
        List<PaymentTypeData> paymentTypeOptions = new ArrayList<>();
        when(paymentTypeReadPlatformService.retrieveAllPaymentTypes()).thenReturn(paymentTypeOptions);

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
                    BigDecimal.ZERO,    // tax amount
                    BigDecimal.TEN      // penalty amount
            ));
            Mockito.doReturn(true).when(paidPeriod).getComplete();

            assertEquals(ExtendedLoanSchedulePeriodData.Status.PAID,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, paidPeriod));
        }

        // Test case: LATE_FEE_APPLIED status - overdue with penalties not fully paid (total paid < penalty)
        {
            LocalDate pastDueDate = today.minusDays(1);
            BigDecimal principalAmount = BigDecimal.TEN;
            BigDecimal penaltyAmount = BigDecimal.valueOf(5);
            BigDecimal partialPayment = BigDecimal.valueOf(2); // Less than penalty amount

            LoanSchedulePeriodData lateFeePeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    2,                  // period number
                    fromDate,           // from date
                    pastDueDate,        // due date in the past (overdue)
                    principalAmount,    // principal amount
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO,    // tax amount
                    penaltyAmount       // penalty amount due
            ));
            Mockito.doReturn(partialPayment).when(lateFeePeriod).getTotalPaidForPeriod();

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
                    BigDecimal.ZERO,    // tax amount
                    BigDecimal.ZERO     // penalty amount
            ));
            Mockito.doReturn(partialPaymentAmount).when(partialPaidPeriod).getTotalPaidForPeriod();

            assertEquals(ExtendedLoanSchedulePeriodData.Status.PARTIAL_PAID,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, partialPaidPeriod));
        }

        // Test case: OVERDUE status - overdue with penalties fully paid (total paid >= penalty)
        {
            LocalDate pastDueDate = today.minusDays(1);
            BigDecimal principalAmount = BigDecimal.TEN;
            BigDecimal penaltyAmount = BigDecimal.valueOf(3);
            BigDecimal fullPayment = BigDecimal.valueOf(5); // Greater than penalty amount

            LoanSchedulePeriodData overduePeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    4,                  // period number
                    fromDate,           // from date
                    pastDueDate,        // due date in the past (overdue)
                    principalAmount,    // principal amount (still outstanding)
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO,    // tax amount
                    penaltyAmount       // penalty amount due
            ));
            Mockito.doReturn(fullPayment).when(overduePeriod).getTotalPaidForPeriod();

            assertEquals(ExtendedLoanSchedulePeriodData.Status.OVERDUE,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, overduePeriod));
        }

        // Test case: OVERDUE status - overdue with no penalties at all
        {
            LocalDate pastDueDate = today.minusDays(1);
            BigDecimal principalAmount = BigDecimal.TEN;

            LoanSchedulePeriodData overdueNoPenaltyPeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    5,                  // period number
                    fromDate,           // from date
                    pastDueDate,        // due date in the past (overdue)
                    principalAmount,    // principal amount (still outstanding)
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO,    // tax amount
                    BigDecimal.ZERO     // no penalty amount due
            ));

            assertEquals(ExtendedLoanSchedulePeriodData.Status.OVERDUE,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, overdueNoPenaltyPeriod));
        }

        // Test case: OVERDUE status - overdue with penalties exactly paid (total paid = penalty)
        {
            LocalDate pastDueDate = today.minusDays(1);
            BigDecimal principalAmount = BigDecimal.TEN;
            BigDecimal penaltyAmount = BigDecimal.valueOf(3);
            BigDecimal exactPayment = BigDecimal.valueOf(3); // Exactly equal to penalty amount

            LoanSchedulePeriodData overdueExactPaymentPeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    6,                  // period number
                    fromDate,           // from date
                    pastDueDate,        // due date in the past (overdue)
                    principalAmount,    // principal amount (still outstanding)
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO,    // tax amount
                    penaltyAmount       // penalty amount due
            ));
            Mockito.doReturn(exactPayment).when(overdueExactPaymentPeriod).getTotalPaidForPeriod();

            assertEquals(ExtendedLoanSchedulePeriodData.Status.OVERDUE,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, overdueExactPaymentPeriod));
        }

        // Test case: DUE status
        {
            LoanSchedulePeriodData duePeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    7,                  // period number
                    fromDate,           // from date
                    today,              // due date is today
                    BigDecimal.ZERO,    // principal amount
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO,    // tax amount
                    BigDecimal.ZERO     // penalty amount
            ));

            assertEquals(ExtendedLoanSchedulePeriodData.Status.DUE,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, duePeriod));
        }

        // Test case: SCHEDULED status
        {
            LocalDate futureDueDate = today.plusDays(5);

            LoanSchedulePeriodData scheduledPeriod = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    8,                  // period number
                    fromDate,           // from date
                    futureDueDate,      // due date in the future
                    BigDecimal.ZERO,    // principal amount
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO,    // tax amount
                    BigDecimal.ZERO     // penalty amount
            ));

            assertEquals(ExtendedLoanSchedulePeriodData.Status.SCHEDULED,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, scheduledPeriod));
        }

        // Test case: SCHEDULED status with penalty charges (not overdue)
        {
            LocalDate futureDueDate = today.plusDays(5);
            BigDecimal penaltyAmount = BigDecimal.valueOf(2);

            LoanSchedulePeriodData scheduledPeriodWithPenalties = Mockito.spy(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    9,                  // period number
                    fromDate,           // from date
                    futureDueDate,      // due date in the future
                    BigDecimal.ZERO,    // principal amount
                    BigDecimal.ZERO,    // outstanding loan balance
                    BigDecimal.ZERO,    // interest amount
                    BigDecimal.ZERO,    // fee amount
                    BigDecimal.ZERO,    // tax amount
                    penaltyAmount       // penalty amount (but not overdue)
            ));

            assertEquals(ExtendedLoanSchedulePeriodData.Status.SCHEDULED,
                    credXLoanReadPlatformService.resolvePeriodStatus(currency, scheduledPeriodWithPenalties));
        }
    }

    @Test
    public void testRetrieveCrediblexOverdueLoansSummaryAggregatesAndBreakdowns() throws SQLException {
        // Single aggregate row: whole-loan outstanding (out*) and past-due overdue (od*) across the qualifying clients.
        final ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getLong("totalClients")).thenReturn(2L);
        when(rs.getLong("totalLoans")).thenReturn(3L);
        when(rs.getString("currencyCode")).thenReturn("AED");
        when(rs.getBigDecimal("outPrincipal")).thenReturn(new BigDecimal("150000"));
        when(rs.getBigDecimal("outInterest")).thenReturn(new BigDecimal("8000"));
        when(rs.getBigDecimal("outFees")).thenReturn(new BigDecimal("500"));
        when(rs.getBigDecimal("outLpi")).thenReturn(new BigDecimal("3000"));
        when(rs.getBigDecimal("outTotal")).thenReturn(new BigDecimal("161500"));
        when(rs.getBigDecimal("odPrincipal")).thenReturn(new BigDecimal("18000"));
        when(rs.getBigDecimal("odInterest")).thenReturn(new BigDecimal("1800"));
        when(rs.getBigDecimal("odFees")).thenReturn(new BigDecimal("200"));
        when(rs.getBigDecimal("odLpi")).thenReturn(new BigDecimal("200"));

        stubSummaryQueryWithRow(rs);

        final CredXOverdueLoansSummaryData summary = credXLoanReadPlatformService.retrieveCrediblexOverdueLoansSummary();

        assertEquals("AED", summary.getCurrencyCode());
        assertEquals(Long.valueOf(2L), summary.getTotalClients());
        assertEquals(Long.valueOf(3L), summary.getTotalLoans());

        assertEquals(new BigDecimal("161500"), summary.getTotalOutstanding().getTotal());
        assertEquals(new BigDecimal("150000"), summary.getTotalOutstanding().getPrincipal());
        assertEquals(new BigDecimal("8000"), summary.getTotalOutstanding().getInterest());
        assertEquals(new BigDecimal("500"), summary.getTotalOutstanding().getFees());
        assertEquals(new BigDecimal("3000"), summary.getTotalOutstanding().getLpi());

        // totalOverdue.total is computed as principal + interest + fees + lpi (18000 + 1800 + 200 + 200 = 20200).
        assertEquals(new BigDecimal("20200"), summary.getTotalOverdue().getTotal());
        assertEquals(new BigDecimal("18000"), summary.getTotalOverdue().getPrincipal());
        assertEquals(new BigDecimal("1800"), summary.getTotalOverdue().getInterest());
        assertEquals(new BigDecimal("200"), summary.getTotalOverdue().getFees());
        assertEquals(new BigDecimal("200"), summary.getTotalOverdue().getLpi());

        // Invariants.
        assertEquals(sumOf(summary.getTotalOverdue()), summary.getTotalOverdue().getTotal());
        assertEquals(summary.getTotalOverdue().getLpi(), summary.getTotalLpiOutstanding());
    }

    @Test
    public void testRetrieveCrediblexOverdueLoansSummaryEmptyPortfolioFallsBackToDefaultCurrency() throws SQLException {
        // Empty portfolio: counts 0, coalesced SUMs 0, and max(currency_code) over no rows is NULL.
        final ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getLong("totalClients")).thenReturn(0L);
        when(rs.getLong("totalLoans")).thenReturn(0L);
        when(rs.getString("currencyCode")).thenReturn(null);
        when(rs.getBigDecimal(anyString())).thenReturn(BigDecimal.ZERO);

        stubSummaryQueryWithRow(rs);

        final CredXOverdueLoansSummaryData summary = credXLoanReadPlatformService.retrieveCrediblexOverdueLoansSummary();

        assertEquals("AED", summary.getCurrencyCode());
        assertEquals(Long.valueOf(0L), summary.getTotalClients());
        assertEquals(Long.valueOf(0L), summary.getTotalLoans());
        assertEquals(BigDecimal.ZERO, summary.getTotalOutstanding().getTotal());
        assertEquals(BigDecimal.ZERO, summary.getTotalOverdue().getTotal());
        assertEquals(BigDecimal.ZERO, summary.getTotalLpiOutstanding());
    }

    @Test
    public void testRetrieveCrediblexOverdueLoansGroupsByClientWithBreakdownsAndReconciliation() throws SQLException {
        // Two qualifying clients. Client 12 owns loan A (overdue, 2 installments) and loan C (active, not overdue).
        // Client 7 owns loan B (overdue, 1 installment). Verifies grouping, per-loan overdue breakdown, zeroed overdue
        // for the non-overdue loan, and client-summary reconciliation.
        final ResultSet clientAcme = clientRow(12L, "Acme Trading LLC", "000000012");
        final ResultSet clientBeta = clientRow(7L, "Beta Foods", "000000007");
        final ResultSet loanA = loanRow(12L, 1L, true, "100000", "5000", "0", "2000", "107000");
        final ResultSet loanC = loanRow(12L, 2L, false, "38000", "2000", "0", "0", "40000");
        final ResultSet loanB = loanRow(7L, 3L, true, "50000", "3000", "0", "1000", "54000");
        final ResultSet instA1 = installmentRow(1L, "9000", "900", "100", "100");
        final ResultSet instA2 = installmentRow(1L, "9000", "900", "100", "100");
        final ResultSet instB1 = installmentRow(3L, "9000", "900", "100", "100");

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(2);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    final String sql = invocation.getArgument(0);
                    final org.springframework.jdbc.core.RowMapper<Object> mapper = invocation.getArgument(1);
                    if (sql.contains("as clientName")) {
                        return List.of(mapper.mapRow(clientAcme, 0), mapper.mapRow(clientBeta, 1));
                    } else if (sql.contains("as feesOutstanding")) {
                        return List.of(mapper.mapRow(instA1, 0), mapper.mapRow(instA2, 1), mapper.mapRow(instB1, 2));
                    } else if (sql.contains("as outTotal")) {
                        return List.of(mapper.mapRow(loanA, 0), mapper.mapRow(loanC, 1), mapper.mapRow(loanB, 2));
                    }
                    return List.of();
                });

        final Page<CredXOverdueClientData> page = credXLoanReadPlatformService.retrieveCrediblexOverdueLoans(0, 50, null);

        assertEquals(2, page.getTotalFilteredRecords());
        assertEquals(2, page.getPageItems().size());

        final CredXOverdueClientData acme = page.getPageItems().stream().filter(c -> c.getClientId() == 12L).findFirst().orElseThrow();
        assertEquals("Acme Trading LLC", acme.getClientName());
        assertEquals("AED", acme.getCurrencyCode());
        assertEquals(2, acme.getLoans().size());

        final CredXOverdueLoanData overdueLoan = acme.getLoans().stream().filter(l -> l.getLoanId() == 1L).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, overdueLoan.getIsOverdue());
        assertEquals(2, overdueLoan.getOverdueInstallments().size());
        // Loan A overdue = 2 x (principal 9000, interest 900, fees 100, lpi 100).
        assertEquals(new BigDecimal("18000"), overdueLoan.getOverdue().getPrincipal());
        assertEquals(new BigDecimal("200"), overdueLoan.getOverdue().getLpi());
        assertEquals(sumOf(overdueLoan.getOverdue()), overdueLoan.getOverdue().getTotal());

        final CredXOverdueLoanData currentLoan = acme.getLoans().stream().filter(l -> l.getLoanId() == 2L).findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, currentLoan.getIsOverdue());
        assertEquals(BigDecimal.ZERO, currentLoan.getOverdue().getTotal());
        Assertions.assertTrue(currentLoan.getOverdueInstallments().isEmpty());

        // Client summary reconciles with the component-wise sum of its loans.
        assertEquals(new BigDecimal("138000"), acme.getSummary().getTotalOutstanding().getPrincipal()); // 100000 +
                                                                                                        // 38000
        assertEquals(new BigDecimal("147000"), acme.getSummary().getTotalOutstanding().getTotal()); // 107000 + 40000
        assertEquals(new BigDecimal("18000"), acme.getSummary().getTotalOverdue().getPrincipal());
        assertEquals(new BigDecimal("20200"), acme.getSummary().getTotalOverdue().getTotal());
        assertEquals(acme.getSummary().getTotalOverdue().getLpi(), acme.getSummary().getTotalLpiOutstanding());
    }

    private static BigDecimal sumOf(final com.crediblex.fineract.portfolio.loanaccount.data.CredXOverdueAmountBreakdown b) {
        return b.getPrincipal().add(b.getInterest()).add(b.getFees()).add(b.getLpi());
    }

    private static ResultSet clientRow(final long clientId, final String name, final String accountNo) throws SQLException {
        final ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getLong("clientId")).thenReturn(clientId);
        when(rs.getString("clientName")).thenReturn(name);
        when(rs.getString("accountNo")).thenReturn(accountNo);
        return rs;
    }

    private static ResultSet loanRow(final long clientId, final long loanId, final boolean overdue, final String principal,
            final String interest, final String fees, final String lpi, final String total) throws SQLException {
        // Lenient: the mapper reads loanOfficerId/productId via JdbcSupport.getLong, which resolves columns by INDEX
        // (findColumn -> getLong(index)); those index reads would otherwise trip strict stubbing. clientId (also read
        // via
        // JdbcSupport.getLong) is wired through the index path so grouping still sees the right client.
        final ResultSet rs = Mockito.mock(ResultSet.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        when(rs.findColumn("clientId")).thenReturn(1);
        when(rs.getLong(1)).thenReturn(clientId);
        when(rs.getLong("loanId")).thenReturn(loanId);
        when(rs.getString("currencyCode")).thenReturn("AED");
        when(rs.getInt("isOverdue")).thenReturn(overdue ? 1 : 0);
        when(rs.getBigDecimal("outPrincipal")).thenReturn(new BigDecimal(principal));
        when(rs.getBigDecimal("outInterest")).thenReturn(new BigDecimal(interest));
        when(rs.getBigDecimal("outFees")).thenReturn(new BigDecimal(fees));
        when(rs.getBigDecimal("outLpi")).thenReturn(new BigDecimal(lpi));
        when(rs.getBigDecimal("outTotal")).thenReturn(new BigDecimal(total));
        return rs;
    }

    private static ResultSet installmentRow(final long loanId, final String principal, final String interest, final String fees,
            final String lpi) throws SQLException {
        // Lenient: the mapper also reads emiAmount/excessAmount/dueDate/installmentNumber which this fixture leaves at
        // their defaults; those unstubbed reads would otherwise trip strict stubbing.
        final ResultSet rs = Mockito.mock(ResultSet.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        when(rs.getLong("loanId")).thenReturn(loanId);
        when(rs.getBigDecimal("principalOutstanding")).thenReturn(new BigDecimal(principal));
        when(rs.getBigDecimal("interestOutstanding")).thenReturn(new BigDecimal(interest));
        when(rs.getBigDecimal("feesOutstanding")).thenReturn(new BigDecimal(fees));
        when(rs.getBigDecimal("lpiOutstanding")).thenReturn(new BigDecimal(lpi));
        return rs;
    }

    @SuppressWarnings("unchecked")
    private void stubSummaryQueryWithRow(final ResultSet rs) {
        when(jdbcTemplate.queryForObject(anyString(),
                any(org.springframework.jdbc.core.RowMapper.class))).thenAnswer(invocation -> {
                    final org.springframework.jdbc.core.RowMapper<CredXOverdueLoansSummaryData> mapper = invocation.getArgument(1);
                    return mapper.mapRow(rs, 0);
                });
    }

    @Test
    public void testRetrieveCrediblexOverdueCollectedReturnsRowsAndCount() throws SQLException {
        final ResultSet row1 = collectedRow(199L, "CITY FAMOUS TYRE REPAIR LLC-OPC", 232L, 29320L, 300, "2026-01-26", "20762.72", "843.75",
                28);
        final ResultSet row2 = collectedRow(430L, "Blue Apple Advertising FZ LLC", 298L, 19360L, 300, "2026-06-13", "162684.82", "4639.45",
                75);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(2);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    final String sql = invocation.getArgument(0);
                    final org.springframework.jdbc.core.RowMapper<Object> mapper = invocation.getArgument(1);
                    if (sql.contains("as lpiPaid")) {
                        return List.of(mapper.mapRow(row1, 0), mapper.mapRow(row2, 1));
                    }
                    return List.of();
                });

        final Page<CredXOverdueCollectedData> page = credXLoanReadPlatformService.retrieveCrediblexOverdueCollected(0, 50, "2026-01-01",
                "2026-07-21", null, null);

        assertEquals(2, page.getTotalFilteredRecords());
        assertEquals(2, page.getPageItems().size());

        final CredXOverdueCollectedData r1 = page.getPageItems().get(0);
        assertEquals(Long.valueOf(199L), r1.getClientId());
        assertEquals("CITY FAMOUS TYRE REPAIR LLC-OPC", r1.getClientName());
        assertEquals(Long.valueOf(232L), r1.getLoanId());
        assertEquals(Long.valueOf(29320L), r1.getTransactionId());
        assertEquals(Integer.valueOf(300), r1.getLoanStatusId());
        assertEquals("2026-01-26", r1.getTransactionDate());
        assertEquals(new BigDecimal("20762.72"), r1.getPrincipalPaid());
        assertEquals(new BigDecimal("843.75"), r1.getLpiPaid());
        assertEquals(Integer.valueOf(28), r1.getMaxDaysOverdueAtPayment());
    }

    private static ResultSet collectedRow(final long clientId, final String clientName, final long loanId, final long txId,
            final int loanStatusId, final String transactionDate, final String principalPaid, final String lpiPaid, final int maxDays)
            throws SQLException {
        // Lenient: the mapper reads loanStatusId via JdbcSupport.getInteger (index-based findColumn -> getInt(index))
        // and
        // several columns this fixture leaves at defaults; those would otherwise trip strict stubbing.
        final ResultSet rs = Mockito.mock(ResultSet.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        when(rs.getLong("clientId")).thenReturn(clientId);
        when(rs.getString("clientName")).thenReturn(clientName);
        when(rs.getLong("loanId")).thenReturn(loanId);
        when(rs.getLong("transactionId")).thenReturn(txId);
        when(rs.findColumn("loanStatusId")).thenReturn(1);
        when(rs.getInt(1)).thenReturn(loanStatusId);
        when(rs.getDate("transactionDate")).thenReturn(Date.valueOf(transactionDate));
        when(rs.getBigDecimal("principalPaid")).thenReturn(new BigDecimal(principalPaid));
        when(rs.getBigDecimal("lpiPaid")).thenReturn(new BigDecimal(lpiPaid));
        when(rs.getInt("maxDaysOverdueAtPayment")).thenReturn(maxDays);
        return rs;
    }

    @Test
    public void testRetrieveCrediblexOverdueCollectedSummaryWindows() throws SQLException {
        // Single aggregate row: all-time totals + last-7 / last-30 conditional sums.
        final ResultSet rs = Mockito.mock(ResultSet.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        when(rs.getLong("allCount")).thenReturn(5L);
        when(rs.getBigDecimal("allPrincipal")).thenReturn(new BigDecimal("50000"));
        when(rs.getBigDecimal("allInterest")).thenReturn(new BigDecimal("8000"));
        when(rs.getBigDecimal("allFees")).thenReturn(new BigDecimal("100"));
        when(rs.getBigDecimal("allLpi")).thenReturn(new BigDecimal("1000"));
        when(rs.getLong("count7")).thenReturn(2L);
        when(rs.getBigDecimal("lpi7")).thenReturn(new BigDecimal("300"));
        when(rs.getLong("count30")).thenReturn(4L);
        when(rs.getBigDecimal("lpi30")).thenReturn(new BigDecimal("800"));

        when(jdbcTemplate.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    final org.springframework.jdbc.core.RowMapper<Object> mapper = invocation.getArgument(1);
                    return mapper.mapRow(rs, 0);
                });

        final CredXOverdueCollectedSummaryData summary = credXLoanReadPlatformService.retrieveCrediblexOverdueCollectedSummary(null, null);

        assertEquals("AED", summary.getCurrencyCode());
        Assertions.assertNotNull(summary.getBusinessDate());
        Assertions.assertNotNull(summary.getLast7DaysFrom());
        Assertions.assertNotNull(summary.getLast30DaysFrom());

        assertEquals(Long.valueOf(5L), summary.getCollected().getAllTime().getCount());
        assertEquals(new BigDecimal("1000"), summary.getCollected().getAllTime().getLpi());
        // total = principal + interest + fees + lpi = 50000 + 8000 + 100 + 1000
        assertEquals(new BigDecimal("59100"), summary.getCollected().getAllTime().getTotal());

        assertEquals(Long.valueOf(2L), summary.getCollected().getLast7Days().getCount());
        assertEquals(new BigDecimal("300"), summary.getCollected().getLast7Days().getLpi());
        assertEquals(Long.valueOf(4L), summary.getCollected().getLast30Days().getCount());
        assertEquals(new BigDecimal("800"), summary.getCollected().getLast30Days().getLpi());
    }

    @Test
    void retrieveAllLoansWithOverdueInstallments_filtersToInstallmentsWithOutstandingBalance() {
        when(sqlGenerator.currentBusinessDate()).thenReturn("CURRENT_DATE");
        when(sqlGenerator.subDate(anyString(), anyString(), anyString())).thenReturn("DATE_SUB(CURRENT_DATE, INTERVAL ? DAY)");
        when(jdbcTemplate.query(anyString(), any(org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformServiceImpl.MusoniOverdueLoanScheduleMapper.class),
                any(Object[].class))).thenReturn(List.of());

        credXLoanReadPlatformService.retrieveAllLoansWithOverdueInstallments(0L, true);

        final org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(),
                any(org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformServiceImpl.MusoniOverdueLoanScheduleMapper.class),
                eq(0L));
        assertPenaltyJobEligibleOverdueInstallmentSql(sqlCaptor.getValue(), true, false);
    }

    @Test
    void retrieveAllLoansWithOverdueInstallments_withoutBackdate_addsDueDateLowerBound() {
        when(sqlGenerator.currentBusinessDate()).thenReturn("CURRENT_DATE");
        when(sqlGenerator.subDate(anyString(), anyString(), anyString())).thenReturn("DATE_SUB(CURRENT_DATE, INTERVAL ? DAY)");
        when(jdbcTemplate.query(anyString(), any(org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformServiceImpl.MusoniOverdueLoanScheduleMapper.class),
                any(Object[].class))).thenReturn(List.of());

        credXLoanReadPlatformService.retrieveAllLoansWithOverdueInstallments(2L, false);

        final org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(),
                any(org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformServiceImpl.MusoniOverdueLoanScheduleMapper.class),
                eq(2L), eq(2L));
        assertPenaltyJobEligibleOverdueInstallmentSql(sqlCaptor.getValue(), false, false);
    }

    @Test
    void retrieveLoanOverdueInstallments_filtersToInstallmentsWithOutstandingBalance() {
        when(sqlGenerator.currentBusinessDate()).thenReturn("CURRENT_DATE");
        when(sqlGenerator.subDate(anyString(), anyString(), anyString())).thenReturn("DATE_SUB(CURRENT_DATE, INTERVAL ? DAY)");
        when(jdbcTemplate.query(anyString(), any(org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformServiceImpl.MusoniOverdueLoanScheduleMapper.class),
                any(Object[].class))).thenReturn(List.of());

        credXLoanReadPlatformService.retrieveLoanOverdueInstallments(42L, 1L, true);

        final org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(),
                any(org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformServiceImpl.MusoniOverdueLoanScheduleMapper.class),
                eq(1L), eq(42L));
        assertPenaltyJobEligibleOverdueInstallmentSql(sqlCaptor.getValue(), true, true);
    }

    private void assertPenaltyJobEligibleOverdueInstallmentSql(final String sql, final boolean backdatePenalties,
            final boolean perLoanQuery) {
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("ls.completed_derived <> true"),
                "SQL should exclude completed installments");
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("ls.recalculated_interest_component <> true"),
                "SQL should exclude recalculated interest installments");
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("mc.charge_time_enum = 9"), "SQL should filter to overdue charge time");
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("principal_completed_derived"),
                "SQL should compute principal outstanding from completed derived");
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("interest_completed_derived"),
                "SQL should compute interest outstanding from completed derived");
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("penalty_charges_completed_derived"),
                "SQL should compute LPI outstanding from completed derived");
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("> 0 or"), "SQL should require chargeable outstanding on any component");
        if (perLoanQuery) {
            org.junit.jupiter.api.Assertions.assertTrue(sql.contains("ml.id = ?"), "SQL should filter to the requested loan");
        }
        if (!backdatePenalties) {
            org.junit.jupiter.api.Assertions.assertTrue(sql.contains("ls.duedate >="),
                    "SQL should bound due date when backdate penalties is disabled");
        }
    }
}
