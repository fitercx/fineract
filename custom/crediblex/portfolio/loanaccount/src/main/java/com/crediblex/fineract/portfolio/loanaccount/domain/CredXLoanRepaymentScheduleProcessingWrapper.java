package com.crediblex.fineract.portfolio.loanaccount.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.tax.domain.TaxGroupMappings;
import org.apache.fineract.portfolio.tax.service.TaxUtils;

/**
 * CredX custom wrapper that assigns overdue charges (LPI) to the <b>linked installment</b> (the one that became
 * overdue) instead of the period containing the charge due date. This ensures LPI is shown in the <b>same month</b> as
 * the default (e.g., January LPI on January row, not February).
 */
@Slf4j
public class CredXLoanRepaymentScheduleProcessingWrapper
        extends org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleProcessingWrapper {

    @Override
    public void reprocess(final MonetaryCurrency currency, final LocalDate disbursementDate,
            final List<LoanRepaymentScheduleInstallment> repaymentPeriods, final Set<LoanCharge> loanCharges) {

        Money totalInterest = Money.zero(currency);
        Money totalPrincipal = Money.zero(currency);
        for (final LoanRepaymentScheduleInstallment installment : repaymentPeriods) {
            totalInterest = totalInterest.plus(installment.getInterestCharged(currency));
            totalPrincipal = totalPrincipal.plus(installment.getPrincipal(currency));
        }
        LocalDate startDate = disbursementDate;
        LoanRepaymentScheduleInstallment firstNormalPeriod = repaymentPeriods.stream()
                .sorted(Comparator.comparing(LoanRepaymentScheduleInstallment::getInstallmentNumber))
                .filter(repaymentPeriod -> !repaymentPeriod.isDownPayment()).findFirst().orElseThrow();

        final org.apache.fineract.portfolio.loanaccount.domain.Loan loan = firstNormalPeriod.getLoan();
        Money factorRateNetFeeAmountPerInstallment = Money.zero(currency);
        Money factorRateTaxAmountPerInstallment = Money.zero(currency);
        Money factorRateNetFeeAmount = Money.zero(currency);
        Money factorRateTaxAmount = Money.zero(currency);
        boolean isFactorRateEnabled = false;
        if (loan != null && loan.isFactorRateEnabled()) {
            isFactorRateEnabled = true;
            final LoanCharge factorRateInstallmentFee = loanCharges.stream().filter(LoanCharge::isInstalmentFee).findFirst().orElse(null);
            if (factorRateInstallmentFee != null && factorRateInstallmentFee.getLoan() != null) {
                final Integer numberOfRepayments = factorRateInstallmentFee.getLoan().getNumberOfRepayments();
                final Money[] factorRateFeeTaxPerInstallment = calculateFactorRateFeeTaxPerInstallment(currency, loanCharges);
                factorRateNetFeeAmount = factorRateFeeTaxPerInstallment[0];
                factorRateTaxAmount = factorRateFeeTaxPerInstallment[1];
                factorRateNetFeeAmountPerInstallment = factorRateNetFeeAmount.dividedBy(numberOfRepayments);
                factorRateTaxAmountPerInstallment = factorRateTaxAmount.dividedBy(numberOfRepayments);
            }
        }
        for (final LoanRepaymentScheduleInstallment period : repaymentPeriods) {
            if (!period.isDownPayment()) {
                boolean isFirstNonDownPaymentPeriod = period.equals(firstNormalPeriod);
                final int lastNormalInstallmentNumber = fetchLastNormalInstallmentNumber(repaymentPeriods);
                boolean isLastNonDownPaymentPeriod = period.getInstallmentNumber() != null
                        && period.getInstallmentNumber().equals(lastNormalInstallmentNumber);
                final Money taxesWaivedForRepaymentPeriod = Money.zero(currency);
                final Money taxesWrittenOffForRepaymentPeriod = Money.zero(currency);
                Money taxesDueForRepaymentPeriod = Money.zero(currency);
                Money feeChargesDueForRepaymentPeriod = cumulativeFeeChargesDueWithin(startDate, period.getDueDate(), loanCharges, currency,
                        period, totalPrincipal, totalInterest, !period.isRecalculatedInterestComponent(), isFirstNonDownPaymentPeriod);
                if (isFactorRateEnabled) {
                    feeChargesDueForRepaymentPeriod = factorRateNetFeeAmountPerInstallment;
                    taxesDueForRepaymentPeriod = factorRateTaxAmountPerInstallment;
                }

                if (isFactorRateEnabled && isLastNonDownPaymentPeriod) {
                    feeChargesDueForRepaymentPeriod = feeChargesDueForRepaymentPeriod.plus(factorRateNetFeeAmount
                            .minus(factorRateNetFeeAmountPerInstallment.multipliedBy(BigDecimal.valueOf(loan.getNumberOfRepayments()))));
                    taxesDueForRepaymentPeriod = taxesDueForRepaymentPeriod.plus(factorRateTaxAmount
                            .minus(factorRateTaxAmountPerInstallment.multipliedBy(BigDecimal.valueOf(loan.getNumberOfRepayments()))));
                }

                final Money feeChargesWaivedForRepaymentPeriod = cumulativeChargesWaivedWithin(startDate, period.getDueDate(), loanCharges,
                        currency, !period.isRecalculatedInterestComponent(), isFirstNonDownPaymentPeriod, feeCharge());
                final Money feeChargesWrittenOffForRepaymentPeriod = cumulativeChargesWrittenOffWithin(startDate, period.getDueDate(),
                        loanCharges, currency, !period.isRecalculatedInterestComponent(), isFirstNonDownPaymentPeriod, feeCharge());
                // CredX: Use custom penalty logic - LPI on same month (linked installment)
                final Money penaltyChargesDueForRepaymentPeriod = cumulativePenaltyChargesDueWithinCredX(startDate, period.getDueDate(),
                        loanCharges, currency, period, totalPrincipal, totalInterest, !period.isRecalculatedInterestComponent(),
                        isFirstNonDownPaymentPeriod, isLastNonDownPaymentPeriod, isFactorRateEnabled);
                final Money penaltyChargesWaivedForRepaymentPeriod = cumulativeChargesWaivedWithin(startDate, period.getDueDate(),
                        loanCharges, currency, !period.isRecalculatedInterestComponent(), isFirstNonDownPaymentPeriod,
                        LoanCharge::isPenaltyCharge);
                final Money penaltyChargesWrittenOffForRepaymentPeriod = cumulativeChargesWrittenOffWithin(startDate, period.getDueDate(),
                        loanCharges, currency, !period.isRecalculatedInterestComponent(), isFirstNonDownPaymentPeriod,
                        LoanCharge::isPenaltyCharge);

                period.updateChargePortion(feeChargesDueForRepaymentPeriod, feeChargesWaivedForRepaymentPeriod,
                        feeChargesWrittenOffForRepaymentPeriod, penaltyChargesDueForRepaymentPeriod, penaltyChargesWaivedForRepaymentPeriod,
                        penaltyChargesWrittenOffForRepaymentPeriod, taxesDueForRepaymentPeriod, taxesWaivedForRepaymentPeriod,
                        taxesWrittenOffForRepaymentPeriod);

                startDate = period.getDueDate();
            }
        }
    }

    /**
     * CredX custom: Assign overdue charges to the LINKED installment (same month as default), not the period containing
     * the charge due date.
     */
    private Money cumulativePenaltyChargesDueWithinCredX(final LocalDate periodStart, final LocalDate periodEnd,
            final Set<LoanCharge> loanCharges, final MonetaryCurrency currency, LoanRepaymentScheduleInstallment period,
            final Money totalPrincipal, final Money totalInterest, boolean isInstallmentChargeApplicable, boolean isFirstPeriod,
            boolean isLastPeriod, boolean isFactorRateEnabled) {

        Money cumulative = Money.zero(currency);

        for (final LoanCharge loanCharge : loanCharges) {
            if (loanCharge.isPenaltyCharge()) {
                final boolean isPenaltyDueForFactorRateLoan = isLastPeriod && isFactorRateEnabled
                        && DateUtils.isAfter(loanCharge.getDueDate(), periodStart);
                boolean isDue = loanCharge.isDueInPeriod(periodStart, periodEnd, isFirstPeriod) || isPenaltyDueForFactorRateLoan;

                if (loanCharge.isInstalmentFee() && isInstallmentChargeApplicable) {
                    cumulative = cumulative.plus(getInstallmentFeeCredX(currency, period, loanCharge));
                } else if (loanCharge.isOverdueInstallmentCharge()) {
                    // LPI SAME-MONTH FIX: Assign to linked installment (the one that became overdue)
                    if (loanCharge.getOverdueInstallmentCharge() != null
                            && loanCharge.getOverdueInstallmentCharge().getInstallment().equals(period)) {
                        Money chargeAmount = loanCharge.getChargeCalculation().isPercentageBased()
                                ? Money.of(currency, loanCharge.chargeAmount())
                                : loanCharge.getAmount(currency);
                        cumulative = cumulative.plus(chargeAmount);
                        if (log.isDebugEnabled()) {
                            LoanRepaymentScheduleInstallment inst = period;
                            if (inst.getLoan() != null) {
                                log.debug(
                                        "LPI same-month: loanId={}, installment#={}, dueDate={}, chargeAmount={}, assigned to same period",
                                        inst.getLoan().getId(), inst.getInstallmentNumber(), inst.getDueDate(), chargeAmount);
                            }
                        }
                    }
                } else if (isDue && loanCharge.getChargeCalculation().isPercentageBased()) {
                    BigDecimal amount = BigDecimal.ZERO;
                    if (loanCharge.getChargeCalculation().isPercentageOfAmountAndInterest()) {
                        amount = amount.add(totalPrincipal.getAmount()).add(totalInterest.getAmount());
                    } else if (loanCharge.getChargeCalculation().isPercentageOfInterest()) {
                        amount = amount.add(totalInterest.getAmount());
                    } else {
                        amount = amount.add(totalPrincipal.getAmount());
                    }
                    cumulative = cumulative
                            .plus(Money.of(currency, amount.multiply(loanCharge.getPercentage()).divide(BigDecimal.valueOf(100))));
                } else if (isDue) {
                    cumulative = cumulative.plus(loanCharge.amount());
                }
            }
        }

        return cumulative;
    }

    private Money cumulativeFeeChargesDueWithin(final LocalDate periodStart, final LocalDate periodEnd, final Set<LoanCharge> loanCharges,
            final MonetaryCurrency monetaryCurrency, LoanRepaymentScheduleInstallment period, final Money totalPrincipal,
            final Money totalInterest, boolean isInstallmentChargeApplicable, boolean isFirstPeriod) {

        Money cumulative = Money.zero(monetaryCurrency);
        for (final LoanCharge loanCharge : loanCharges) {
            if (loanCharge.isFeeCharge() && !loanCharge.isDueAtDisbursement()) {
                boolean isDue = loanCharge.isDueInPeriod(periodStart, periodEnd, isFirstPeriod);
                if (loanCharge.isInstalmentFee() && isInstallmentChargeApplicable) {
                    cumulative = cumulative.plus(getInstallmentFeeCredX(monetaryCurrency, period, loanCharge));
                } else if (loanCharge.isOverdueInstallmentCharge() && isDue && loanCharge.getChargeCalculation().isPercentageBased()) {
                    cumulative = cumulative.plus(loanCharge.chargeAmount());
                } else if (isDue && loanCharge.getChargeCalculation().isPercentageBased()) {
                    BigDecimal amount = BigDecimal.ZERO;
                    if (loanCharge.getChargeCalculation().isPercentageOfAmountAndInterest()) {
                        amount = amount.add(totalPrincipal.getAmount()).add(totalInterest.getAmount());
                    } else if (loanCharge.getChargeCalculation().isPercentageOfInterest()) {
                        amount = amount.add(totalInterest.getAmount());
                    } else {
                        if (loanCharge.getLoan() != null && loanCharge.isSpecifiedDueDate()
                                && loanCharge.getLoan().isMultiDisburmentLoan()) {
                            for (final LoanDisbursementDetails d : loanCharge.getLoan().getDisbursementDetails()) {
                                if (!DateUtils.isAfter(d.expectedDisbursementDate(), loanCharge.getDueDate())) {
                                    amount = amount.add(d.principal());
                                }
                            }
                        } else {
                            amount = amount.add(totalPrincipal.getAmount());
                        }
                    }
                    cumulative = cumulative
                            .plus(Money.of(monetaryCurrency, amount.multiply(loanCharge.getPercentage()).divide(BigDecimal.valueOf(100))));
                } else if (isDue) {
                    cumulative = cumulative.plus(loanCharge.amount());
                }
            }
        }
        return cumulative;
    }

    private Money cumulativeChargesWaivedWithin(final LocalDate periodStart, final LocalDate periodEnd, final Set<LoanCharge> loanCharges,
            final MonetaryCurrency currency, boolean isInstallmentChargeApplicable, boolean isFirstPeriod,
            Predicate<LoanCharge> predicate) {

        Money cumulative = Money.zero(currency);
        for (final LoanCharge loanCharge : loanCharges) {
            if (predicate.test(loanCharge)) {
                boolean isDue = loanCharge.isDueInPeriod(periodStart, periodEnd, isFirstPeriod);
                if (loanCharge.isInstalmentFee() && isInstallmentChargeApplicable) {
                    LoanInstallmentCharge loanChargePerInstallment = loanCharge.getInstallmentLoanCharge(periodEnd);
                    if (loanChargePerInstallment != null) {
                        cumulative = cumulative.plus(loanChargePerInstallment.getAmountWaived(currency));
                    }
                } else if (isDue) {
                    cumulative = cumulative.plus(loanCharge.getAmountWaived(currency));
                }
            }
        }
        return cumulative;
    }

    private Money cumulativeChargesWrittenOffWithin(final LocalDate periodStart, final LocalDate periodEnd,
            final Set<LoanCharge> loanCharges, final MonetaryCurrency currency, boolean isInstallmentChargeApplicable,
            boolean isFirstPeriod, Predicate<LoanCharge> chargePredicate) {

        Money cumulative = Money.zero(currency);
        for (final LoanCharge loanCharge : loanCharges) {
            if (chargePredicate.test(loanCharge)) {
                boolean isDue = loanCharge.isDueInPeriod(periodStart, periodEnd, isFirstPeriod);
                if (loanCharge.isInstalmentFee() && isInstallmentChargeApplicable) {
                    LoanInstallmentCharge loanChargePerInstallment = loanCharge.getInstallmentLoanCharge(periodEnd);
                    if (loanChargePerInstallment != null) {
                        cumulative = cumulative.plus(loanChargePerInstallment.getAmountWrittenOff(currency));
                    }
                } else if (isDue) {
                    cumulative = cumulative.plus(loanCharge.getAmountWrittenOff(currency));
                }
            }
        }
        return cumulative;
    }

    private Predicate<LoanCharge> feeCharge() {
        return loanCharge -> loanCharge.isFeeCharge() && !loanCharge.isDueAtDisbursement();
    }

    private Money getInstallmentFeeCredX(MonetaryCurrency currency, LoanRepaymentScheduleInstallment period, LoanCharge loanCharge) {
        if (loanCharge.getChargeCalculation().isPercentageBased()) {
            BigDecimal amount = BigDecimal.ZERO;
            amount = getBaseAmountCredX(currency, period, loanCharge, amount);
            return Money.of(currency, amount.multiply(loanCharge.getPercentage()).divide(BigDecimal.valueOf(100)));
        } else {
            return Money.of(currency, loanCharge.amountOrPercentage());
        }
    }

    private BigDecimal getBaseAmountCredX(MonetaryCurrency monetaryCurrency, LoanRepaymentScheduleInstallment period, LoanCharge loanCharge,
            BigDecimal amount) {
        if (loanCharge.getChargeCalculation().isPercentageOfAmountAndInterest()) {
            amount = amount.add(period.getPrincipal(monetaryCurrency).getAmount())
                    .add(period.getInterestCharged(monetaryCurrency).getAmount());
        } else if (loanCharge.getChargeCalculation().isPercentageOfInterest()) {
            amount = amount.add(period.getInterestCharged(monetaryCurrency).getAmount());
        } else {
            amount = amount.add(period.getPrincipal(monetaryCurrency).getAmount());
        }
        return amount;
    }

    private Money[] calculateFactorRateFeeTaxPerInstallment(final MonetaryCurrency currency, final Set<LoanCharge> loanCharges) {
        final LoanCharge factorRateInstallmentFee = loanCharges.stream().filter(LoanCharge::isInstalmentFee).findFirst().orElse(null);
        Money factorRateTaxAmountMoney = Money.zero(currency);
        Money factorRateNetFeeAmountMoney = Money.zero(currency);
        if (factorRateInstallmentFee != null) {
            final org.apache.fineract.portfolio.loanaccount.domain.Loan loan = factorRateInstallmentFee.getLoan();
            if (loan != null && loan.isFactorRateEnabled()) {
                final LocalDate chargeDate = factorRateInstallmentFee.getDueDate() != null ? factorRateInstallmentFee.getDueDate()
                        : DateUtils.getBusinessLocalDate();
                final BigDecimal loanAmount = factorRateInstallmentFee.getLoan().getFactorRateLoanAmount();
                final BigDecimal factorRate = factorRateInstallmentFee.getLoan().getFactorRate();
                final Set<TaxGroupMappings> taxGroupMappings = factorRateInstallmentFee.getCharge().getTaxGroup() != null
                        ? factorRateInstallmentFee.getCharge().getTaxGroup().getTaxGroupMappings()
                        : Collections.emptySet();
                final BigDecimal taxAmount = TaxUtils.calculateFactorRateTaxAmount(loanAmount, chargeDate, factorRate, taxGroupMappings);
                final BigDecimal principalAmount = factorRateInstallmentFee.getLoan().getPrincipal().getAmount();
                final BigDecimal totalFactorRateFeeAmount = loanAmount.subtract(principalAmount);
                final BigDecimal factorRateNetFeeAmount = totalFactorRateFeeAmount.subtract(taxAmount);
                factorRateNetFeeAmountMoney = Money.of(currency, factorRateNetFeeAmount);
                factorRateTaxAmountMoney = Money.of(currency, taxAmount);
            }
        }
        return new Money[] { factorRateNetFeeAmountMoney, factorRateTaxAmountMoney };
    }
}
