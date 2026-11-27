package com.crediblex.fineract.portfolio.loanaccount.service;

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
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Daily LPI job path: {@code hasRepaymentScheduleChargeMismatch} then
 * {@code recalculateInstallmentChargesFromActiveLoanCharges}. Loan 000005431: EMI 1 paid 7-9 and 12-13 Oct LPI, 10-11
 * Oct waived, the schedule shows 8 Oct onwards on EMI 2.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CredXLoanChargePaidLpiScheduleTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);

    @InjectMocks
    private CredXLoanChargeWritePlatformServiceImpl service;

    private LoanRepaymentScheduleInstallment emi1;
    private LoanRepaymentScheduleInstallment emi2;
    private LoanRepaymentScheduleInstallment emi3;

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

        emi1 = row(1, "2026-08-31", "2026-10-07", "27235.19", "12933.29");
        settle(emi1, "27235.19", "12933.29", "67.22");
        emi2 = row(2, "2026-10-07", "2026-11-07", "30313.95", "9854.53");
        settle(emi2, null, "6354.02", null);
        emi3 = row(3, "2026-11-07", "2026-12-07", "31381.85", "8786.63");
    }

    @Test
    void lpiJobRebuildKeepsPaidLpiOnEmi1SoItCompletes() {
        final Loan loan = loan5431();

        ReflectionTestUtils.invokeMethod(service, "recalculateInstallmentChargesFromActiveLoanCharges", loan);

        assertThat(charged(emi1)).isEqualByComparingTo("67.22");
        assertThat(penaltyOutstanding(emi1)).isEqualByComparingTo("0");
        assertThat(emi1.isObligationsMet()).isTrue();
        assertThat(emi1.getObligationsMetOnDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(charged(emi2)).isEqualByComparingTo("69.68");
        assertThat(emi2.getPenaltyChargesWaived(currency).getAmount()).isEqualByComparingTo("44.76");
        assertThat(penaltyOutstanding(emi2)).isEqualByComparingTo("24.92");
        assertThat(charged(emi3)).isEqualByComparingTo("49.84");
        assertThat(penaltyOutstanding(emi3)).isEqualByComparingTo("49.84");
    }

    @Test
    void mismatchCheckAcceptsTheRebuiltScheduleSoTheNextLpiRunLeavesItAlone() {
        final Loan loan = loan5431();
        ReflectionTestUtils.invokeMethod(service, "recalculateInstallmentChargesFromActiveLoanCharges", loan);

        final Boolean mismatch = ReflectionTestUtils.invokeMethod(service, "hasRepaymentScheduleChargeMismatch", loan);

        assertThat(mismatch).isFalse();
    }

    @Test
    void mismatchCheckFlagsAnEmiWhosePaidLpiWasPushedOntoTheNextRow() {
        // Current production state: EMI 1 charged 22.38 / paid 67.22. The next LPI run must repair it.
        ReflectionTestUtils.setField(emi1, "penaltyCharges", new BigDecimal("22.38"));
        ReflectionTestUtils.setField(emi2, "penaltyCharges", new BigDecimal("114.52"));
        ReflectionTestUtils.setField(emi2, "penaltyChargesWaived", new BigDecimal("44.76"));
        ReflectionTestUtils.setField(emi3, "penaltyCharges", new BigDecimal("49.84"));

        final Boolean mismatch = ReflectionTestUtils.invokeMethod(service, "hasRepaymentScheduleChargeMismatch", loan5431());

        assertThat(mismatch).isTrue();
    }

    @Test
    void mismatchCheckIsUnchangedForAnEffectiveDateScheduleWithNoPaidAboveCharged() {
        // EMI 1 paid only its due-date day; the later days are unpaid and stay on EMI 2 as before.
        settle(emi1, "27235.19", "12933.29", "22.38");
        ReflectionTestUtils.setField(emi1, "penaltyCharges", new BigDecimal("22.38"));
        ReflectionTestUtils.setField(emi2, "penaltyCharges", new BigDecimal("44.76"));
        final List<LoanCharge> charges = List.of(lpi(emi1, "2026-10-07", "22.38", "22.38", "0", 1),
                lpi(emi1, "2026-10-08", "22.38", "0", "0", 2), lpi(emi1, "2026-10-09", "22.38", "0", "0", 3));
        final Loan loan = loan(charges, List.of());

        final Boolean mismatch = ReflectionTestUtils.invokeMethod(service, "hasRepaymentScheduleChargeMismatch", loan);
        ReflectionTestUtils.invokeMethod(service, "recalculateInstallmentChargesFromActiveLoanCharges", loan);

        assertThat(mismatch).isFalse();
        assertThat(charged(emi1)).isEqualByComparingTo("22.38");
        assertThat(charged(emi2)).isEqualByComparingTo("44.76");
    }

    private Loan loan5431() {
        final List<LoanCharge> charges = new ArrayList<>();
        charges.add(lpi(emi1, "2026-10-07", "22.38", "22.38", "0", 1));
        charges.add(lpi(emi1, "2026-10-08", "22.38", "22.38", "0", 2));
        charges.add(lpi(emi1, "2026-10-09", "22.38", "22.38", "0", 3));
        charges.add(lpi(emi1, "2026-10-10", "22.38", "0", "22.38", 4));
        charges.add(lpi(emi1, "2026-10-11", "22.38", "0", "22.38", 5));
        charges.add(lpi(emi1, "2026-10-12", "0.04", "0.04", "0", 6));
        charges.add(lpi(emi1, "2026-10-13", "0.04", "0.04", "0", 7));
        charges.add(lpi(emi2, "2026-11-07", "24.92", "0", "0", 1));
        charges.add(lpi(emi2, "2026-11-08", "24.92", "0", "0", 2));
        charges.add(lpi(emi2, "2026-11-09", "24.92", "0", "0", 3));
        return loan(charges, List.of(repayment("2026-10-10", emi1), repayment("2026-11-07", emi1, emi2)));
    }

    private Loan loan(final List<LoanCharge> charges, final List<LoanTransaction> transactions) {
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(emi1, emi2, emi3));
        when(loan.getLoanCharges()).thenReturn(Set.copyOf(charges));
        when(loan.getLoanTransactions()).thenReturn(transactions);
        return loan;
    }

    private LoanRepaymentScheduleInstallment row(final int number, final String from, final String due, final String principal,
            final String interest) {
        return new LoanRepaymentScheduleInstallment(null, number, LocalDate.parse(from), LocalDate.parse(due), new BigDecimal(principal),
                new BigDecimal(interest), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, null, BigDecimal.ZERO);
    }

    private void settle(final LoanRepaymentScheduleInstallment installment, final String principalPaid, final String interestPaid,
            final String penaltyPaid) {
        ReflectionTestUtils.setField(installment, "principalCompleted", principalPaid == null ? null : new BigDecimal(principalPaid));
        ReflectionTestUtils.setField(installment, "interestPaid", interestPaid == null ? null : new BigDecimal(interestPaid));
        ReflectionTestUtils.setField(installment, "penaltyChargesPaid", penaltyPaid == null ? null : new BigDecimal(penaltyPaid));
    }

    private LoanCharge lpi(final LoanRepaymentScheduleInstallment owner, final String dueDate, final String amount, final String paid,
            final String waived, final int frequency) {
        final LoanCharge charge = mock(LoanCharge.class);
        final LoanOverdueInstallmentCharge link = mock(LoanOverdueInstallmentCharge.class);
        when(link.getInstallment()).thenReturn(owner);
        when(link.getFrequencyNumber()).thenReturn(frequency);
        doReturn(link).when(charge).getOverdueInstallmentCharge();
        doReturn(true).when(charge).isActive();
        doReturn(true).when(charge).isOverdueInstallmentCharge();
        doReturn(true).when(charge).isPenaltyCharge();
        doReturn(false).when(charge).isInstalmentFee();
        doReturn(new HashSet<>()).when(charge).getLoanChargePaidBySet();
        doReturn(LocalDate.parse(dueDate)).when(charge).getDueLocalDate();
        doReturn(Money.of(currency, new BigDecimal(amount))).when(charge).getAmount(currency);
        doReturn(Money.of(currency, new BigDecimal(paid))).when(charge).getAmountPaid(currency);
        doReturn(Money.of(currency, new BigDecimal(waived))).when(charge).getAmountWaived(currency);
        doReturn(Money.zero(currency)).when(charge).getAmountWrittenOff(currency);
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

    private BigDecimal charged(final LoanRepaymentScheduleInstallment installment) {
        return installment.getPenaltyChargesCharged(currency).getAmount();
    }

    private BigDecimal penaltyOutstanding(final LoanRepaymentScheduleInstallment installment) {
        return installment.getPenaltyChargesOutstanding(currency).getAmount();
    }
}
