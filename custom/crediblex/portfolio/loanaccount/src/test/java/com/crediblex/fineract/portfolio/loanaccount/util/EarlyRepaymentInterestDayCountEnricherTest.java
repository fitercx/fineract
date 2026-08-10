package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.crediblex.fineract.portfolio.loanaccount.data.ForeclosureWaivedSchedulePeriodData;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EarlyRepaymentInterestDayCountEnricherTest {

    @Test
    void enrichPeriod_matchesLoan13858DayCounts() {
        final ForeclosureWaivedSchedulePeriodData period = new ForeclosureWaivedSchedulePeriodData(1, LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 8, 2), new BigDecimal("5385.21"), new BigDecimal("239.34"));

        EarlyRepaymentInterestDayCountEnricher.enrichPeriod(period, LocalDate.of(2026, 7, 29), new BigDecimal("5145.87"));

        assertThat(period.getPeriodDays()).isEqualTo(90);
        assertThat(period.getInterestChargedDays()).isEqualTo(86);
        assertThat(period.getInterestWaivedDays()).isEqualTo(4);
        assertThat(period.getInterestCharged()).isEqualByComparingTo("5145.87");
    }
}
