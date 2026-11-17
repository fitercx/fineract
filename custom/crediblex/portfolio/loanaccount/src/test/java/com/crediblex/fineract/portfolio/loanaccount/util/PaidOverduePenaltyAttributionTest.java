package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaidOverduePenaltyAttributionTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);

    @BeforeEach
    void setUp() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 11, 10));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "default", "UTC", null));
        final ConfigurationDomainService cfg = mock(ConfigurationDomainService.class);
        when(cfg.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", cfg);
        moneyHelper.initialize();
    }

    @Test
    void loan5431_paidLpiOfEmi1ShownOnEmi2IsReturnedSoEmi1Completes() {
        // After an effective-date rebuild EMI 1 keeps only 7 Oct (22.38) but already paid 7-9 Oct and 12-13 Oct
        // (67.22): outstanding -44.84, never completed. 8-13 Oct sit on EMI 2 next to the 10-11 Oct waiver.
        final LoanRepaymentScheduleInstallment emi1 = row(1, "2026-08-31", "2026-10-07", "27235.19", "12933.29", "22.38");
        settle(emi1, "27235.19", "12933.29", "67.22", null);
        final LoanRepaymentScheduleInstallment emi2 = row(2, "2026-10-07", "2026-11-07", "30313.95", "9854.53", "114.52");
        settle(emi2, null, "6354.02", null, "44.76");
        final LoanRepaymentScheduleInstallment emi3 = row(3, "2026-11-07", "2026-12-07", "31381.85", "8786.63", "49.84");
        final List<LoanRepaymentScheduleInstallment> schedule = List.of(emi1, emi2, emi3);

        final List<LoanCharge> charges = new ArrayList<>();
        charges.add(lpi(emi1, "2026-10-07", "22.38", "22.38"));
        charges.add(lpi(emi1, "2026-10-08", "22.38", "22.38"));
        charges.add(lpi(emi1, "2026-10-09", "22.38", "22.38"));
        charges.add(lpi(emi1, "2026-10-10", "22.38", "0"));
        charges.add(lpi(emi1, "2026-10-11", "22.38", "0"));
        charges.add(lpi(emi1, "2026-10-12", "0.04", "0.04"));
        charges.add(lpi(emi1, "2026-10-13", "0.04", "0.04"));
        charges.add(lpi(emi2, "2026-11-07", "24.92", "0"));
        charges.add(lpi(emi2, "2026-11-08", "24.92", "0"));
        charges.add(lpi(emi2, "2026-11-09", "24.92", "0"));

        final Loan loan = loan(schedule, charges, List.of(repayment("2026-10-10", emi1), repayment("2026-11-07", emi1, emi2)));

        assertThat(PaidOverduePenaltyAttribution.apply(loan, byDateWindow(schedule))).isTrue();

        assertThat(charged(emi1)).isEqualByComparingTo("67.22");
        assertThat(penaltyOutstanding(emi1)).isEqualByComparingTo("0");
        assertThat(emi1.isObligationsMet()).isTrue();
        assertThat(emi1.getObligationsMetOnDate()).isEqualTo(LocalDate.of(2026, 11, 7));

        assertThat(charged(emi2)).isEqualByComparingTo("69.68");
        assertThat(emi2.getPenaltyChargesWaived(currency).getAmount()).isEqualByComparingTo("44.76");
        assertThat(penaltyOutstanding(emi2)).isEqualByComparingTo("24.92");
        assertThat(emi2.isObligationsMet()).isFalse();

        assertThat(charged(emi3)).isEqualByComparingTo("49.84");
        assertThat(charged(emi1).add(charged(emi2)).add(charged(emi3))).isEqualByComparingTo("186.74");
    }

    @Test
    void secondRunIsANoOp() {
        final LoanRepaymentScheduleInstallment emi1 = row(1, "2026-08-31", "2026-10-07", "100.00", "10.00", "22.38");
        settle(emi1, "100.00", "10.00", "44.76", null);
        final LoanRepaymentScheduleInstallment emi2 = row(2, "2026-10-07", "2026-11-07", "100.00", "10.00", "22.38");
        final List<LoanRepaymentScheduleInstallment> schedule = List.of(emi1, emi2);
        final Loan loan = loan(schedule, List.of(lpi(emi1, "2026-10-07", "22.38", "22.38"), lpi(emi1, "2026-10-08", "22.38", "22.38")),
                List.of());

        assertThat(PaidOverduePenaltyAttribution.apply(loan, byDateWindow(schedule))).isTrue();
        assertThat(PaidOverduePenaltyAttribution.apply(loan, byDateWindow(schedule))).isFalse();
        assertThat(charged(emi1)).isEqualByComparingTo("44.76");
        assertThat(charged(emi2)).isEqualByComparingTo("0");
    }

    @Test
    void leavesScheduleUntouchedWhenNoInstallmentHasPaidAboveCharged() {
        final LoanRepaymentScheduleInstallment emi1 = row(1, "2026-08-31", "2026-10-07", "100.00", "10.00", "22.38");
        settle(emi1, null, null, "22.38", null);
        final LoanRepaymentScheduleInstallment emi2 = row(2, "2026-10-07", "2026-11-07", "100.00", "10.00", "22.38");
        final List<LoanRepaymentScheduleInstallment> schedule = List.of(emi1, emi2);
        final Loan loan = loan(schedule, List.of(lpi(emi1, "2026-10-07", "22.38", "22.38"), lpi(emi1, "2026-10-08", "22.38", "0")),
                List.of());

        assertThat(PaidOverduePenaltyAttribution.apply(loan, byDateWindow(schedule))).isFalse();
        assertThat(charged(emi1)).isEqualByComparingTo("22.38");
        assertThat(charged(emi2)).isEqualByComparingTo("22.38");
    }

    @Test
    void movesOnlyThePaidPartOfAPartiallyPaidCharge() {
        final LoanRepaymentScheduleInstallment emi1 = row(1, "2026-08-31", "2026-10-07", "100.00", "10.00", "10.00");
        settle(emi1, null, null, "14.00", null);
        final LoanRepaymentScheduleInstallment emi2 = row(2, "2026-10-07", "2026-11-07", "100.00", "10.00", "10.00");
        final List<LoanRepaymentScheduleInstallment> schedule = List.of(emi1, emi2);
        final Loan loan = loan(schedule, List.of(lpi(emi1, "2026-10-07", "10.00", "10.00"), lpi(emi1, "2026-10-08", "10.00", "4.00")),
                List.of());

        assertThat(PaidOverduePenaltyAttribution.apply(loan, byDateWindow(schedule))).isTrue();
        assertThat(charged(emi1)).isEqualByComparingTo("14.00");
        assertThat(penaltyOutstanding(emi1)).isEqualByComparingTo("0");
        assertThat(charged(emi2)).isEqualByComparingTo("6.00");
        assertThat(penaltyOutstanding(emi2)).isEqualByComparingTo("6.00");
    }

    @Test
    void neverTakesMoreThanTheLaterRowsOutstandingSoNoNewDeficitAppears() {
        final LoanRepaymentScheduleInstallment emi1 = row(1, "2026-08-31", "2026-10-07", "100.00", "10.00", "10.00");
        settle(emi1, null, null, "30.00", null);
        final LoanRepaymentScheduleInstallment emi2 = row(2, "2026-10-07", "2026-11-07", "100.00", "10.00", "20.00");
        settle(emi2, null, null, "15.00", null);
        final List<LoanRepaymentScheduleInstallment> schedule = List.of(emi1, emi2);
        final Loan loan = loan(schedule, List.of(lpi(emi1, "2026-10-07", "10.00", "10.00"), lpi(emi1, "2026-10-08", "20.00", "20.00")),
                List.of());

        assertThat(PaidOverduePenaltyAttribution.apply(loan, byDateWindow(schedule))).isTrue();
        assertThat(penaltyOutstanding(emi2)).isEqualByComparingTo("0");
        assertThat(penaltyOutstanding(emi1)).isEqualByComparingTo("-15.00");
        assertThat(charged(emi1).add(charged(emi2))).isEqualByComparingTo("30.00");
    }

    @Test
    void doesNotMoveLpiLinkedToAnotherInstallment() {
        final LoanRepaymentScheduleInstallment emi1 = row(1, "2026-08-31", "2026-10-07", "100.00", "10.00", "10.00");
        settle(emi1, null, null, "20.00", null);
        final LoanRepaymentScheduleInstallment emi2 = row(2, "2026-10-07", "2026-11-07", "100.00", "10.00", "10.00");
        final List<LoanRepaymentScheduleInstallment> schedule = List.of(emi1, emi2);
        final Loan loan = loan(schedule, List.of(lpi(emi1, "2026-10-07", "10.00", "10.00"), lpi(emi2, "2026-10-08", "10.00", "10.00")),
                List.of());

        assertThat(PaidOverduePenaltyAttribution.apply(loan, byDateWindow(schedule))).isFalse();
        assertThat(charged(emi1)).isEqualByComparingTo("10.00");
        assertThat(charged(emi2)).isEqualByComparingTo("10.00");
    }

    @Test
    void ignoresInactiveAndUnlinkedCharges() {
        final LoanRepaymentScheduleInstallment emi1 = row(1, "2026-08-31", "2026-10-07", "100.00", "10.00", "10.00");
        settle(emi1, null, null, "20.00", null);
        final LoanRepaymentScheduleInstallment emi2 = row(2, "2026-10-07", "2026-11-07", "100.00", "10.00", "20.00");
        final List<LoanRepaymentScheduleInstallment> schedule = List.of(emi1, emi2);
        final LoanCharge inactive = lpi(emi1, "2026-10-08", "10.00", "10.00");
        doReturn(false).when(inactive).isActive();
        final LoanCharge unlinked = lpi(emi1, "2026-10-09", "10.00", "10.00");
        doReturn(null).when(unlinked).getOverdueInstallmentCharge();
        final Loan loan = loan(schedule, List.of(lpi(emi1, "2026-10-07", "10.00", "10.00"), inactive, unlinked), List.of());

        assertThat(PaidOverduePenaltyAttribution.apply(loan, byDateWindow(schedule))).isFalse();
        assertThat(charged(emi1)).isEqualByComparingTo("10.00");
    }

    @Test
    void shiftsAreEmptyForMissingInputs() {
        assertThat(PaidOverduePenaltyAttribution.shifts(null, Map.of(), charge -> 1, currency)).isEmpty();
        assertThat(PaidOverduePenaltyAttribution.shifts(List.of(), null, charge -> 1, currency)).isEmpty();
        assertThat(PaidOverduePenaltyAttribution.apply(null, charge -> 1)).isFalse();
    }

    private Function<LoanCharge, Integer> byDateWindow(final List<LoanRepaymentScheduleInstallment> schedule) {
        return charge -> OverdueChargeScheduleAllocationUtils.resolveInstallmentNumber(charge.getDueLocalDate(), schedule);
    }

    private LoanRepaymentScheduleInstallment row(final int number, final String from, final String due, final String principal,
            final String interest, final String penalty) {
        return new LoanRepaymentScheduleInstallment(null, number, LocalDate.parse(from), LocalDate.parse(due), new BigDecimal(principal),
                new BigDecimal(interest), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(penalty), false, null, BigDecimal.ZERO);
    }

    private void settle(final LoanRepaymentScheduleInstallment installment, final String principalPaid, final String interestPaid,
            final String penaltyPaid, final String penaltyWaived) {
        ReflectionTestUtils.setField(installment, "principalCompleted", amount(principalPaid));
        ReflectionTestUtils.setField(installment, "interestPaid", amount(interestPaid));
        ReflectionTestUtils.setField(installment, "penaltyChargesPaid", amount(penaltyPaid));
        ReflectionTestUtils.setField(installment, "penaltyChargesWaived", amount(penaltyWaived));
    }

    private BigDecimal amount(final String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private LoanCharge lpi(final LoanRepaymentScheduleInstallment owner, final String dueDate, final String amount, final String paid) {
        final LoanCharge charge = mock(LoanCharge.class);
        final LoanOverdueInstallmentCharge link = mock(LoanOverdueInstallmentCharge.class);
        when(link.getInstallment()).thenReturn(owner);
        doReturn(link).when(charge).getOverdueInstallmentCharge();
        doReturn(true).when(charge).isActive();
        doReturn(true).when(charge).isOverdueInstallmentCharge();
        doReturn(true).when(charge).isPenaltyCharge();
        doReturn(LocalDate.parse(dueDate)).when(charge).getDueLocalDate();
        doReturn(Money.of(currency, new BigDecimal(amount))).when(charge).getAmount(currency);
        doReturn(Money.of(currency, new BigDecimal(paid))).when(charge).getAmountPaid(currency);
        return charge;
    }

    private LoanTransaction repayment(final String date, final LoanRepaymentScheduleInstallment... paid) {
        final LoanTransaction transaction = mock(LoanTransaction.class);
        when(transaction.isReversed()).thenReturn(false);
        when(transaction.getTransactionDate()).thenReturn(LocalDate.parse(date));
        final Set<LoanTransactionToRepaymentScheduleMapping> mappings = new HashSet<>();
        for (final LoanRepaymentScheduleInstallment installment : paid) {
            final LoanTransactionToRepaymentScheduleMapping mapping = mock(LoanTransactionToRepaymentScheduleMapping.class);
            when(mapping.getLoanRepaymentScheduleInstallment()).thenReturn(installment);
            mappings.add(mapping);
        }
        when(transaction.getLoanTransactionToRepaymentScheduleMappings()).thenReturn(mappings);
        return transaction;
    }

    private Loan loan(final List<LoanRepaymentScheduleInstallment> schedule, final List<LoanCharge> charges,
            final List<LoanTransaction> transactions) {
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(schedule);
        when(loan.getLoanCharges()).thenReturn(Set.copyOf(charges));
        when(loan.getLoanTransactions()).thenReturn(transactions);
        return loan;
    }

    private BigDecimal charged(final LoanRepaymentScheduleInstallment installment) {
        return installment.getPenaltyChargesCharged(currency).getAmount();
    }

    private BigDecimal penaltyOutstanding(final LoanRepaymentScheduleInstallment installment) {
        return installment.getPenaltyChargesOutstanding(currency).getAmount();
    }
}
