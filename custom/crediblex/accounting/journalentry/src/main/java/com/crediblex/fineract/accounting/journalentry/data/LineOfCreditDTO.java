package com.crediblex.fineract.accounting.journalentry.data;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LineOfCreditDTO {

    private Long savingsAccountId;
    private Long savingsAccountTransactionId;
    private List<TaxPaymentDTO> taxPayments;
    private LocalDate transactionDate;
    private Long officeId;
    private String currencyCode;
    private boolean reversed;

}
