package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BackdatedSettlementLpiPolicyTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);

    @BeforeEach
    void setUp() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 9, 28));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "default", "UTC", null));
        final ConfigurationDomainService cfg = mock(ConfigurationDomainService.class);
        when(cfg.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", cfg);
        moneyHelper.initialize();
    }

    @Test
    void backdatedAmountBelowCloseTotalIsPartial() {
        final Loan loan = loanOutstanding("100000.00", "3287.67");

        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("15000.00")))
                .isTrue();
    }

    @Test
    void amountThatClosesTheLoanAsOfTheValueDateIsNotPartial() {
        final Loan loan = loanOutstanding("100000.00", "3287.67");

        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("103287.67")))
                .isFalse();
    }

    @Test
    void oneCentShortOfTheCloseAmountIsStillAFullClose() {
        final Loan loan = loanOutstanding("1000.00", "0.00");

        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("999.99")))
                .isFalse();
    }

    @Test
    void twoCentsShortOfTheCloseAmountIsPartial() {
        final Loan loan = loanOutstanding("1000.00", "0.00");

        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("999.98")))
                .isTrue();
    }

    @Test
    void closeAmountIncludesLpiStrictlyBeforeTheValueDateAndExcludesLaterDays() {
        final Loan loan = loanOutstanding("100.00", "0.00");
        final Set<LoanCharge> charges = new LinkedHashSet<>();
        charges.add(penaltyCharge(LocalDate.of(2026, 8, 9), "82.19"));
        charges.add(penaltyCharge(LocalDate.of(2026, 8, 10), "82.19"));
        when(loan.getActiveCharges()).thenReturn(charges);

        assertThat(BackdatedSettlementLpiPolicy.amountRequiredToClose(loan, LocalDate.of(2026, 8, 10)).getAmount())
                .isEqualByComparingTo("182.19");
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("150.00")))
                .isTrue();
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("182.19")))
                .isFalse();
    }

    @Test
    void nullInterestFeeAndTaxAreTreatedAsZero() {
        final Loan loan = loanOutstanding("100.00", null);
        when(loan.getSummary().getTotalFeeChargesOutstanding()).thenReturn(null);
        when(loan.getSummary().getTotalTaxChargesOutstanding()).thenReturn(null);

        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("100.00")))
                .isFalse();
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("50.00")))
                .isTrue();
    }

    @Test
    void missingInputsAndALoanThatCannotBePricedAreNotPartial() {
        final Loan loan = loanOutstanding("100000.00", "0.00");
        final LocalDate valueDate = LocalDate.of(2026, 8, 10);

        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(null, valueDate, new BigDecimal("1"))).isFalse();
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, null, new BigDecimal("1"))).isFalse();
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, valueDate, null)).isFalse();
        when(loan.getCurrency()).thenReturn(null);
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, valueDate, new BigDecimal("1"))).isFalse();

        final Loan noSummary = mock(Loan.class);
        when(noSummary.getCurrency()).thenReturn(currency);
        when(noSummary.getSummary()).thenReturn(null);
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(noSummary, valueDate, new BigDecimal("1"))).isFalse();

        final Loan nothingDue = loanOutstanding("0.00", "0.00");
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(nothingDue, valueDate, new BigDecimal("1"))).isFalse();
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loanOutstanding("100.00", "0.00"), LocalDate.of(2026, 9, 29),
                new BigDecimal("1"))).isFalse();
    }

    @Test
    void lms146And147_feroPartialOf81000On11SepStaysPartial() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 9, 22));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        final Loan loan = loanOutstanding("217054.01", "0.00");
        final Set<LoanCharge> charges = new LinkedHashSet<>();
        charges.add(penaltyCharge(LocalDate.of(2026, 9, 9), "178.40"));
        charges.add(penaltyCharge(LocalDate.of(2026, 9, 10), "178.40"));
        charges.add(penaltyCharge(LocalDate.of(2026, 9, 11), "178.40"));
        when(loan.getActiveCharges()).thenReturn(charges);

        assertThat(BackdatedSettlementLpiPolicy.amountRequiredToClose(loan, LocalDate.of(2026, 9, 11)).getAmount())
                .isEqualByComparingTo("217410.81");
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 9, 11), new BigDecimal("81000.820")))
                .isTrue();
    }

    @Test
    void lms132_partialOnTheDueDateDoesNotTakeTheFullWaivePath() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 9, 3));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        final Loan loan = loanOutstanding("546036.05", "170602.80");

        assertThat(BackdatedSettlementLpiPolicy.amountRequiredToClose(loan, LocalDate.of(2026, 8, 22)).getAmount())
                .isEqualByComparingTo("716638.85");
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 22), new BigDecimal("293987.85")))
                .isTrue();
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 22), new BigDecimal("716638.85")))
                .isFalse();
    }

    @Test
    void lms150_fifteenThousandOn10AugIsPartialAndTheFullCloseIsNot() {
        final Loan loan = loanOutstanding("100000.00", "3287.67");
        final Set<LoanCharge> charges = new LinkedHashSet<>();
        charges.add(penaltyCharge(LocalDate.of(2026, 7, 31), "82.19"));
        charges.add(penaltyCharge(LocalDate.of(2026, 8, 9), "739.71"));
        charges.add(penaltyCharge(LocalDate.of(2026, 8, 10), "82.19"));
        when(loan.getActiveCharges()).thenReturn(charges);

        assertThat(BackdatedSettlementLpiPolicy.amountRequiredToClose(loan, LocalDate.of(2026, 8, 10)).getAmount())
                .isEqualByComparingTo("104109.57");
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("15000.00")))
                .isTrue();
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("104109.57")))
                .isFalse();
    }

    @Test
    void closeAmountIncludesLpiDatedBeforeSettlementEvenWhenItsInstallmentIsDueLater() {
        final Loan loan = loanOutstanding("100.00", "0.00");
        final LoanCharge charge = penaltyCharge(LocalDate.of(2026, 8, 5), "82.19");
        when(charge.getOverdueInstallmentCharge().getInstallment().getDueDate()).thenReturn(LocalDate.of(2026, 9, 30));
        when(loan.getActiveCharges()).thenReturn(Set.of(charge));

        assertThat(BackdatedSettlementLpiPolicy.amountRequiredToClose(loan, LocalDate.of(2026, 8, 10)).getAmount())
                .isEqualByComparingTo("182.19");
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 8, 10), new BigDecimal("100.00")))
                .isTrue();
    }

    @Test
    void sameDayPartialOnTheDueDateKeepsLaterLpiAndAFullCloseStillWaives() {
        final Loan loan = loanOutstanding("100000.00", "3287.67");
        final LocalDate dueDate = LocalDate.of(2026, 9, 28);

        assertThat(BackdatedSettlementLpiPolicy.keepsLaterLpi(loan, dueDate, new BigDecimal("15000.00"), true)).isTrue();
        assertThat(BackdatedSettlementLpiPolicy.keepsLaterLpi(loan, dueDate, new BigDecimal("103287.67"), true)).isFalse();
        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, dueDate, new BigDecimal("15000.00"))).isFalse();
    }

    @Test
    void sameDayPaymentIsNotAPartialBackdatedSettlement() {
        final Loan loan = loanOutstanding("100000.00", "3287.67");

        assertThat(BackdatedSettlementLpiPolicy.isPartialBackdatedRepayment(loan, LocalDate.of(2026, 9, 28), new BigDecimal("15000.00")))
                .isFalse();
    }

    private Loan loanOutstanding(final String principal, final String interest) {
        final Loan loan = mock(Loan.class);
        final LoanSummary summary = mock(LoanSummary.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getSummary()).thenReturn(summary);
        when(loan.getActiveCharges()).thenReturn(Set.of());
        when(summary.getTotalPrincipalOutstanding()).thenReturn(new BigDecimal(principal));
        when(summary.getTotalInterestOutstanding()).thenReturn(interest == null ? null : new BigDecimal(interest));
        when(summary.getTotalFeeChargesOutstanding()).thenReturn(BigDecimal.ZERO);
        when(summary.getTotalTaxChargesOutstanding()).thenReturn(BigDecimal.ZERO);
        return loan;
    }

    private LoanCharge penaltyCharge(final LocalDate accrualDate, final String outstanding) {
        final LoanCharge charge = mock(LoanCharge.class);
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        final LoanOverdueInstallmentCharge link = mock(LoanOverdueInstallmentCharge.class);
        when(charge.isPenaltyCharge()).thenReturn(true);
        when(charge.getDueDate()).thenReturn(accrualDate);
        when(charge.getAmountOutstanding(currency)).thenReturn(Money.of(currency, new BigDecimal(outstanding)));
        when(charge.isOverdueInstallmentCharge()).thenReturn(true);
        when(installment.getDueDate()).thenReturn(LocalDate.of(2026, 7, 31));
        when(link.getInstallment()).thenReturn(installment);
        when(charge.getOverdueInstallmentCharge()).thenReturn(link);
        return charge;
    }
}
