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
import java.util.concurrent.atomic.AtomicReference;
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
    void foldsLpiOnTheRowAfterASingleEmiOntoThatEmi() {
        final LoanRepaymentScheduleInstallment emi = contractual(LocalDate.of(2026, 3, 2), "145.83");
        final LoanRepaymentScheduleInstallment spill = spill(LocalDate.of(2026, 9, 30), "291.66");
        org.mockito.Mockito.doReturn(true).when(spill).isRecalculatedInterestComponent();
        final LoanCharge unpaid = overdue("437.49");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(unpaid));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(emi, spill));

        assertThat(InstallmentPenaltySyncUtils.syncOutstandingOverduePenaltyOntoSchedule(loan)).isTrue();
        org.mockito.Mockito.verify(emi).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("291.66")) == 0), any(),
                any());
        org.mockito.Mockito.verify(spill).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("-291.66")) == 0), any(),
                any());
    }

    @Test
    void leavesSpillLpiInPlaceWhenAnEarlierEmiIsStillUnpaid() {
        final LoanRepaymentScheduleInstallment first = contractual(LocalDate.of(2026, 3, 2), "145.83");
        org.mockito.Mockito.doReturn(false).when(first).isObligationsMet();
        final LoanRepaymentScheduleInstallment second = contractual(LocalDate.of(2026, 4, 2), "0");
        final LoanRepaymentScheduleInstallment spill = spill(LocalDate.of(2026, 9, 30), "291.66");
        final LoanCharge unpaid = overdue("437.49");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(unpaid));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(first, second, spill));

        assertThat(InstallmentPenaltySyncUtils.syncOutstandingOverduePenaltyOntoSchedule(loan)).isFalse();
        org.mockito.Mockito.verify(spill, org.mockito.Mockito.never()).addToChargePortion(any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void movesLpiDatedBeforeTheValueDateFromTheNextEmiOntoTheOverdueEmi() {
        final LoanRepaymentScheduleInstallment first = contractual(LocalDate.of(2026, 8, 31), "5.75");
        org.mockito.Mockito.doReturn(false).when(first).isObligationsMet();
        org.mockito.Mockito.doReturn(1).when(first).getInstallmentNumber();
        org.mockito.Mockito.doReturn(LocalDate.of(2026, 7, 30)).when(first).getFromDate();
        final LoanRepaymentScheduleInstallment second = contractual(LocalDate.of(2026, 9, 30), "178.02");
        org.mockito.Mockito.doReturn(false).when(second).isObligationsMet();
        org.mockito.Mockito.doReturn(2).when(second).getInstallmentNumber();
        org.mockito.Mockito.doReturn(LocalDate.of(2026, 8, 31)).when(second).getFromDate();

        final LoanCharge aug31 = overdueOn(LocalDate.of(2026, 8, 31), "5.75");
        final LoanCharge sep1 = overdueOn(LocalDate.of(2026, 9, 1), "5.75");
        final LoanCharge sep2 = overdueOn(LocalDate.of(2026, 9, 2), "5.75");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(aug31, sep1, sep2));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(first, second));

        assertThat(InstallmentPenaltySyncUtils.foldPreValueDatePenaltyOntoDueEmi(loan, LocalDate.of(2026, 9, 2))).isTrue();
        org.mockito.Mockito.verify(first, org.mockito.Mockito.times(1)).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("5.75")) == 0), any(), any());
        org.mockito.Mockito.verify(second, org.mockito.Mockito.times(1)).addToChargePortion(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(m -> m != null && m.getAmount().compareTo(new BigDecimal("-5.75")) == 0), any(),
                any());
    }

    @Test
    void lms150_negativePenaltyBalanceDoesNotDropTheLastPreDateDays() {
        // 000014570: EMI 1 holds only the due-date day but a prior partial already paid two later days against it,
        // so its penalty outstanding is -30.76. The next partial must still collect every unpaid day before 23 Sep.
        final LoanRepaymentScheduleInstallment first = contractual(LocalDate.of(2026, 9, 14), "0");
        final AtomicReference<BigDecimal> firstOutstanding = new AtomicReference<>(new BigDecimal("-30.76"));
        trackPenalty(first, firstOutstanding);
        org.mockito.Mockito.doReturn(1).when(first).getInstallmentNumber();
        org.mockito.Mockito.doReturn(LocalDate.of(2026, 8, 12)).when(first).getFromDate();

        final LoanRepaymentScheduleInstallment second = contractual(LocalDate.of(2026, 10, 12), "0");
        final AtomicReference<BigDecimal> secondOutstanding = new AtomicReference<>(new BigDecimal("123.04"));
        trackPenalty(second, secondOutstanding);
        org.mockito.Mockito.doReturn(2).when(second).getInstallmentNumber();
        org.mockito.Mockito.doReturn(LocalDate.of(2026, 9, 14)).when(second).getFromDate();

        final LoanCharge sep17 = overdueOn(LocalDate.of(2026, 9, 17), "15.38");
        final LoanCharge sep18 = overdueOn(LocalDate.of(2026, 9, 18), "15.38");
        final LoanCharge sep19 = overdueOn(LocalDate.of(2026, 9, 19), "15.38");
        final LoanCharge sep20 = overdueOn(LocalDate.of(2026, 9, 20), "15.38");
        final LoanCharge sep21 = overdueOn(LocalDate.of(2026, 9, 21), "15.38");
        final LoanCharge sep22 = overdueOn(LocalDate.of(2026, 9, 22), "15.38");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(sep17, sep18, sep19, sep20, sep21, sep22));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(first, second));

        assertThat(InstallmentPenaltySyncUtils.foldPreValueDatePenaltyOntoDueEmi(loan, LocalDate.of(2026, 9, 23))).isTrue();
        assertThat(firstOutstanding.get()).isEqualByComparingTo("92.28");
        assertThat(secondOutstanding.get()).isEqualByComparingTo("0.00");
    }

    @Test
    void severalOverdueInstallmentsKeepEachInstallmentsOwnLpiOnThatInstallment() {
        // Three EMIs are already due. Only the due-date day sits on its EMI; later days of that EMI are stored on the
        // next overdue EMI. A partial must still take installment 1's full LPI before installment 1's interest.
        final LoanRepaymentScheduleInstallment first = dueEmi(1, LocalDate.of(2026, 7, 14), LocalDate.of(2026, 8, 14), "12.82");
        final LoanRepaymentScheduleInstallment second = dueEmi(2, LocalDate.of(2026, 8, 14), LocalDate.of(2026, 9, 14), "38.46");
        final LoanRepaymentScheduleInstallment third = dueEmi(3, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 14), "38.46");

        final LoanCharge aug14 = linkedOverdue(first, LocalDate.of(2026, 8, 14), "12.82");
        final LoanCharge aug15 = linkedOverdue(first, LocalDate.of(2026, 8, 15), "12.82");
        final LoanCharge sep10 = linkedOverdue(first, LocalDate.of(2026, 9, 10), "12.82");
        final LoanCharge sep14 = linkedOverdue(second, LocalDate.of(2026, 9, 14), "12.82");
        final LoanCharge sep15 = linkedOverdue(second, LocalDate.of(2026, 9, 15), "12.82");
        final LoanCharge oct1 = linkedOverdue(second, LocalDate.of(2026, 10, 1), "12.82");
        final LoanCharge oct14 = linkedOverdue(third, LocalDate.of(2026, 10, 14), "12.82");

        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getLoanCharges()).thenReturn(Set.of(aug14, aug15, sep10, sep14, sep15, oct1, oct14));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(first, second, third));

        assertThat(InstallmentPenaltySyncUtils.foldPreValueDatePenaltyOntoDueEmi(loan, LocalDate.of(2026, 10, 15))).isTrue();
        assertThat(penaltyOutstanding(first)).isEqualByComparingTo("38.46");
        assertThat(penaltyOutstanding(second)).isEqualByComparingTo("38.46");
        assertThat(penaltyOutstanding(third)).isEqualByComparingTo("12.82");
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

    @Test
    void secondPartialDoesNotPostNegativeLpiAfterTheDueDateDayIsAllTheScheduleRowKeeps() {
        // Loan 000014627: EMI 1's 59 LPI days were paid (756.38) but the schedule row keeps only 20 Aug (12.82).
        // The other 743.56 sits on later rows. The next repayment must not post that gap as negative LPI.
        final LoanRepaymentScheduleInstallment emi1 = scheduleRow(1, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 20), "12.82");
        final LoanRepaymentScheduleInstallment emi2 = scheduleRow(2, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 21), "423.40");
        final LoanRepaymentScheduleInstallment emi3 = scheduleRow(3, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 20), "675.48");
        ReflectionTestUtils.setField(emi1, "penaltyChargesPaid", new BigDecimal("756.38"));
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(emi1, emi2, emi3));

        assertThat(InstallmentPenaltySyncUtils.coverPenaltyPaidAboveCharged(loan)).isTrue();
        assertThat(emi1.getPenaltyChargesOutstanding(currency).getAmount()).isEqualByComparingTo("0.00");
        assertThat(emi1.getPenaltyChargesCharged(currency).getAmount()).isEqualByComparingTo("756.38");
        assertThat(emi2.getPenaltyChargesOutstanding(currency).getAmount()).isEqualByComparingTo("355.32");
        assertThat(emi3.getPenaltyChargesOutstanding(currency).getAmount()).isEqualByComparingTo("0.00");

        final Money penaltyPortion = emi1.payPenaltyChargesComponent(LocalDate.of(2026, 10, 18),
                Money.of(currency, new BigDecimal("8000.00")));
        assertThat(penaltyPortion.getAmount()).isEqualByComparingTo("0.00");
        assertThat(emi1.getPenaltyChargesPaid(currency).getAmount()).isEqualByComparingTo("756.38");
    }

    private LoanRepaymentScheduleInstallment scheduleRow(final int number, final LocalDate fromDate, final LocalDate dueDate,
            final String penaltyCharged) {
        return new LoanRepaymentScheduleInstallment(null, number, fromDate, dueDate, new BigDecimal("1000.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(penaltyCharged), false, null, BigDecimal.ZERO);
    }

    private LoanRepaymentScheduleInstallment dueEmi(final int number, final LocalDate fromDate, final LocalDate dueDate,
            final String penaltyOutstanding) {
        final LoanRepaymentScheduleInstallment installment = contractual(dueDate, penaltyOutstanding);
        org.mockito.Mockito.doReturn(false).when(installment).isObligationsMet();
        org.mockito.Mockito.doReturn(number).when(installment).getInstallmentNumber();
        org.mockito.Mockito.doReturn(fromDate).when(installment).getFromDate();
        trackPenalty(installment, new AtomicReference<>(new BigDecimal(penaltyOutstanding)));
        return installment;
    }

    private LoanCharge linkedOverdue(final LoanRepaymentScheduleInstallment owner, final LocalDate dueDate, final String outstanding) {
        final LoanCharge charge = overdueOn(dueDate, outstanding);
        final LoanOverdueInstallmentCharge link = mock(LoanOverdueInstallmentCharge.class);
        when(link.getInstallment()).thenReturn(owner);
        when(charge.getOverdueInstallmentCharge()).thenReturn(link);
        return charge;
    }

    private BigDecimal penaltyOutstanding(final LoanRepaymentScheduleInstallment installment) {
        return installment.getPenaltyChargesOutstanding(currency).getAmount();
    }

    private void trackPenalty(final LoanRepaymentScheduleInstallment installment, final AtomicReference<BigDecimal> outstanding) {
        org.mockito.Mockito.doAnswer(invocation -> Money.of(currency, outstanding.get())).when(installment)
                .getPenaltyChargesOutstanding(currency);
        org.mockito.Mockito.doAnswer(invocation -> {
            final Money delta = invocation.getArgument(6);
            outstanding.set(outstanding.get().add(delta.getAmount()));
            return null;
        }).when(installment).addToChargePortion(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private LoanRepaymentScheduleInstallment contractual(final LocalDate dueDate, final String penaltyOutstanding) {
        final LoanRepaymentScheduleInstallment i = installment(dueDate, penaltyOutstanding, "0");
        org.mockito.Mockito.doReturn(Money.of(currency, new BigDecimal("175000.00"))).when(i).getPrincipal(currency);
        org.mockito.Mockito.doReturn(Money.of(currency, new BigDecimal("5393.84"))).when(i).getInterestCharged(currency);
        org.mockito.Mockito.doReturn(false).when(i).isAdditional();
        org.mockito.Mockito.doReturn(true).when(i).isObligationsMet();
        return i;
    }

    private LoanRepaymentScheduleInstallment spill(final LocalDate dueDate, final String penaltyOutstanding) {
        final LoanRepaymentScheduleInstallment i = installment(dueDate, penaltyOutstanding, "0");
        org.mockito.Mockito.doReturn(Money.zero(currency)).when(i).getPrincipal(currency);
        org.mockito.Mockito.doReturn(Money.zero(currency)).when(i).getInterestCharged(currency);
        org.mockito.Mockito.doReturn(false).when(i).isAdditional();
        return i;
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
