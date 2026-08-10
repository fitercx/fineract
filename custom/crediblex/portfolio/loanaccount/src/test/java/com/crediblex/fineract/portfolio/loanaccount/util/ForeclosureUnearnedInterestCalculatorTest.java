package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ForeclosureUnearnedInterestCalculatorTest {

    @Test
    void computeFromOriginalScheduleHistory_returnsDifferenceForForeclosedLoanExample() {
        final BigDecimal unearned = ForeclosureUnearnedInterestCalculator.computeFromOriginalScheduleHistory(new BigDecimal("5632.58"),
                new BigDecimal("1949.89"), BigDecimal.ZERO);

        assertThat(unearned).isEqualByComparingTo("3682.69");
    }

    @Test
    void computeFromOriginalScheduleHistory_neverReturnsNegative() {
        final BigDecimal unearned = ForeclosureUnearnedInterestCalculator.computeFromOriginalScheduleHistory(new BigDecimal("100"),
                new BigDecimal("120"), BigDecimal.ZERO);

        assertThat(unearned).isEqualByComparingTo("0");
    }
}
