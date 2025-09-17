package com.crediblex.fineract.portfolio.loc.charge.validation;

import com.crediblex.fineract.portfolio.loc.charge.LocChargeConstants;
import com.crediblex.fineract.portfolio.loc.charge.command.LocChargeCreateCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.springframework.stereotype.Component;

@Component
public class LocChargeValidator {

    public void validateCreate(Charge chargeDefinition, LocChargeCreateCommand cmd) {
        ChargeTimeType timeType = ChargeTimeType.fromInt(chargeDefinition.getChargeTimeType());
        if (!LocChargeConstants.isSupportedChargeTime(timeType)) {
            throw new IllegalArgumentException("Unsupported charge time for LOC: " + timeType);
        }
        ChargeCalculationType calcType = ChargeCalculationType.fromInt(chargeDefinition.getChargeCalculation());
        if (!(calcType == ChargeCalculationType.FLAT || calcType == ChargeCalculationType.PERCENT_OF_AMOUNT)) {
            throw new IllegalArgumentException("Unsupported calculation type for LOC: " + calcType);
        }
        if (cmd.getOverrideAmount() != null && cmd.getOverrideAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("overrideAmount must be > 0 when provided");
        }
        if (timeType.isOnSpecifiedDueDate() || timeType.isWeeklyFee()) {
            require(cmd.getDueDate() != null, "dueDate required for specified/weekly");
        }
        if (timeType.isMonthlyFee() || timeType.isAnnualFee()) {
            require((cmd.getFeeOnMonthDay() != null) || chargeDefinition.getFeeOnMonthDay() != null,
                    "feeOnMonthDay required for monthly/annual");
        }
        if (timeType.isWeeklyFee()) {
            require(cmd.getDueDate() != null, "dueDate required for weekly fee");
        }
        if (cmd.getDueDate() != null) {
            LocalDate today = LocalDate.now();
            require(!cmd.getDueDate().isBefore(today.minusYears(1)), "dueDate too far in past");
        }
    }

    private static void require(boolean condition, String msg) { if (!condition) throw new IllegalArgumentException(msg); }
}

