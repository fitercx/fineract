package com.crediblex.fineract.portfolio.loanaccount.util;

import com.crediblex.fineract.portfolio.loanaccount.data.ForeclosureWaivedSchedulePeriodData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ForeclosureWaivedPeriodCalculator {

    private ForeclosureWaivedPeriodCalculator() {}

    public static List<ForeclosureWaivedSchedulePeriodData> computeWaivedPeriods(final LocalDate foreclosureDate,
            final List<OriginalInstallmentRow> originalInstallments, final List<CurrentInstallmentRow> currentInstallments) {
        if (foreclosureDate == null || originalInstallments == null || originalInstallments.isEmpty()) {
            return List.of();
        }

        final Map<LocalDate, BigDecimal> earnedInterestByFromDate = new HashMap<>();
        if (currentInstallments != null) {
            for (final CurrentInstallmentRow currentInstallment : currentInstallments) {
                if (currentInstallment.fromDate() != null && currentInstallment.interestAmount() != null) {
                    earnedInterestByFromDate.merge(currentInstallment.fromDate(), currentInstallment.interestAmount(), BigDecimal::add);
                }
            }
        }

        final List<ForeclosureWaivedSchedulePeriodData> waivedPeriods = new ArrayList<>();
        for (final OriginalInstallmentRow originalInstallment : originalInstallments) {
            if (originalInstallment.dueDate() == null || originalInstallment.interestAmount() == null) {
                continue;
            }

            final BigDecimal scheduledInterest = originalInstallment.interestAmount();
            if (scheduledInterest.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (originalInstallment.dueDate().isAfter(foreclosureDate)) {
                BigDecimal waivedInterest = scheduledInterest;
                if (originalInstallment.fromDate() != null && !originalInstallment.fromDate().isAfter(foreclosureDate)) {
                    final BigDecimal earnedInterest = earnedInterestByFromDate.getOrDefault(originalInstallment.fromDate(),
                            BigDecimal.ZERO);
                    waivedInterest = scheduledInterest.subtract(earnedInterest);
                    if (waivedInterest.compareTo(BigDecimal.ZERO) < 0) {
                        waivedInterest = BigDecimal.ZERO;
                    }
                }

                if (waivedInterest.compareTo(BigDecimal.ZERO) > 0) {
                    waivedPeriods.add(new ForeclosureWaivedSchedulePeriodData(originalInstallment.installmentNumber(),
                            originalInstallment.fromDate(), originalInstallment.dueDate(), scheduledInterest, waivedInterest));
                }
            }
        }

        return waivedPeriods;
    }

    public record OriginalInstallmentRow(Integer installmentNumber, LocalDate fromDate, LocalDate dueDate, BigDecimal interestAmount) {
    }

    public record CurrentInstallmentRow(LocalDate fromDate, LocalDate dueDate, BigDecimal interestAmount) {
    }

}
