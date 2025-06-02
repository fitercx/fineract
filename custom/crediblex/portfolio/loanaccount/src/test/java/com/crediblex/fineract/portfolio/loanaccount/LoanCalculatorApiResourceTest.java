package com.crediblex.fineract.portfolio.loanaccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.crediblex.fineract.portfolio.loanaccount.api.LoanCalculatorApiResource;
import org.apache.fineract.infrastructure.core.api.JsonQuery;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleCalculationPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LoanCalculatorApiResourceTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private FromJsonHelper fromJsonHelper;

    @Mock
    private LoanScheduleCalculationPlatformService calculationPlatformService;

    @Mock
    private DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer;

    @Mock
    private AppUser appUser;

    @Mock
    private LoanScheduleModel loanScheduleModel;

    private LoanCalculatorApiResource loanCalculatorApiResource;

    private final LocalDate BUSINESS_DATE = LocalDate.of(2025, 5, 1);

    @BeforeEach
    public void setUp() {
        loanCalculatorApiResource = new LoanCalculatorApiResource(
                context, fromJsonHelper, calculationPlatformService, toApiJsonSerializer);

        when(context.authenticatedUser()).thenReturn(appUser);
    }

    @Test
    public void testCalculateLoan_WithMinimalRequest() {
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class);
             MockedStatic<MoneyHelper> moneyHelperMock = mockStatic(MoneyHelper.class)) {

            // Mock DateUtils to return a fixed business date
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBeforeBusinessDate(any(LocalDate.class))).thenReturn(false);

            // Mock MoneyHelper to return a MathContext
            moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new MathContext(8, RoundingMode.HALF_EVEN));

            // Given
            String requestJson = "{"
                    + "\"principal\": 1000000,"
                    + "\"loanTermFrequency\": 12,"
                    + "\"loanTermFrequencyType\": 2,"
                    + "\"numberOfRepayments\": 12,"
                    + "\"interestRatePerPeriod\": 25,"
                    + "\"interestType\": 1"
                    + "}";

            JsonObject jsonObject = JsonParser.parseString(requestJson).getAsJsonObject();

            when(fromJsonHelper.parse(requestJson)).thenReturn(jsonObject);

            // Create empty JsonArray for charges and collateral
            JsonArray emptyArray = new JsonArray();
            when(fromJsonHelper.extractJsonArrayNamed("charges", jsonObject)).thenReturn(null);
            when(fromJsonHelper.extractJsonArrayNamed("collateral", jsonObject)).thenReturn(null);

            // Mock the loan schedule calculation
            LoanScheduleData loanScheduleData = createSampleLoanScheduleData();
            when(loanScheduleModel.toData()).thenReturn(loanScheduleData);
            when(calculationPlatformService.calculateLoanSchedule(any(JsonQuery.class), eq(true)))
                    .thenReturn(loanScheduleModel);

            // Mock the serialization
            Map<String, Object> expectedResult = Map.of(
                    "installmentAmount", new BigDecimal("91666.67"),
                    "interestAmount", new BigDecimal("100000.00"),
                    "repaymentAmount", new BigDecimal("1100000.00")
            );
            when(toApiJsonSerializer.serialize(any(Map.class))).thenReturn("{\"installmentAmount\":91666.67,\"interestAmount\":100000.00,\"repaymentAmount\":1100000.00}");

            // When
            String result = loanCalculatorApiResource.calculateLoan(requestJson);

            // Then
            //verify(appUser).validateHasReadPermission("LOAN_CALCULATOR");

            // Capture the JsonQuery passed to the calculation service
            ArgumentCaptor<JsonQuery> jsonQueryCaptor = ArgumentCaptor.forClass(JsonQuery.class);
            verify(calculationPlatformService).calculateLoanSchedule(jsonQueryCaptor.capture(), eq(true));

            // Verify the JsonQuery contains all required fields
            JsonElement capturedJsonElement = jsonQueryCaptor.getValue().parsedJson();
            assertTrue(capturedJsonElement.isJsonObject());
            JsonObject capturedJsonObject = capturedJsonElement.getAsJsonObject();

            assertNotNull(capturedJsonObject.get("submittedOnDate"));
            assertNotNull(capturedJsonObject.get("expectedDisbursementDate"));
            assertNotNull(capturedJsonObject.get("transactionProcessingStrategyCode"));
            assertNotNull(capturedJsonObject.get("loanType"));
            assertNotNull(capturedJsonObject.get("dateFormat"));
            assertNotNull(capturedJsonObject.get("locale"));

            // Verify the result
            assertNotNull(result);
            assertEquals("{\"installmentAmount\":91666.67,\"interestAmount\":100000.00,\"repaymentAmount\":1100000.00}", result);
        }
    }

    @Test
    public void testCalculateLoan_WithFullRequest() {
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class);
             MockedStatic<MoneyHelper> moneyHelperMock = mockStatic(MoneyHelper.class)) {

            // Mock DateUtils to return a fixed business date
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBeforeBusinessDate(any(LocalDate.class))).thenReturn(false);

            // Mock MoneyHelper to return a MathContext
            moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new MathContext(8, RoundingMode.HALF_EVEN));

            // Given
            String requestJson = "{"
                    + "\"productId\": 1,"
                    + "\"loanOfficerId\": \"\","
                    + "\"loanPurposeId\": \"\","
                    + "\"fundId\": \"\","
                    + "\"submittedOnDate\": \"19 May 2025\","
                    + "\"expectedDisbursementDate\": \"19 May 2025\","
                    + "\"externalId\": \"\","
                    + "\"linkAccountId\": \"\","
                    + "\"createStandingInstructionAtDisbursement\": \"\","
                    + "\"loanTermFrequency\": 12,"
                    + "\"loanTermFrequencyType\": 2,"
                    + "\"numberOfRepayments\": 12,"
                    + "\"repaymentEvery\": 1,"
                    + "\"repaymentFrequencyType\": 2,"
                    + "\"repaymentFrequencyNthDayType\": \"\","
                    + "\"repaymentFrequencyDayOfWeekType\": \"\","
                    + "\"repaymentsStartingFromDate\": null,"
                    + "\"interestChargedFromDate\": null,"
                    + "\"interestType\": 1,"
                    + "\"isEqualAmortization\": false,"
                    + "\"amortizationType\": 1,"
                    + "\"interestCalculationPeriodType\": 1,"
                    + "\"loanIdToClose\": \"\","
                    + "\"isTopup\": \"\","
                    + "\"transactionProcessingStrategyCode\": \"mifos-standard-strategy\","
                    + "\"interestRateFrequencyType\": 3,"
                    + "\"interestRatePerPeriod\": 25,"
                    + "\"charges\": [],"
                    + "\"collateral\": [],"
                    + "\"dateFormat\": \"dd MMMM yyyy\","
                    + "\"locale\": \"en\","
                    + "\"clientId\": 3,"
                    + "\"loanType\": \"individual\","
                    + "\"principal\": 1000000,"
                    + "\"allowPartialPeriodInterestCalcualtion\": false"
                    + "}";

            JsonObject jsonObject = JsonParser.parseString(requestJson).getAsJsonObject();

            when(fromJsonHelper.parse(requestJson)).thenReturn(jsonObject);

            // Mock the loan schedule calculation
            LoanScheduleData loanScheduleData = createSampleLoanScheduleData();
            when(loanScheduleModel.toData()).thenReturn(loanScheduleData);
            when(calculationPlatformService.calculateLoanSchedule(any(JsonQuery.class), eq(true)))
                    .thenReturn(loanScheduleModel);

            // Mock the serialization
            Map<String, Object> expectedResult = Map.of(
                    "installmentAmount", new BigDecimal("91666.67"),
                    "interestAmount", new BigDecimal("100000.00"),
                    "repaymentAmount", new BigDecimal("1100000.00")
            );
            when(toApiJsonSerializer.serialize(any(Map.class))).thenReturn("{\"installmentAmount\":91666.67,\"interestAmount\":100000.00,\"repaymentAmount\":1100000.00}");

            // When
            String result = loanCalculatorApiResource.calculateLoan(requestJson);

            // Then
            //verify(appUser).validateHasReadPermission("LOAN_CALCULATOR");

            // Verify the calculation service was called with the correct parameters
            ArgumentCaptor<JsonQuery> jsonQueryCaptor = ArgumentCaptor.forClass(JsonQuery.class);
            verify(calculationPlatformService).calculateLoanSchedule(jsonQueryCaptor.capture(), eq(true));

            // Verify the JsonQuery contains all the fields from the original request
            JsonElement capturedJsonElement = jsonQueryCaptor.getValue().parsedJson();
            assertTrue(capturedJsonElement.isJsonObject());
            JsonObject capturedJsonObject = capturedJsonElement.getAsJsonObject();

            assertEquals(1, capturedJsonObject.get("productId").getAsInt());
            assertEquals("19 May 2025", capturedJsonObject.get("submittedOnDate").getAsString());
            assertEquals("19 May 2025", capturedJsonObject.get("expectedDisbursementDate").getAsString());
            assertEquals(12, capturedJsonObject.get("loanTermFrequency").getAsInt());
            assertEquals(2, capturedJsonObject.get("loanTermFrequencyType").getAsInt());
            assertEquals(12, capturedJsonObject.get("numberOfRepayments").getAsInt());
            assertEquals(1, capturedJsonObject.get("repaymentEvery").getAsInt());
            assertEquals(2, capturedJsonObject.get("repaymentFrequencyType").getAsInt());
            assertEquals(1, capturedJsonObject.get("interestType").getAsInt());
            assertEquals(1, capturedJsonObject.get("amortizationType").getAsInt());
            assertEquals(1, capturedJsonObject.get("interestCalculationPeriodType").getAsInt());
            assertEquals("mifos-standard-strategy", capturedJsonObject.get("transactionProcessingStrategyCode").getAsString());
            assertEquals(3, capturedJsonObject.get("interestRateFrequencyType").getAsInt());
            assertEquals(25, capturedJsonObject.get("interestRatePerPeriod").getAsInt());
            assertEquals("dd MMMM yyyy", capturedJsonObject.get("dateFormat").getAsString());
            assertEquals("en", capturedJsonObject.get("locale").getAsString());
            assertEquals("individual", capturedJsonObject.get("loanType").getAsString());
            assertEquals(1000000, capturedJsonObject.get("principal").getAsInt());

            // Verify the result
            assertNotNull(result);
            assertEquals("{\"installmentAmount\":91666.67,\"interestAmount\":100000.00,\"repaymentAmount\":1100000.00}", result);
        }
    }

    @Test
    public void testCalculateLoan_WithDecliningBalanceInterest() {
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class);
             MockedStatic<MoneyHelper> moneyHelperMock = mockStatic(MoneyHelper.class)) {

            // Mock DateUtils to return a fixed business date
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBeforeBusinessDate(any(LocalDate.class))).thenReturn(false);

            // Mock MoneyHelper to return a MathContext
            moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new MathContext(8, RoundingMode.HALF_EVEN));

            // Given
            String requestJson = "{"
                    + "\"principal\": 1000000,"
                    + "\"loanTermFrequency\": 12,"
                    + "\"loanTermFrequencyType\": 2,"
                    + "\"numberOfRepayments\": 12,"
                    + "\"repaymentEvery\": 1,"
                    + "\"repaymentFrequencyType\": 2,"
                    + "\"interestRatePerPeriod\": 10,"
                    + "\"amortizationType\": 1,"
                    + "\"interestType\": 0,"  // Declining Balance
                    + "\"interestCalculationPeriodType\": 1,"
                    + "\"transactionProcessingStrategyCode\": \"mifos-standard-strategy\","
                    + "\"dateFormat\": \"dd MMMM yyyy\","
                    + "\"locale\": \"en\""
                    + "}";

            JsonObject jsonObject = JsonParser.parseString(requestJson).getAsJsonObject();

            when(fromJsonHelper.parse(requestJson)).thenReturn(jsonObject);
            when(fromJsonHelper.extractJsonArrayNamed("charges", jsonObject)).thenReturn(null);
            when(fromJsonHelper.extractJsonArrayNamed("collateral", jsonObject)).thenReturn(null);

            // Mock the loan schedule calculation with declining balance
            LoanScheduleData loanScheduleData = createDecliningBalanceLoanScheduleData();
            when(loanScheduleModel.toData()).thenReturn(loanScheduleData);
            when(calculationPlatformService.calculateLoanSchedule(any(JsonQuery.class), eq(true)))
                    .thenReturn(loanScheduleModel);

            // Mock the serialization
            Map<String, Object> expectedResult = Map.of(
                    "installmentAmount", new BigDecimal("87916.67"),
                    "interestAmount", new BigDecimal("55000.00"),
                    "repaymentAmount", new BigDecimal("1055000.00")
            );
            when(toApiJsonSerializer.serialize(any(Map.class))).thenReturn("{\"installmentAmount\":87916.67,\"interestAmount\":55000.00,\"repaymentAmount\":1055000.00}");

            // When
            String result = loanCalculatorApiResource.calculateLoan(requestJson);

            // Then
            //verify(appUser).validateHasReadPermission("LOAN_CALCULATOR");

            // Verify the calculation service was called with the correct parameters
            ArgumentCaptor<JsonQuery> jsonQueryCaptor = ArgumentCaptor.forClass(JsonQuery.class);
            verify(calculationPlatformService).calculateLoanSchedule(jsonQueryCaptor.capture(), eq(true));

            // Verify the JsonQuery contains the correct interest type
            JsonElement capturedJsonElement = jsonQueryCaptor.getValue().parsedJson();
            assertTrue(capturedJsonElement.isJsonObject());
            JsonObject capturedJsonObject = capturedJsonElement.getAsJsonObject();

            assertEquals(0, capturedJsonObject.get("interestType").getAsInt());

            // Verify the result
            assertNotNull(result);
            assertEquals("{\"installmentAmount\":87916.67,\"interestAmount\":55000.00,\"repaymentAmount\":1055000.00}", result);
        }
    }

    @Test
    public void testCalculateLoan_WithCharges() {
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class);
             MockedStatic<MoneyHelper> moneyHelperMock = mockStatic(MoneyHelper.class)) {

            // Mock DateUtils to return a fixed business date
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBeforeBusinessDate(any(LocalDate.class))).thenReturn(false);

            // Mock MoneyHelper to return a MathContext
            moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new MathContext(8, RoundingMode.HALF_EVEN));

            // Given
            String requestJson = "{"
                    + "\"principal\": 1000000,"
                    + "\"loanTermFrequency\": 12,"
                    + "\"loanTermFrequencyType\": 2,"
                    + "\"numberOfRepayments\": 12,"
                    + "\"repaymentEvery\": 1,"
                    + "\"repaymentFrequencyType\": 2,"
                    + "\"interestRatePerPeriod\": 25,"
                    + "\"amortizationType\": 1,"
                    + "\"interestType\": 1,"
                    + "\"interestCalculationPeriodType\": 1,"
                    + "\"transactionProcessingStrategyCode\": \"mifos-standard-strategy\","
                    + "\"charges\": ["
                    + "  {\"chargeId\": 1, \"amount\": 10000, \"dueDate\": \"19 May 2025\"},"
                    + "  {\"chargeId\": 2, \"amount\": 5000}"
                    + "],"
                    + "\"dateFormat\": \"dd MMMM yyyy\","
                    + "\"locale\": \"en\""
                    + "}";

            JsonObject jsonObject = JsonParser.parseString(requestJson).getAsJsonObject();
            JsonArray chargesArray = JsonParser.parseString("[{\"chargeId\": 1, \"amount\": 10000, \"dueDate\": \"19 May 2025\"},{\"chargeId\": 2, \"amount\": 5000}]").getAsJsonArray();

            when(fromJsonHelper.parse(requestJson)).thenReturn(jsonObject);
            when(fromJsonHelper.extractJsonArrayNamed("charges", jsonObject)).thenReturn(chargesArray);
            when(fromJsonHelper.extractJsonArrayNamed("collateral", jsonObject)).thenReturn(null);

            // Mock the loan schedule calculation
            LoanScheduleData loanScheduleData = createSampleLoanScheduleDataWithCharges();
            when(loanScheduleModel.toData()).thenReturn(loanScheduleData);
            when(calculationPlatformService.calculateLoanSchedule(any(JsonQuery.class), eq(true)))
                    .thenReturn(loanScheduleModel);

            // Mock the serialization
            Map<String, Object> expectedResult = Map.of(
                    "installmentAmount", new BigDecimal("91666.67"),
                    "interestAmount", new BigDecimal("100000.00"),
                    "repaymentAmount", new BigDecimal("1115000.00"),
                    "chargesAmount", new BigDecimal("15000.00")
            );
            when(toApiJsonSerializer.serialize(any(Map.class))).thenReturn("{\"installmentAmount\":91666.67,\"interestAmount\":100000.00,\"repaymentAmount\":1115000.00,\"chargesAmount\":15000.00}");

            // When
            String result = loanCalculatorApiResource.calculateLoan(requestJson);

            // Then
            //verify(appUser).validateHasReadPermission("LOAN_CALCULATOR");

            // Verify the calculation service was called with the correct parameters
            ArgumentCaptor<JsonQuery> jsonQueryCaptor = ArgumentCaptor.forClass(JsonQuery.class);
            verify(calculationPlatformService).calculateLoanSchedule(jsonQueryCaptor.capture(), eq(true));

            // Verify the JsonQuery contains the charges
            JsonElement capturedJsonElement = jsonQueryCaptor.getValue().parsedJson();
            assertTrue(capturedJsonElement.isJsonObject());
            JsonObject capturedJsonObject = capturedJsonElement.getAsJsonObject();

            JsonArray capturedCharges = capturedJsonObject.getAsJsonArray("charges");
            assertNotNull(capturedCharges);
            assertEquals(2, capturedCharges.size());

            // Verify the result
            assertNotNull(result);
            assertEquals("{\"installmentAmount\":91666.67,\"interestAmount\":100000.00,\"repaymentAmount\":1115000.00,\"chargesAmount\":15000.00}", result);
        }
    }

    private void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true but was false");
        }
    }

    private LoanScheduleData createSampleLoanScheduleData() {
        Collection<LoanSchedulePeriodData> periods = new ArrayList<>();

        // Add disbursement period
        periods.add(LoanSchedulePeriodData.disbursementOnlyPeriod(
                LocalDate.of(2025, 5, 19),
                BigDecimal.valueOf(1000000),
                BigDecimal.ZERO,
                false));

        // Add repayment periods - using the correct method signature from LoanSchedulePeriodData
        LocalDate startDate = LocalDate.of(2025, 5, 19);

        for (int i = 1; i <= 12; i++) {
            // Calculate dates properly to avoid month overflow
            LocalDate fromDate = startDate.plusMonths(i - 1);
            LocalDate dueDate = startDate.plusMonths(i);

            periods.add(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    i,
                    fromDate,
                    dueDate,
                    BigDecimal.valueOf(83333.33),
                    BigDecimal.valueOf(1000000 - (i - 1) * 83333.33),
                    BigDecimal.valueOf(8333.33),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO));
        }

        // Using the correct constructor for LoanScheduleData
        return new LoanScheduleData(
                null, // CurrencyData
                periods,
                365, // loanTermInDays
                BigDecimal.valueOf(1000000), // totalPrincipalDisbursed
                BigDecimal.valueOf(1000000), // totalPrincipalExpected
                BigDecimal.valueOf(0), // totalPrincipalPaid
                BigDecimal.valueOf(100000), // totalInterestCharged
                BigDecimal.valueOf(0), // totalFeeChargesCharged
                BigDecimal.valueOf(0), // totalPenaltyChargesCharged
                BigDecimal.valueOf(0), // totalWaived
                BigDecimal.valueOf(0), // totalWrittenOff
                BigDecimal.valueOf(1100000), // totalRepaymentExpected
                BigDecimal.valueOf(0), // totalRepayment
                BigDecimal.valueOf(0), // totalPaidInAdvance
                BigDecimal.valueOf(0), // totalPaidLate
                BigDecimal.valueOf(1100000), // totalOutstanding
                BigDecimal.valueOf(0)); // totalCredits
    }

    private LoanScheduleData createDecliningBalanceLoanScheduleData() {
        Collection<LoanSchedulePeriodData> periods = new ArrayList<>();

        // Add disbursement period
        LocalDate startDate = LocalDate.of(2025, 5, 19);

        periods.add(LoanSchedulePeriodData.disbursementOnlyPeriod(
                startDate,
                BigDecimal.valueOf(1000000),
                BigDecimal.ZERO,
                false));

        // Add repayment periods with declining balance interest
        BigDecimal principal = BigDecimal.valueOf(1000000);
        BigDecimal monthlyPrincipal = BigDecimal.valueOf(83333.33);

        for (int i = 1; i <= 12; i++) {
            // Calculate dates properly to avoid month overflow
            LocalDate fromDate = startDate.plusMonths(i - 1);
            LocalDate dueDate = startDate.plusMonths(i);

            BigDecimal interest = principal.multiply(BigDecimal.valueOf(0.1 / 12));

            periods.add(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    i,
                    fromDate,
                    dueDate,
                    monthlyPrincipal,
                    principal,
                    interest,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO));

            principal = principal.subtract(monthlyPrincipal);
        }

        return new LoanScheduleData(
                null, // CurrencyData
                periods,
                365, // loanTermInDays
                BigDecimal.valueOf(1000000), // totalPrincipalDisbursed
                BigDecimal.valueOf(1000000), // totalPrincipalExpected
                BigDecimal.valueOf(0), // totalPrincipalPaid
                BigDecimal.valueOf(55000), // totalInterestCharged
                BigDecimal.valueOf(0), // totalFeeChargesCharged
                BigDecimal.valueOf(0), // totalPenaltyChargesCharged
                BigDecimal.valueOf(0), // totalWaived
                BigDecimal.valueOf(0), // totalWrittenOff
                BigDecimal.valueOf(1055000), // totalRepaymentExpected
                BigDecimal.valueOf(0), // totalRepayment
                BigDecimal.valueOf(0), // totalPaidInAdvance
                BigDecimal.valueOf(0), // totalPaidLate
                BigDecimal.valueOf(1055000), // totalOutstanding
                BigDecimal.valueOf(0)); // totalCredits
    }

    private LoanScheduleData createSampleLoanScheduleDataWithCharges() {
        Collection<LoanSchedulePeriodData> periods = new ArrayList<>();

        // Add disbursement period
        LocalDate startDate = LocalDate.of(2025, 5, 19);

        periods.add(LoanSchedulePeriodData.disbursementOnlyPeriod(
                startDate,
                BigDecimal.valueOf(1000000),
                BigDecimal.ZERO,
                false));

        // Add repayment periods - avoid using periodWithPayments which requires MoneyHelper
        for (int i = 1; i <= 12; i++) {
            // Calculate dates properly to avoid month overflow
            LocalDate fromDate = startDate.plusMonths(i - 1);
            LocalDate dueDate = startDate.plusMonths(i);

            BigDecimal fee = (i == 1) ? BigDecimal.valueOf(15000) : BigDecimal.ZERO;

            // Use repaymentOnlyPeriod instead of periodWithPayments to avoid MoneyHelper dependency
            periods.add(LoanSchedulePeriodData.repaymentOnlyPeriod(
                    i,
                    fromDate,
                    dueDate,
                    BigDecimal.valueOf(83333.33),
                    BigDecimal.valueOf(1000000 - (i - 1) * 83333.33),
                    BigDecimal.valueOf(8333.33),
                    fee,
                    BigDecimal.ZERO));
        }

        return new LoanScheduleData(
                null, // CurrencyData
                periods,
                365, // loanTermInDays
                BigDecimal.valueOf(1000000), // totalPrincipalDisbursed
                BigDecimal.valueOf(1000000), // totalPrincipalExpected
                BigDecimal.valueOf(0), // totalPrincipalPaid
                BigDecimal.valueOf(100000), // totalInterestCharged
                BigDecimal.valueOf(15000), // totalFeeChargesCharged
                BigDecimal.valueOf(0), // totalPenaltyChargesCharged
                BigDecimal.valueOf(0), // totalWaived
                BigDecimal.valueOf(0), // totalWrittenOff
                BigDecimal.valueOf(1115000), // totalRepaymentExpected
                BigDecimal.valueOf(0), // totalRepayment
                BigDecimal.valueOf(0), // totalPaidInAdvance
                BigDecimal.valueOf(0), // totalPaidLate
                BigDecimal.valueOf(1115000), // totalOutstanding
                BigDecimal.valueOf(0)); // totalCredits
    }
}