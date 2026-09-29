package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PartialBackdatedLpiRepriceTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);
    private final LocalDate valueDate = LocalDate.of(2026, 8, 10);

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
    void repricesUnpaidLpiOnOrAfterTheValueDateFromReducedPrincipal() {
        final LoanCharge later = percentCharge(valueDate, "82.19", "0.08219");
        final Loan loan = loanWithPrincipal(later, "85000.00");

        org.assertj.core.api.Assertions.assertThat(PartialBackdatedLpiReprice.repriceUnpaidChargesOnOrAfter(loan, valueDate)).isTrue();
        verify(later).update(eq(new BigDecimal("0.08219")), eq(valueDate),
                org.mockito.ArgumentMatchers.argThat(base -> base != null && base.compareTo(new BigDecimal("85000.00")) == 0), isNull(),
                eq(BigDecimal.ZERO));
    }

    @Test
    void repricesPercentOfPrincipalAndInterestFromBothBalances() {
        final LoanCharge later = percentCharge(valueDate, "82.19", "0.08219");
        when(later.getChargeCalculation()).thenReturn(ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST);
        final Loan loan = loanWithPrincipal(later, "85000.00");
        when(installmentOf(later).getInterestOutstanding(currency)).thenReturn(Money.of(currency, new BigDecimal("3000.00")));

        org.assertj.core.api.Assertions.assertThat(PartialBackdatedLpiReprice.repriceUnpaidChargesOnOrAfter(loan, valueDate)).isTrue();
        verify(later).update(eq(new BigDecimal("0.08219")), eq(valueDate),
                org.mockito.ArgumentMatchers.argThat(base -> base != null && base.compareTo(new BigDecimal("88000.00")) == 0), isNull(),
                eq(BigDecimal.ZERO));
    }

    @Test
    void usesSummaryPrincipalWhenTheChargeHasNoInstallmentLink() {
        final LoanCharge later = percentCharge(valueDate, "82.19", "0.08219");
        when(later.getOverdueInstallmentCharge()).thenReturn(null);
        final Loan loan = mock(Loan.class);
        final LoanSummary summary = mock(LoanSummary.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(later));
        when(loan.getSummary()).thenReturn(summary);
        when(summary.getTotalPrincipalOutstanding()).thenReturn(new BigDecimal("85000.00"));

        org.assertj.core.api.Assertions.assertThat(PartialBackdatedLpiReprice.repriceUnpaidChargesOnOrAfter(loan, valueDate)).isTrue();
        verify(later).update(eq(new BigDecimal("0.08219")), eq(valueDate),
                org.mockito.ArgumentMatchers.argThat(base -> base != null && base.compareTo(new BigDecimal("85000.00")) == 0), isNull(),
                eq(BigDecimal.ZERO));
    }

    @Test
    void skipsPaidWaivedFlatAndAlreadyCorrectCharges() {
        final LoanCharge paid = percentCharge(valueDate, "82.19", "0.08219");
        when(paid.getAmountPaid(currency)).thenReturn(Money.of(currency, new BigDecimal("1.00")));
        final LoanCharge waived = percentCharge(valueDate, "82.19", "0.08219");
        when(waived.isWaived()).thenReturn(true);
        final LoanCharge flat = percentCharge(valueDate, "82.19", "0.08219");
        when(flat.getChargeCalculation()).thenReturn(ChargeCalculationType.FLAT);
        final BigDecimal unchanged = LoanCharge.percentageOf(new BigDecimal("85000.00"), new BigDecimal("0.08219"));
        final LoanCharge already = percentCharge(valueDate, "82.19", "0.08219");
        final Money exactAmount = mock(Money.class);
        when(exactAmount.getAmount()).thenReturn(unchanged);
        when(already.getAmount(currency)).thenReturn(exactAmount);

        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(paid, waived, flat, already));
        for (final LoanCharge charge : Set.of(paid, waived, flat, already)) {
            attachPrincipal(charge, "85000.00");
        }

        org.assertj.core.api.Assertions.assertThat(PartialBackdatedLpiReprice.repriceUnpaidChargesOnOrAfter(loan, valueDate)).isFalse();
        verify(paid, never()).update(any(), any(), any(), any(), any());
        verify(waived, never()).update(any(), any(), any(), any(), any());
        verify(flat, never()).update(any(), any(), any(), any(), any());
        verify(already, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void skipsAZeroPrincipalBaseAndMissingInputs() {
        final LoanCharge later = percentCharge(valueDate, "82.19", "0.08219");
        final Loan loan = loanWithPrincipal(later, "0.00");

        org.assertj.core.api.Assertions.assertThat(PartialBackdatedLpiReprice.repriceUnpaidChargesOnOrAfter(loan, valueDate)).isFalse();
        org.assertj.core.api.Assertions.assertThat(PartialBackdatedLpiReprice.repriceUnpaidChargesOnOrAfter(null, valueDate)).isFalse();
        org.assertj.core.api.Assertions.assertThat(PartialBackdatedLpiReprice.repriceUnpaidChargesOnOrAfter(loan, null)).isFalse();
        verify(later, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void lms132_repricesEveryUnpaidDayOnOrAfter22AugFromTheRemainingPrincipal() {
        final LocalDate valueDate = LocalDate.of(2026, 8, 22);
        final LoanCharge dueDate = percentCharge(valueDate, "448.79", "0.08219");
        final LoanCharge nextDay = percentCharge(LocalDate.of(2026, 8, 23), "448.79", "0.08219");
        final LoanCharge lastDay = percentCharge(LocalDate.of(2026, 9, 2), "448.79", "0.08219");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(dueDate, nextDay, lastDay));
        for (final LoanCharge charge : Set.of(dueDate, nextDay, lastDay)) {
            attachPrincipal(charge, "422651.00");
        }

        org.assertj.core.api.Assertions.assertThat(PartialBackdatedLpiReprice.repriceUnpaidChargesOnOrAfter(loan, valueDate)).isTrue();
        verify(dueDate).update(eq(new BigDecimal("0.08219")), eq(valueDate),
                org.mockito.ArgumentMatchers.argThat(base -> base != null && base.compareTo(new BigDecimal("422651.00")) == 0), isNull(),
                eq(BigDecimal.ZERO));
        verify(nextDay).update(eq(new BigDecimal("0.08219")), eq(LocalDate.of(2026, 8, 23)),
                org.mockito.ArgumentMatchers.argThat(base -> base != null && base.compareTo(new BigDecimal("422651.00")) == 0), isNull(),
                eq(BigDecimal.ZERO));
        verify(lastDay).update(eq(new BigDecimal("0.08219")), eq(LocalDate.of(2026, 9, 2)),
                org.mockito.ArgumentMatchers.argThat(base -> base != null && base.compareTo(new BigDecimal("422651.00")) == 0), isNull(),
                eq(BigDecimal.ZERO));
    }

    @Test
    void lms150ScenarioB_daysBeforeTheSecondValueDateKeepTheirAccruedRate() {
        final LocalDate secondValueDate = LocalDate.of(2026, 8, 5);
        final LoanCharge before = percentCharge(LocalDate.of(2026, 8, 4), "82.19", "0.08219");
        final LoanCharge onDate = percentCharge(secondValueDate, "82.19", "0.08219");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(before, onDate));
        attachPrincipal(before, "85000.00");
        attachPrincipal(onDate, "85000.00");

        org.assertj.core.api.Assertions.assertThat(PartialBackdatedLpiReprice.repriceUnpaidChargesOnOrAfter(loan, secondValueDate))
                .isTrue();
        verify(before, never()).update(any(), any(), any(), any(), any());
        verify(onDate).update(eq(new BigDecimal("0.08219")), eq(secondValueDate),
                org.mockito.ArgumentMatchers.argThat(base -> base != null && base.compareTo(new BigDecimal("85000.00")) == 0), isNull(),
                eq(BigDecimal.ZERO));
    }

    @Test
    void leavesLpiDatedBeforeTheValueDateAtTheRateItAccrued() {
        final LoanCharge earlier = percentCharge(LocalDate.of(2026, 8, 9), "82.19", "0.08219");
        final Loan loan = loanWithPrincipal(earlier, "85000.00");

        org.assertj.core.api.Assertions.assertThat(PartialBackdatedLpiReprice.repriceUnpaidChargesOnOrAfter(loan, valueDate)).isFalse();
        verify(earlier, never()).update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private Loan loanWithPrincipal(final LoanCharge charge, final String principal) {
        attachPrincipal(charge, principal);
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(charge));
        return loan;
    }

    private void attachPrincipal(final LoanCharge charge, final String principal) {
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getPrincipalOutstanding(currency)).thenReturn(Money.of(currency, new BigDecimal(principal)));
        when(installment.getInterestOutstanding(currency)).thenReturn(Money.zero(currency));
        final LoanOverdueInstallmentCharge link = mock(LoanOverdueInstallmentCharge.class);
        when(link.getInstallment()).thenReturn(installment);
        when(charge.getOverdueInstallmentCharge()).thenReturn(link);
    }

    private LoanRepaymentScheduleInstallment installmentOf(final LoanCharge charge) {
        return charge.getOverdueInstallmentCharge().getInstallment();
    }

    private LoanCharge percentCharge(final LocalDate dueDate, final String amount, final String rate) {
        final LoanCharge charge = mock(LoanCharge.class);
        when(charge.isActive()).thenReturn(true);
        when(charge.isWaived()).thenReturn(false);
        when(charge.isOverdueInstallmentCharge()).thenReturn(true);
        when(charge.getChargeCalculation()).thenReturn(ChargeCalculationType.PERCENT_OF_AMOUNT);
        when(charge.getDueLocalDate()).thenReturn(dueDate);
        when(charge.getAmountPaid(currency)).thenReturn(Money.zero(currency));
        when(charge.amountOrPercentage()).thenReturn(new BigDecimal(rate));
        when(charge.getAmount(currency)).thenReturn(Money.of(currency, new BigDecimal(amount)));
        return charge;
    }
}
