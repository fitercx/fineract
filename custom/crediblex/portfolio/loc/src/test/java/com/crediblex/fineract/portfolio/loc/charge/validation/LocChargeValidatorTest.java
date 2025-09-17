package com.crediblex.fineract.portfolio.loc.charge.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loc.charge.command.LocChargeCreateCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocChargeValidatorTest {

    private final LocChargeValidator validator = new LocChargeValidator();

    private Charge mockCharge(ChargeTimeType timeType, ChargeCalculationType calcType, boolean penalty, MonthDay md) {
        Charge charge = mock(Charge.class);
        when(charge.getChargeTimeType()).thenReturn(timeType.getValue());
        when(charge.getChargeCalculation()).thenReturn(calcType.getValue());
        when(charge.getFeeOnMonthDay()).thenReturn(md);
        when(charge.isPenalty()).thenReturn(penalty);
        when(charge.getAmount()).thenReturn(BigDecimal.TEN);
        return charge;
    }

    @Test
    @DisplayName("Valid specified due date flat charge passes validation")
    void validSpecifiedDueDate() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.FLAT, false, null);
        LocChargeCreateCommand cmd = LocChargeCreateCommand.builder().chargeId(1L).dueDate(LocalDate.now().plusDays(2))
                .overrideAmount(BigDecimal.ONE).build();
        assertThatCode(() -> validator.validateCreate(charge, cmd)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Missing due date rejected")
    void missingDueDate() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.FLAT, false, null);
        LocChargeCreateCommand cmd = LocChargeCreateCommand.builder().chargeId(1L).build();
        assertThrows(IllegalArgumentException.class, () -> validator.validateCreate(charge, cmd));
    }

    @Test
    @DisplayName("Monthly fee requires monthDay")
    void monthlyFeeMissingMonthDay() {
        Charge charge = mockCharge(ChargeTimeType.MONTHLY_FEE, ChargeCalculationType.FLAT, false, null);
        LocChargeCreateCommand cmd = LocChargeCreateCommand.builder().chargeId(1L).build();
        assertThrows(IllegalArgumentException.class, () -> validator.validateCreate(charge, cmd));
    }

    @Test
    @DisplayName("Unsupported calculation type rejected")
    void unsupportedCalcType() {
        Charge charge = mockCharge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.PERCENT_OF_DISBURSEMENT_AMOUNT, false, null);
        LocChargeCreateCommand cmd = LocChargeCreateCommand.builder().chargeId(1L).dueDate(LocalDate.now().plusDays(1)).build();
        assertThrows(IllegalArgumentException.class, () -> validator.validateCreate(charge, cmd));
    }
}
