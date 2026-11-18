package com.crediblex.fineract.portfolio.loc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LineOfCreditDrawdownEndDateTest {

    @Test
    void drawdownDateMustBeOnOrBeforeEndDate() {
        final LineOfCredit lineOfCredit = new LineOfCredit();
        lineOfCredit.setStatus(LocStatus.ACTIVE);
        lineOfCredit.setEndDate(LocalDate.of(2027, 10, 19));
        lineOfCredit.getLineOfCreditStateChange().setActivateOnDate(LocalDate.of(2026, 1, 1));

        assertThat(lineOfCredit.canDrawDown(LocalDate.of(2027, 10, 19))).isTrue();
        assertThat(lineOfCredit.canDrawDown(LocalDate.of(2027, 10, 20))).isFalse();
        assertThat(lineOfCredit.canDrawDown(LocalDate.of(2025, 12, 31))).isFalse();
    }
}
