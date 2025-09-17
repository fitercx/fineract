package com.crediblex.fineract.portfolio.loc.charge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LineOfCreditChargeDomainServiceTest {

    private LineOfCreditChargeDomainService service;
    private LineOfCredit loc; // simple reference object (no persistence needed for unit test)

    @BeforeEach
    void setUp() {
        service = new LineOfCreditChargeDomainService();
        loc = mock(LineOfCredit.class);
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
    @DisplayName("Create flat specified due date charge")
    void createFlatSpecifiedDueDate() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.FLAT, BigDecimal.valueOf(50), false, null, null);
        var applied = service.create(loc, charge, null, LocalDate.now().plusDays(3), null, null);
        assertThat(applied.getAmount()).isEqualTo(BigDecimal.valueOf(50));
        assertThat(applied.getAmountOutstanding()).isEqualTo(BigDecimal.valueOf(50));
        assertThat(applied.isPaid()).isFalse();
    }

    @Test
    @DisplayName("Percent charge base applied later")
    void percentChargeApplyBase() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.PERCENT_OF_AMOUNT, BigDecimal.valueOf(10), false,
                null, null); // 10%
        var applied = service.create(loc, charge, null, LocalDate.now().plusDays(1), null, null);
        assertThat(applied.getAmount()).isZero(); // not computed yet
        service.applyPercentBase(applied, BigDecimal.valueOf(200));
        assertThat(applied.getAmount()).isEqualByComparingTo("20.000000");
        assertThat(applied.getAmountOutstanding()).isEqualByComparingTo("20.000000");
    }

    @Test
    @DisplayName("Partial then full payment")
    void payInTwoSteps() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.FLAT, BigDecimal.valueOf(100), false, null, null);
        var applied = service.create(loc, charge, null, LocalDate.now().plusDays(2), null, null);
        service.pay(applied, BigDecimal.valueOf(30));
        assertThat(applied.getAmountOutstanding()).isEqualByComparingTo("70");
        assertThat(applied.isPaid()).isFalse();
        service.pay(applied, BigDecimal.valueOf(100)); // overpay scenario should cap at remaining 70
        assertThat(applied.getAmountOutstanding()).isZero();
        assertThat(applied.isPaid()).isTrue();
    }

    @Test
    @DisplayName("Waive recurring monthly fee advances cycle")
    void waiveMonthlyFeeAdvancesCycle() {
        MonthDay md = MonthDay.now();
        Charge charge = mockCharge(ChargeTimeType.MONTHLY_FEE, ChargeCalculationType.FLAT, BigDecimal.valueOf(15), false, md, 1);
        var applied = service.create(loc, charge, null, null, md, 1);
        LocalDate firstDue = applied.getChargeDueDate();
        service.waive(applied);
        // After waive + cycle advance: due date should move forward a month and outstanding restored to amount
        assertThat(applied.getChargeDueDate()).isAfter(firstDue);
        assertThat(applied.getAmountOutstanding()).isEqualByComparingTo("15");
        // Waived flag should be reset after cycle move (since new cycle started)
        assertThat(applied.isWaived()).isFalse();
        assertThat(applied.isPaid()).isFalse();
    }

    @Test
    @DisplayName("Reject unsupported calc type")
    void rejectUnsupportedCalcType() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.PERCENT_OF_DISBURSEMENT_AMOUNT,
                BigDecimal.valueOf(5), false, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.create(loc, charge, null, LocalDate.now().plusDays(1), null, null));
    }

    @Test
    @DisplayName("Reject missing due date for specified due date")
    void rejectMissingDueDate() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.FLAT, BigDecimal.TEN, false, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.create(loc, charge, null, null, null, null));
    }
}
