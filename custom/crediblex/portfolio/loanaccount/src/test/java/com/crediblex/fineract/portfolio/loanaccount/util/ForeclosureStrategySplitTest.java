package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.FineractStyleLoanRepaymentScheduleTransactionProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ForeclosureStrategySplitTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("AED", 2, 0);
    private final LocalDate foreclosureDate = LocalDate.of(2026, 10, 26);

    @BeforeEach
    void setUp() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, foreclosureDate);
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "default", "UTC", null));
        final ConfigurationDomainService cfg = mock(ConfigurationDomainService.class);
        when(cfg.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", cfg);
        moneyHelper.initialize();
    }

    @Test
    void standardStrategyPaysOpenInstallmentPenaltyBeforePrincipal() {
        // Loan 10066. The charge quote was LPI 118.08 and principal 70,078.70. The standard strategy paid the open
        // installment's schedule penalty 339.48 first, so principal posted was 69,857.30. Same cash either way.
        final LoanRepaymentScheduleInstallment open = installment(5, LocalDate.of(2026, 9, 8), LocalDate.of(2026, 10, 8), "7981.10",
                "1852.41", "339.48");
        final LoanRepaymentScheduleInstallment current = installment(6, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 11, 10), "62097.60",
                "6737.00", "0");
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.transactionProcessingStrategy()).thenReturn(FineractStyleLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(open, current));

        final Money cash = money("72972.51");
        final ForeclosureStrategySplit.Split split = ForeclosureStrategySplit.allocate(loan, foreclosureDate, cash, money("70078.70"),
                money("2775.73"), money("0"), money("118.08"), money("0"));

        assertThat(split.penalty().getAmount()).isEqualByComparingTo("339.48");
        assertThat(split.interest().getAmount()).isEqualByComparingTo("2775.73");
        assertThat(split.principal().getAmount()).isEqualByComparingTo("69857.30");
        assertThat(split.principal().plus(split.interest()).plus(split.penalty()).getAmount()).isEqualByComparingTo("72972.51");
    }

    private LoanRepaymentScheduleInstallment installment(final int number, final LocalDate fromDate, final LocalDate dueDate,
            final String principal, final String interest, final String penalty) {
        return new LoanRepaymentScheduleInstallment(null, number, fromDate, dueDate, new BigDecimal(principal), new BigDecimal(interest),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(penalty), false, null, BigDecimal.ZERO);
    }

    private Money money(final String amount) {
        return Money.of(currency, new BigDecimal(amount));
    }
}
