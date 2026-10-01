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

    private LoanCharge overdue(final String outstanding) {
        final LoanCharge c = mock(LoanCharge.class);
        final Money outstandingMoney = Money.of(currency, new BigDecimal(outstanding));
        org.mockito.Mockito.doReturn(true).when(c).isActive();
        org.mockito.Mockito.doReturn(true).when(c).isOverdueInstallmentCharge();
        org.mockito.Mockito.doReturn(false).when(c).isWaived();
        org.mockito.Mockito.doReturn(LocalDate.of(2026, 8, 13)).when(c).getDueLocalDate();
        org.mockito.Mockito.doReturn(outstandingMoney).when(c).getAmountOutstanding(currency);
        return c;
    }
}
