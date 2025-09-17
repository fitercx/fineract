package com.crediblex.fineract.portfolio.loc.charge.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LocChargeCreateCommand {

    Long chargeId;
    BigDecimal overrideAmount; // nullable
    LocalDate dueDate; // for SPECIFIED_DUE_DATE or WEEKLY_FEE
    MonthDay feeOnMonthDay; // for monthly/annual
    Integer feeInterval; // for recurring (monthly/weekly)
}
