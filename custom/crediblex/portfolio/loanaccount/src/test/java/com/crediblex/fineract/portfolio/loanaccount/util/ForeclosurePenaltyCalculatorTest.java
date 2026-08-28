/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reproduces UAT RBF loan 000016184: seven daily "Daily Late Repayment Fee" penalty charges of 12.82 dated 20..26 Aug,
 * all LINKED to installment #1 (due 20 Aug). A client settling on day D must not be charged the late fee accrued on day
 * D (or later), so the foreclosure penalty is the sum of charges dated strictly before the settlement date.
 */
class ForeclosurePenaltyCalculatorTest {

    private final MonetaryCurrency currency = mock(MonetaryCurrency.class);
    private final ConfigurationDomainService configurationDomainService = mock(ConfigurationDomainService.class);

    @BeforeEach
    void setUp() {
        when(currency.getCode()).thenReturn("AED");
        when(currency.getDigitsAfterDecimal()).thenReturn(2);
        when(currency.getCurrencyInMultiplesOf()).thenReturn(0);
        when(currency.toData()).thenReturn(new CurrencyData("AED", "UAE Dirham", 2, 0, "AED", "currency.AED"));
        final MoneyHelper moneyHelper = new MoneyHelper();
        ReflectionTestUtils.setField(moneyHelper, "configurationDomainService", configurationDomainService);
        lenient().when(configurationDomainService.getRoundingMode()).thenReturn(BigDecimal.ROUND_HALF_UP);
        moneyHelper.initialize();
    }

    private Money money(final String amount) {
        return Money.of(currency, new BigDecimal(amount));
    }

    /** A daily overdue-installment (LPI) charge accrued on {@code accrualDate}, linked to an installment due on {@code owningDueDate}. */
    private LoanCharge linkedLpiCharge(final String accrualDate, final String owningDueDate, final String outstanding) {
        final Money outstandingMoney = money(outstanding);
        final LocalDate accrual = LocalDate.parse(accrualDate);
        final LocalDate owningDue = LocalDate.parse(owningDueDate);
        final LoanCharge charge = mock(LoanCharge.class);
        when(charge.isPenaltyCharge()).thenReturn(true);
        when(charge.getDueDate()).thenReturn(accrual);
        when(charge.getAmountOutstanding(currency)).thenReturn(outstandingMoney);
        lenient().when(charge.isOverdueInstallmentCharge()).thenReturn(true);
        final LoanOverdueInstallmentCharge link = mock(LoanOverdueInstallmentCharge.class);
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        lenient().when(installment.getDueDate()).thenReturn(owningDue);
        lenient().when(link.getInstallment()).thenReturn(installment);
        lenient().when(charge.getOverdueInstallmentCharge()).thenReturn(link);
        return charge;
    }

    /** Loan 16184: daily 12.82 charges dated 20..26 Aug, all linked to installment #1 due 20 Aug. */
    private Loan loan16184() {
        final Loan loan = mock(Loan.class);
        final LinkedHashSet<LoanCharge> charges = new LinkedHashSet<>();
        for (LocalDate d = LocalDate.parse("2026-08-20"); !d.isAfter(LocalDate.parse("2026-08-26")); d = d.plusDays(1)) {
            charges.add(linkedLpiCharge(d.toString(), "2026-08-20", "12.82"));
        }
        when(loan.getActiveCharges()).thenReturn(charges);
        return loan;
    }

    private BigDecimal penaltyAsOf(final Loan loan, final String settlementDate) {
        return ForeclosurePenaltyCalculator.computePenaltyPayableFromActiveCharges(loan, LocalDate.parse(settlementDate), currency)
                .getAmount();
    }

    @Test
    @DisplayName("Settling today collects all LPI accrued through yesterday (no charge dated today)")
    void settlingTodayCollectsAllAccrued() {
        assertThat(penaltyAsOf(loan16184(), "2026-08-27")).isEqualByComparingTo("89.74"); // 7 x 12.82
    }

    @Test
    @DisplayName("Settling on 26 Aug waives the 26 Aug LPI even though the charge is linked to the 20 Aug installment")
    void settlingOn26AugWaivesThatDaysLpi() {
        assertThat(penaltyAsOf(loan16184(), "2026-08-26")).isEqualByComparingTo("76.92"); // 6 x 12.82 (20..25)
    }

    @Test
    @DisplayName("Settling on 25 Aug waives both the 25 and 26 Aug LPI")
    void settlingOn25AugWaivesTwoDaysLpi() {
        assertThat(penaltyAsOf(loan16184(), "2026-08-25")).isEqualByComparingTo("64.10"); // 5 x 12.82 (20..24)
    }

    @Test
    @DisplayName("Quote is monotonic - one earlier settlement day drops exactly one day's LPI")
    void quoteIsMonotonicByDay() {
        final Loan loan = loan16184();
        assertThat(penaltyAsOf(loan, "2026-08-27")).isEqualByComparingTo("89.74");
        assertThat(penaltyAsOf(loan, "2026-08-26")).isEqualByComparingTo("76.92");
        assertThat(penaltyAsOf(loan, "2026-08-25")).isEqualByComparingTo("64.10");
        assertThat(penaltyAsOf(loan, "2026-08-24")).isEqualByComparingTo("51.28");
        // Settling on the installment due date itself is on-time: no LPI at all.
        assertThat(penaltyAsOf(loan, "2026-08-20")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Waived/zero-outstanding charges never contribute")
    void ignoresNonPenaltyCharges() {
        final LoanCharge fee = mock(LoanCharge.class);
        when(fee.isPenaltyCharge()).thenReturn(false);
        final Loan loan = mock(Loan.class);
        when(loan.getActiveCharges()).thenReturn(new LinkedHashSet<>(List.of(fee)));
        assertThat(penaltyAsOf(loan, "2026-08-26")).isEqualByComparingTo("0");
    }
}
