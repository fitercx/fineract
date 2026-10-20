package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PartialBackdatedClearedInstallmentsTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);
    private final LocalDate valueDate = LocalDate.of(2026, 10, 12);

    @BeforeEach
    void setUp() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 18));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "default", "UTC", null));
        final ConfigurationDomainService cfg = mock(ConfigurationDomainService.class);
        when(cfg.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", cfg);
        moneyHelper.initialize();
    }

    @Test
    void partialThatFinishesTheFirstInstallmentClearsOnlyThatInstallment() {
        final LoanRepaymentScheduleInstallment first = installment(1, "6842.34", "0.00");
        final LoanRepaymentScheduleInstallment second = installment(2, "16011.11", "2231.01");
        final Loan loan = loan(List.of(first, second), Set.of(charge(first, LocalDate.of(2026, 10, 11), "5.62"),
                charge(first, valueDate, "5.62"), charge(second, LocalDate.of(2026, 9, 21), "13.16")));

        assertThat(PartialBackdatedClearedInstallments.installmentNumbersClearedBy(loan, valueDate, new BigDecimal("7000.00")))
                .containsExactly(1);
    }

    @Test
    void partialThatDoesNotFinishAnInstallmentClearsNone() {
        final LoanRepaymentScheduleInstallment first = installment(1, "6842.34", "0.00");
        final Loan loan = loan(List.of(first), Set.of(charge(first, LocalDate.of(2026, 10, 11), "5.62")));

        assertThat(PartialBackdatedClearedInstallments.installmentNumbersClearedBy(loan, valueDate, new BigDecimal("1000.00"))).isEmpty();
    }

    @Test
    void remainderThatAlsoFinishesTheNextInstallmentClearsBoth() {
        final LoanRepaymentScheduleInstallment first = installment(1, "100.00", "0.00");
        final LoanRepaymentScheduleInstallment second = installment(2, "50.00", "10.00");
        final Loan loan = loan(List.of(first, second), Set.of(charge(first, LocalDate.of(2026, 10, 11), "5.00"),
                charge(second, LocalDate.of(2026, 10, 11), "5.00"), charge(second, valueDate, "9.00")));

        assertThat(PartialBackdatedClearedInstallments.installmentNumbersClearedBy(loan, valueDate, new BigDecimal("170.00")))
                .containsExactly(1, 2);
    }

    private Loan loan(final List<LoanRepaymentScheduleInstallment> installments, final Set<LoanCharge> charges) {
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(installments);
        when(loan.getCharges()).thenReturn(charges);
        return loan;
    }

    private LoanRepaymentScheduleInstallment installment(final int number, final String principal, final String interest) {
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getInstallmentNumber()).thenReturn(number);
        when(installment.getPrincipalOutstanding(currency)).thenReturn(Money.of(currency, new BigDecimal(principal)));
        when(installment.getInterestOutstanding(currency)).thenReturn(Money.of(currency, new BigDecimal(interest)));
        when(installment.getFeeChargesOutstanding(currency)).thenReturn(Money.zero(currency));
        when(installment.getTaxChargesOutstanding(currency)).thenReturn(Money.zero(currency));
        return installment;
    }

    private LoanCharge charge(final LoanRepaymentScheduleInstallment installment, final LocalDate dueDate, final String outstanding) {
        final LoanOverdueInstallmentCharge link = mock(LoanOverdueInstallmentCharge.class);
        when(link.getInstallment()).thenReturn(installment);
        final LoanCharge charge = mock(LoanCharge.class);
        when(charge.isActive()).thenReturn(true);
        when(charge.isWaived()).thenReturn(false);
        when(charge.isOverdueInstallmentCharge()).thenReturn(true);
        when(charge.getOverdueInstallmentCharge()).thenReturn(link);
        when(charge.getDueLocalDate()).thenReturn(dueDate);
        when(charge.getAmountOutstanding(currency)).thenReturn(Money.of(currency, new BigDecimal(outstanding)));
        return charge;
    }
}
