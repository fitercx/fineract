package com.crediblex.fineract.portfolio.loanaccount.util;

import com.crediblex.fineract.portfolio.loanaccount.data.ForeclosureUnearnedInterestDetailsData;
import com.crediblex.fineract.portfolio.loanaccount.data.ForeclosureWaivedSchedulePeriodData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class EarlyRepaymentInterestDayCountEnricher {

    private EarlyRepaymentInterestDayCountEnricher() {}

    public static void enrichPeriod(final ForeclosureWaivedSchedulePeriodData period, final LocalDate paymentDate,
            final BigDecimal interestCharged) {
        period.setPaymentDate(paymentDate);
        period.setInterestCharged(interestCharged);
        if (period.getFromDate() == null || period.getDueDate() == null || paymentDate == null) {
            return;
        }
        final int periodDays = (int) ChronoUnit.DAYS.between(period.getFromDate(), period.getDueDate());
        final int chargedDays = (int) ChronoUnit.DAYS.between(period.getFromDate(), paymentDate);
        final int waivedDays = Math.max(periodDays - chargedDays, 0);
        period.setPeriodDays(periodDays);
        period.setInterestChargedDays(Math.max(chargedDays, 0));
        period.setInterestWaivedDays(waivedDays);
    }

    public static void enrichSummaryFromFirstPeriod(final ForeclosureUnearnedInterestDetailsData summary,
            final ForeclosureWaivedSchedulePeriodData firstPeriod) {
        if (summary == null || firstPeriod == null) {
            return;
        }
        summary.setPaymentDate(firstPeriod.getPaymentDate());
        summary.setPeriodStartDate(firstPeriod.getFromDate());
        summary.setPeriodDays(firstPeriod.getPeriodDays());
        summary.setInterestChargedDays(firstPeriod.getInterestChargedDays());
        summary.setInterestWaivedDays(firstPeriod.getInterestWaivedDays());
    }

}
