package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ForeclosureWaivedSchedulePeriodData {

    private Integer installmentNumber;
    private LocalDate fromDate;
    private LocalDate dueDate;
    private BigDecimal scheduledInterest;
    private BigDecimal waivedInterest;
    private LocalDate paymentDate;
    private BigDecimal interestCharged;
    private Integer periodDays;
    private Integer interestChargedDays;
    private Integer interestWaivedDays;

    public ForeclosureWaivedSchedulePeriodData(final Integer installmentNumber, final LocalDate fromDate, final LocalDate dueDate,
            final BigDecimal scheduledInterest, final BigDecimal waivedInterest) {
        this.installmentNumber = installmentNumber;
        this.fromDate = fromDate;
        this.dueDate = dueDate;
        this.scheduledInterest = scheduledInterest;
        this.waivedInterest = waivedInterest;
    }

}
