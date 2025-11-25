package com.crediblex.fineract.portfolio.loc.charge.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LocChargeData {

    Long id;
    Long chargeDefinitionId;
    boolean penalty;
    String chargeName;
    Integer chargeTime;
    Integer chargeCalculation;
    LocalDate dueDate;
    Integer feeOnMonth;
    Integer feeOnDay;
    Integer feeInterval;
    BigDecimal amount;
    BigDecimal amountOutstanding;
    BigDecimal amountPaid;
    BigDecimal amountWaived;
    boolean paid;
    boolean waived;
    boolean active;
}
