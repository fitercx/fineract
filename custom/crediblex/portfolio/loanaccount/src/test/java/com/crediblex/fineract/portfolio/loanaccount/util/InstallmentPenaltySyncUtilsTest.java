package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InstallmentPenaltySyncUtilsTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);

    @BeforeEach
    void setUp() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 8, 14));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "default", "UTC", null));
        final ConfigurationDomainService cfg = mock(ConfigurationDomainService.class);
        when(cfg.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", cfg);
        moneyHelper.initialize();
    }

    @Test
    void addsUnmappedOverdueLpiOntoOverdueEmiNotDummyGraceRow() {
        final LoanRepaymentScheduleInstallment overdueEmi = installment(LocalDate.of(2026, 8, 10), "0", "0");
        final LoanRepaymentScheduleInstallment dummy = installment(LocalDate.of(2026, 8, 14), "1.00", "1.00");
        org.mockito.Mockito.doReturn(true).when(dummy).isAdditional();
        org.mockito.Mockito.doReturn(true).when(dummy).isRecalculatedInterestComponent();

        final LoanCharge unpaid = overdue("6.56");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(unpaid));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(overdueEmi, dummy));

        assertThat(InstallmentPenaltySyncUtils.syncOutstandingOverduePenaltyOntoSchedule(loan)).isTrue();
        org.mockito.Mockito.verify(overdueEmi).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("6.56")) == 0), any(), any());
        org.mockito.Mockito.verify(dummy, org.mockito.Mockito.never()).addToChargePortion(any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void noOpWhenScheduleAlreadyMatchesChargeOutstanding() {
        final LoanRepaymentScheduleInstallment installment = installment(LocalDate.of(2026, 7, 23), "2021.33", "0");
        final LoanCharge unpaid = overdue("2021.33");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(unpaid));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(installment));

        assertThat(InstallmentPenaltySyncUtils.syncOutstandingOverduePenaltyOntoSchedule(loan)).isFalse();
        org.mockito.Mockito.verify(installment, org.mockito.Mockito.never()).addToChargePortion(any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void partialBackdateKeepsOnlyLpiStrictlyBeforeTheValueDateOnTheSchedule() {
        final LoanRepaymentScheduleInstallment overdueEmi = installment(LocalDate.of(2026, 7, 31), "164.38", "0");
        final LoanCharge beforeValueDate = overdueOn(LocalDate.of(2026, 8, 1), "82.19");
        final LoanCharge onValueDate = overdueOn(LocalDate.of(2026, 8, 10), "82.19");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(beforeValueDate, onValueDate));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(overdueEmi));

        assertThat(InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(loan, LocalDate.of(2026, 8, 10))).isTrue();
        org.mockito.Mockito.verify(overdueEmi).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("-82.19")) == 0), any(),
                any());
    }

    @Test
    void lms146_feroBackdatedPartialCollectsTheDueDateAndTheNextOverdueDay() {
        // EMI due 9 Sep holds only that day's 178.40. Payment on 11 Sep must also collect 10 Sep.
        final LoanRepaymentScheduleInstallment overdueEmi = installment(LocalDate.of(2026, 9, 9), "178.40", "0");
        final LoanCharge dueDate = overdueOn(LocalDate.of(2026, 9, 9), "178.40");
        final LoanCharge dayBeforePayment = overdueOn(LocalDate.of(2026, 9, 10), "178.40");
        final LoanCharge paymentDate = overdueOn(LocalDate.of(2026, 9, 11), "178.40");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(dueDate, dayBeforePayment, paymentDate));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(overdueEmi));

        assertThat(InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(loan, LocalDate.of(2026, 9, 11))).isTrue();
        org.mockito.Mockito.verify(overdueEmi).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("178.40")) == 0), any(),
                any());
    }

    @Test
    void lms150_loan18068CollectsTenDaysBefore10AugNotOnlyTheDueDate() {
        // Due 31 Jul is on the schedule (82.19). 1–9 Aug (739.71) must be added. 10 Aug stays off the payable schedule.
        final LoanRepaymentScheduleInstallment overdueEmi = installment(LocalDate.of(2026, 7, 31), "82.19", "0");
        final LoanCharge dueDate = overdueOn(LocalDate.of(2026, 7, 31), "82.19");
        final LoanCharge throughNinth = overdueOn(LocalDate.of(2026, 8, 1), "739.71");
        final LoanCharge paymentDate = overdueOn(LocalDate.of(2026, 8, 10), "82.19");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(dueDate, throughNinth, paymentDate));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(overdueEmi));

        assertThat(InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(loan, LocalDate.of(2026, 8, 10))).isTrue();
        org.mockito.Mockito.verify(overdueEmi).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("739.71")) == 0), any(),
                any());
    }

    @Test
    void partialBackdateAddsPreValueDateLpiMissingFromTheSchedule() {
        final LoanRepaymentScheduleInstallment overdueEmi = installment(LocalDate.of(2026, 7, 31), "82.19", "0");
        final LoanCharge firstDay = overdueOn(LocalDate.of(2026, 7, 31), "82.19");
        final LoanCharge laterDay = overdueOn(LocalDate.of(2026, 8, 9), "82.19");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(firstDay, laterDay));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(overdueEmi));

        assertThat(InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(loan, LocalDate.of(2026, 8, 10))).isTrue();
        org.mockito.Mockito.verify(overdueEmi).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("82.19")) == 0), any(),
                any());
    }

    @Test
    void alignIsANoOpWhenScheduleAlreadyMatchesPreDateLpi() {
        final LoanRepaymentScheduleInstallment overdueEmi = installment(LocalDate.of(2026, 7, 31), "82.19", "0");
        final Loan loan = loanWith(overdueEmi, overdueOn(LocalDate.of(2026, 8, 9), "82.19"));

        assertThat(InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(loan, LocalDate.of(2026, 8, 10))).isFalse();
        org.mockito.Mockito.verify(overdueEmi, org.mockito.Mockito.never()).addToChargePortion(any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void alignIgnoresWaivedLpiAndPullsItOffThePayableSchedule() {
        final LoanRepaymentScheduleInstallment overdueEmi = installment(LocalDate.of(2026, 7, 31), "82.19", "0");
        final LoanCharge waived = overdueOn(LocalDate.of(2026, 8, 1), "82.19");
        org.mockito.Mockito.doReturn(true).when(waived).isWaived();
        final Loan loan = loanWith(overdueEmi, waived);

        assertThat(InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(loan, LocalDate.of(2026, 8, 10))).isTrue();
        org.mockito.Mockito.verify(overdueEmi).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("-82.19")) == 0), any(),
                any());
    }

    @Test
    void alignTreatsAChargeWithNoDueDateAsPayable() {
        final LoanRepaymentScheduleInstallment overdueEmi = installment(LocalDate.of(2026, 7, 31), "0", "0");
        final LoanCharge undated = overdueOn(null, "10.00");
        final Loan loan = loanWith(overdueEmi, undated);

        assertThat(InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(loan, LocalDate.of(2026, 8, 10))).isTrue();
        org.mockito.Mockito.verify(overdueEmi).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("10.00")) == 0), any(),
                any());
    }

    @Test
    void alignReducesTheLargestPenaltyInstallmentFirstAndDoesNotExceedItsOutstanding() {
        final LoanRepaymentScheduleInstallment larger = installment(LocalDate.of(2026, 7, 31), "100.00", "0");
        final LoanRepaymentScheduleInstallment smaller = installment(LocalDate.of(2026, 8, 31), "50.00", "0");
        final LoanCharge payable = overdueOn(LocalDate.of(2026, 8, 1), "10.00");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(payable));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(smaller, larger));

        assertThat(InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(loan, LocalDate.of(2026, 8, 10))).isTrue();
        org.mockito.Mockito.verify(larger).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("-100.00")) == 0), any(),
                any());
        org.mockito.Mockito.verify(smaller).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("-40.00")) == 0), any(),
                any());
    }

    @Test
    void alignReturnsFalseForMissingLoanOrDate() {
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of());

        assertThat(InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(null, LocalDate.of(2026, 8, 10))).isFalse();
        assertThat(InstallmentPenaltySyncUtils.alignSchedulePenaltyToChargesBefore(loan, null)).isFalse();
    }

    @Test
    void absorbsUnpaidDummyPenaltyByPuttingTheGapOnTheOverdueEmi() {
        final LoanRepaymentScheduleInstallment overdueEmi = installment(LocalDate.of(2026, 8, 10), "0", "0");
        final LoanRepaymentScheduleInstallment dummy = installment(LocalDate.of(2026, 8, 14), "1.00", "0");
        org.mockito.Mockito.doReturn(true).when(dummy).isRecalculatedInterestComponent();
        final LoanCharge unpaid = overdue("6.56");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(unpaid));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(overdueEmi, dummy));

        assertThat(InstallmentPenaltySyncUtils.syncOutstandingOverduePenaltyOntoSchedule(loan)).isTrue();
        org.mockito.Mockito.verify(overdueEmi).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("5.56")) == 0), any(), any());
    }

    private LoanRepaymentScheduleInstallment installment(final LocalDate dueDate, final String charged, final String paid) {
        final LoanRepaymentScheduleInstallment i = mock(LoanRepaymentScheduleInstallment.class);
        final BigDecimal outstanding = new BigDecimal(charged).subtract(new BigDecimal(paid)).max(BigDecimal.ZERO);
        final Money outstandingMoney = Money.of(currency, outstanding);
        org.mockito.Mockito.doReturn(dueDate).when(i).getDueDate();
        org.mockito.Mockito.doReturn(false).when(i).isDownPayment();
        org.mockito.Mockito.doReturn(outstandingMoney).when(i).getPenaltyChargesOutstanding(currency);
        return i;
    }

    private Loan loanWith(final LoanRepaymentScheduleInstallment installment, final LoanCharge charge) {
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(charge));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(installment));
        return loan;
    }

    private LoanCharge overdue(final String outstanding) {
        return overdueOn(LocalDate.of(2026, 8, 13), outstanding);
    }

    private LoanCharge overdueOn(final LocalDate dueDate, final String outstanding) {
        final LoanCharge c = mock(LoanCharge.class);
        final Money outstandingMoney = Money.of(currency, new BigDecimal(outstanding));
        org.mockito.Mockito.doReturn(true).when(c).isActive();
        org.mockito.Mockito.doReturn(true).when(c).isOverdueInstallmentCharge();
        org.mockito.Mockito.doReturn(false).when(c).isWaived();
        org.mockito.Mockito.doReturn(dueDate).when(c).getDueLocalDate();
        org.mockito.Mockito.doReturn(outstandingMoney).when(c).getAmountOutstanding(currency);
        return c;
    }
}
