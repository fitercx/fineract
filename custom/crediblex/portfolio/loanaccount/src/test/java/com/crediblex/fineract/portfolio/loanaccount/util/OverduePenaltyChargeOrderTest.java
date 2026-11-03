package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OverduePenaltyChargeOrderTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);

    @BeforeEach
    void setUp() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 15));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "default", "UTC", null));
        final ConfigurationDomainService cfg = mock(ConfigurationDomainService.class);
        when(cfg.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", cfg);
        moneyHelper.initialize();
    }

    @Test
    void paysTheOldestInstallmentBeforeALaterInstallmentOnTheSameDay() {
        final LoanCharge oldestLaterDay = charge(1L, 1, LocalDate.of(2026, 10, 7), "12.82");
        final LoanCharge secondSameDay = charge(2L, 2, LocalDate.of(2026, 10, 7), "13.16");
        final LoanCharge oldestEarlierDay = charge(3L, 1, LocalDate.of(2026, 10, 6), "12.82");

        assertThat(OverduePenaltyChargeOrder.nextUnpaid(Set.of(secondSameDay, oldestLaterDay, oldestEarlierDay), currency))
                .isSameAs(oldestEarlierDay);
    }

    @Test
    void doesNotPayTheSecondInstallmentWhileTheOldestStillHasALaterUnpaidDay() {
        final LoanCharge oldestLaterDay = charge(1L, 1, LocalDate.of(2026, 10, 14), "12.82");
        final LoanCharge secondEarlierDay = charge(2L, 2, LocalDate.of(2026, 9, 30), "13.16");

        assertThat(OverduePenaltyChargeOrder.nextUnpaid(Set.of(secondEarlierDay, oldestLaterDay), currency)).isSameAs(oldestLaterDay);
    }

    @Test
    void leavesASingleInstallmentToDateOrder() {
        final LoanCharge only = charge(1L, 1, LocalDate.of(2026, 10, 6), "12.82");

        assertThat(OverduePenaltyChargeOrder.nextUnpaid(Set.of(only), currency)).isNull();
    }

    private LoanCharge charge(final long id, final int installmentNumber, final LocalDate dueDate, final String outstanding) {
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getInstallmentNumber()).thenReturn(installmentNumber);
        final LoanOverdueInstallmentCharge link = mock(LoanOverdueInstallmentCharge.class);
        when(link.getInstallment()).thenReturn(installment);
        final LoanCharge charge = mock(LoanCharge.class);
        when(charge.getId()).thenReturn(id);
        when(charge.isOverdueInstallmentCharge()).thenReturn(true);
        when(charge.isActive()).thenReturn(true);
        when(charge.isWaived()).thenReturn(false);
        when(charge.isDueAtDisbursement()).thenReturn(false);
        when(charge.getDueLocalDate()).thenReturn(dueDate);
        when(charge.getOverdueInstallmentCharge()).thenReturn(link);
        when(charge.getAmountOutstanding(currency)).thenReturn(Money.of(currency, new BigDecimal(outstanding)));
        return charge;
    }
}
