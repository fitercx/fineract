package org.apache.fineract.portfolio.loanaccount.service;

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
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OverduePenaltyArrearsTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);
    private final LocalDate businessDate = LocalDate.of(2026, 10, 18);

    @BeforeEach
    void setUp() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, businessDate);
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "default", "UTC", null));
        final ConfigurationDomainService cfg = mock(ConfigurationDomainService.class);
        when(cfg.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", cfg);
        moneyHelper.initialize();
    }

    @Test
    void payingTheNextInstallmentLeavesOnlyItsUnpaidDays() {
        final Loan loan = loan(charge(LocalDate.of(2026, 10, 14), "0.00", false), charge(LocalDate.of(2026, 10, 15), "0.00", false),
                charge(LocalDate.of(2026, 10, 16), "12.52", false), charge(LocalDate.of(2026, 10, 17), "12.52", false));

        assertThat(OverduePenaltyArrears.unpaidBefore(loan, businessDate)).isEqualByComparingTo("25.04");
    }

    @Test
    void aClearedInstallmentDoesNotAddANegative() {
        final Loan loan = loan(charge(LocalDate.of(2026, 10, 10), "0.00", true), charge(LocalDate.of(2026, 10, 16), "12.52", false));

        assertThat(OverduePenaltyArrears.unpaidBefore(loan, businessDate)).isEqualByComparingTo("12.52");
    }

    private Loan loan(final LoanCharge... charges) {
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getCharges()).thenReturn(Set.of(charges));
        return loan;
    }

    private LoanCharge charge(final LocalDate dueDate, final String outstanding, final boolean waived) {
        final LoanCharge charge = mock(LoanCharge.class);
        when(charge.isOverdueInstallmentCharge()).thenReturn(true);
        when(charge.isActive()).thenReturn(true);
        when(charge.isWaived()).thenReturn(waived);
        when(charge.getDueLocalDate()).thenReturn(dueDate);
        final Money amount = Money.of(currency, new BigDecimal(outstanding));
        when(charge.getAmountOutstanding(currency)).thenReturn(amount);
        return charge;
    }
}
