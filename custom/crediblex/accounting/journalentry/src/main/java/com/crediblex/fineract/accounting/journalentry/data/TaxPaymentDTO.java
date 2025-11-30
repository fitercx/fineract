package com.crediblex.fineract.accounting.journalentry.data;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TaxPaymentDTO {

    private BigDecimal amount;
    private Long creditAccountId;
    private Long debitAccountId;
}
