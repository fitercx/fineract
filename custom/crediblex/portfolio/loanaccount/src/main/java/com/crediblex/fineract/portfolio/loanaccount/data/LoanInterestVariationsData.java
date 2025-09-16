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
    private final transient BigDecimal originalApprovedInterestRate;

    public LoanInterestVariationsData(LocalDate fromDate, LocalDate toDate, BigDecimal decimalValue,
                                      BigDecimal originalApprovedInterestRate) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.decimalValue = decimalValue;
        this.originalApprovedInterestRate = originalApprovedInterestRate;
    }

    @Override
    public String toString() {
        return "InterestPeriod{" +
                "fromDate=" + fromDate +
                ", toDate=" + toDate +
                ", decimalValue=" + decimalValue +
                ", originalApprovedInterestRate=" + originalApprovedInterestRate +
                '}';
    }

    public static List<LoanInterestVariationsData> buildInterestPeriods(
            List<LoanTermVariationsData> variations,
            List<LoanSchedulePeriodData> schedule,
            BigDecimal originalApprovedInterestRate) {

        List<LoanInterestVariationsData> result = new ArrayList<>();

        if (schedule == null || schedule.isEmpty()) {
            return result;
        }

        // Sort schedule by fromDate
        schedule = schedule.stream()
                .sorted(Comparator.comparing(LoanSchedulePeriodData::getFromDate))
                .collect(Collectors.toList());

        // Sort variations by applicableFrom
        if (variations == null) variations = new ArrayList<>();
        else variations = variations.stream()
                .sorted(Comparator.comparing(LoanTermVariationsData::getTermVariationApplicableFrom))
                .collect(Collectors.toList());

        LocalDate firstInstallmentDueDate = schedule.get(0).getDueDate();

        // Add original interest period if first variation starts after first installment
        if (!variations.isEmpty() &&
                variations.get(0).getTermVariationApplicableFrom().isAfter(firstInstallmentDueDate)) {

            LocalDate firstVariationDate = variations.get(0).getTermVariationApplicableFrom();
            LocalDate toDate = schedule.stream()
                    .map(LoanSchedulePeriodData::getDueDate)
                    .filter(d -> !d.isAfter(firstVariationDate)) // <= firstVariationDate
                    .max(LocalDate::compareTo)
                    .orElse(firstInstallmentDueDate);

            result.add(new LoanInterestVariationsData(
                    firstInstallmentDueDate,
                    toDate,
                    originalApprovedInterestRate,
                    originalApprovedInterestRate
            ));
        }

        // Process all variations
        for (int i = 0; i < variations.size(); i++) {
            LoanTermVariationsData current = variations.get(i);
            LocalDate nextVariationDate = (i + 1 < variations.size())
                    ? variations.get(i + 1).getTermVariationApplicableFrom()
                    : null;

            // Determine toDate
            LocalDate toDate;
            if (nextVariationDate != null) {
                toDate = schedule.stream()
                        .map(LoanSchedulePeriodData::getDueDate)
                        .filter(d -> d.isBefore(nextVariationDate))
                        .max(LocalDate::compareTo)
                        .orElse(current.getTermVariationApplicableFrom());
            } else {
                toDate = schedule.stream()
                        .map(LoanSchedulePeriodData::getDueDate)
                        .max(LocalDate::compareTo)
                        .orElse(current.getTermVariationApplicableFrom());
            }

            // Safeguard: extend period if fromDate == toDate
            if (toDate != null && toDate.isEqual(current.getTermVariationApplicableFrom())) {
                toDate = schedule.stream()
                        .map(LoanSchedulePeriodData::getDueDate)
                        .filter(d -> d.isAfter(current.getTermVariationApplicableFrom()))
                        .min(LocalDate::compareTo)
                        .orElse(toDate);
            }

            result.add(new LoanInterestVariationsData(
                    current.getTermVariationApplicableFrom(),
                    toDate,
                    current.getDecimalValue(),
                    originalApprovedInterestRate
            ));
        }

        return result;
    }
}
