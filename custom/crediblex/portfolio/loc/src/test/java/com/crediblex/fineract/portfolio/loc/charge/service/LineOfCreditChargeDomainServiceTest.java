package com.crediblex.fineract.portfolio.loc.charge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import java.math.BigDecimal;
import java.time.MonthDay;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LineOfCreditChargeDomainServiceTest {

    private LineOfCreditChargeDomainService service;
    private LineOfCredit loc;

    @BeforeEach
    void setUp() {
        service = new LineOfCreditChargeDomainService(mock(JournalEntryWritePlatformService.class));
        loc = mock(LineOfCredit.class);
        when(loc.getMaximumAmount()).thenReturn(BigDecimal.valueOf(1000));
    }

    private Charge mockCharge(ChargeTimeType timeType, ChargeCalculationType calcType, BigDecimal amount, boolean penalty, MonthDay md,
            Integer feeInterval) {
        Charge charge = mock(Charge.class);
        when(charge.getChargeTimeType()).thenReturn(timeType.getValue());
        when(charge.getChargeCalculation()).thenReturn(calcType.getValue());
        when(charge.getAmount()).thenReturn(amount);
        when(charge.isPenalty()).thenReturn(penalty);
        when(charge.getFeeOnMonthDay()).thenReturn(md);
        when(charge.feeInterval()).thenReturn(feeInterval);
        return charge;
    }

    @Test
    @DisplayName("Create flat charge")
    void createFlatCharge() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.FLAT, BigDecimal.valueOf(50), false, null,
                null);
        var applied = service.create(loc, charge, null);
        assertThat(applied.getAmount()).isEqualTo(BigDecimal.valueOf(50));
        assertThat(applied.getAmountOutstanding()).isEqualTo(BigDecimal.valueOf(50));
        assertThat(applied.isPaid()).isFalse();
    }

    @Test
    @DisplayName("Percent charge uses LOC maximum as base")
    void percentChargeUsesLocMaximum() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.PERCENT_OF_AMOUNT, BigDecimal.valueOf(10),
                false, null, null);
        var applied = service.create(loc, charge, null);
        assertThat(applied.getAmount()).isEqualByComparingTo("100.000");
        assertThat(applied.getAmountOutstanding()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("Partial then full payment")
    void payInTwoSteps() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.FLAT, BigDecimal.valueOf(100), false, null,
                null);
        var applied = service.create(loc, charge, null);
        service.pay(applied, BigDecimal.valueOf(30));
        assertThat(applied.getAmountOutstanding()).isEqualByComparingTo("70");
        assertThat(applied.isPaid()).isFalse();
        service.pay(applied, BigDecimal.valueOf(100));
        assertThat(applied.getAmountOutstanding()).isZero();
        assertThat(applied.isPaid()).isTrue();
    }
}
