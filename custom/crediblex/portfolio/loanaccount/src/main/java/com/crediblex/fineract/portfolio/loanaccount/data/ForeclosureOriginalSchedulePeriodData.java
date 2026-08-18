package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ForeclosureOriginalSchedulePeriodData {

    private Integer installmentNumber;
    private LocalDate fromDate;
    private LocalDate dueDate;
    private BigDecimal principalDue;
    private BigDecimal interestDue;

    public ForeclosureOriginalSchedulePeriodData(final Integer installmentNumber, final LocalDate fromDate, final LocalDate dueDate,
            final BigDecimal principalDue, final BigDecimal interestDue) {
        this.installmentNumber = installmentNumber;
        this.fromDate = fromDate;
        this.dueDate = dueDate;
        this.principalDue = principalDue;
        this.interestDue = interestDue;
    }

}
