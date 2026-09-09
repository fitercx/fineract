package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ForeclosureUnearnedInterestDetailsData {

    private BigDecimal unearnedInterest;
    private LocalDate foreclosureDate;
    private LocalDate originalMaturityDate;
    private Integer remainingDays;
    private Integer removedInstallmentCount;
    private BigDecimal originalScheduleInterest;
    private BigDecimal interestCollected;
    private List<ForeclosureWaivedSchedulePeriodData> waivedPeriods;
    /** Original generator schedule (version-1 history) — preserved for UI after foreclosure rewrite. */
    private List<ForeclosureOriginalSchedulePeriodData> originalSchedulePeriods;
    /** {@code EARLY_REPAYMENT} or {@code FORECLOSURE} */
    private String closureType;
    private LocalDate paymentDate;
    private LocalDate periodStartDate;
    private Integer periodDays;
    private Integer interestChargedDays;
    private Integer interestWaivedDays;

    public ForeclosureUnearnedInterestDetailsData(final BigDecimal unearnedInterest, final LocalDate foreclosureDate,
            final LocalDate originalMaturityDate, final Integer remainingDays, final Integer removedInstallmentCount,
            final BigDecimal originalScheduleInterest, final BigDecimal interestCollected,
            final List<ForeclosureWaivedSchedulePeriodData> waivedPeriods, final String closureType) {
        this.unearnedInterest = unearnedInterest;
        this.foreclosureDate = foreclosureDate;
        this.originalMaturityDate = originalMaturityDate;
        this.remainingDays = remainingDays;
        this.removedInstallmentCount = removedInstallmentCount;
        this.originalScheduleInterest = originalScheduleInterest;
        this.interestCollected = interestCollected;
        this.waivedPeriods = waivedPeriods;
        this.closureType = closureType;
    }

}
