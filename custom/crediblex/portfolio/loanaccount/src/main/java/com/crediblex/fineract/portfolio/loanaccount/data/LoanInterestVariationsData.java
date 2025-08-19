package com.crediblex.fineract.portfolio.loanaccount.data;

import lombok.Getter;
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class LoanInterestVariationsData {
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final BigDecimal decimalValue;

    public LoanInterestVariationsData(LocalDate fromDate, LocalDate toDate, BigDecimal decimalValue) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.decimalValue = decimalValue;
    }

    @Override
    public String toString() {
        return "InterestPeriod{" +
                "fromDate=" + fromDate +
                ", toDate=" + toDate +
                ", decimalValue=" + decimalValue +
                '}';
    }

    /**
     * Factory method to merge loan term variations with repayment schedule and build InterestVariations.
     */
    public static List<LoanInterestVariationsData> buildInterestPeriods(
            List<LoanTermVariationsData> variations,
            List<LoanSchedulePeriodData> schedule) {

        List<LoanInterestVariationsData> result = new ArrayList<>();

        if (variations == null || variations.isEmpty() ||
                schedule == null || schedule.isEmpty()) {
            return result;
        }

        // Sort both lists by date
        variations = variations.stream()
                .sorted(Comparator.comparing(LoanTermVariationsData::getTermVariationApplicableFrom))
                .collect(Collectors.toList());

        schedule = schedule.stream()
                .sorted(Comparator.comparing(LoanSchedulePeriodData::getFromDate))
                .collect(Collectors.toList());

        for (int i = 0; i < variations.size(); i++) {
            LoanTermVariationsData current = variations.get(i);

            // Look ahead to next variation
            LocalDate nextVariationDate = (i + 1 < variations.size())
                    ? variations.get(i + 1).getTermVariationApplicableFrom()
                    : null;

            // Find max fromDate in schedule that is < nextVariationDate
            LocalDate toDate = schedule.stream()
                    .map(LoanSchedulePeriodData::getFromDate)
                    .filter(d -> nextVariationDate == null || d.isBefore(nextVariationDate))
                    .max(LocalDate::compareTo)
                    .orElse(null);

            result.add(new LoanInterestVariationsData(
                    current.getTermVariationApplicableFrom(),
                    toDate,
                    current.getDecimalValue()
            ));
        }

        return result;
    }
}
