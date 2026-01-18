package com.crediblex.fineract.portfolio.loanaccount.loanschedule.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementData;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleModelDownPaymentPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleParams;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.CumulativeDecliningBalanceInterestLoanScheduleGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelDisbursementPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.PaymentPeriodsInOneYearCalculator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.PrincipalInterest;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.exception.MultiDisbursementOutstandingAmoutException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Custom implementation of CumulativeDecliningBalanceInterestLoanScheduleGenerator that fixes multi-tranche loan
 * schedule calculation to use only disbursed amounts.
 */
@Slf4j
@Component
@Primary
public class CustomCumulativeDecliningBalanceLoanScheduleGenerator extends CumulativeDecliningBalanceInterestLoanScheduleGenerator {

    // Thread-local storage for loan charges to be used in processDisbursements
    private static final ThreadLocal<Set<LoanCharge>> loanChargesThreadLocal = new ThreadLocal<>();

    public CustomCumulativeDecliningBalanceLoanScheduleGenerator(ScheduledDateGenerator scheduledDateGenerator,
            PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator) {
        super(scheduledDateGenerator, paymentPeriodsInOneYearCalculator);
    }

    @Override
    public LoanScheduleModel generate(final MathContext mc, final LoanApplicationTerms loanApplicationTerms,
            final Set<LoanCharge> loanCharges, final HolidayDetailDTO holidayDetailDTO) {
        try {
            // Store loan charges in thread-local for use in processDisbursements
            loanChargesThreadLocal.set(loanCharges);
            return super.generate(mc, loanApplicationTerms, loanCharges, holidayDetailDTO);
        } finally {
            // Clean up thread-local to prevent memory leaks
            loanChargesThreadLocal.remove();
        }
    }

    /**
     * Override to fix multi-tranche loan schedule calculation.
     *
     * For multi-disbursal loans: - If loan has disbursed amounts (existing loan), use only DISBURSED amount - If loan
     * has no disbursed amounts but has expected tranches (pre-creation calculateLoanSchedule API), use ALL expected
     * tranches (getTotalMultiDisbursedAmount)
     *
     * This ensures: - Existing loans: schedules reflect only actual disbursed principal - Pre-creation API: schedules
     * include all expected tranches for preview
     */
    @Override
    protected Money getPrincipalToBeScheduled(final LoanApplicationTerms loanApplicationTerms) {
        Money principalToBeScheduled;
        if (loanApplicationTerms.isMultiDisburseLoan()) {
            Money totalDisbursed = loanApplicationTerms.getTotalDisbursedAmount();
            Money totalMultiDisbursed = loanApplicationTerms.getTotalMultiDisbursedAmount();

            if (totalDisbursed.isGreaterThanZero()) {
                // Existing loan with disbursed amounts - use only disbursed amount
                principalToBeScheduled = totalDisbursed;
            } else if (totalMultiDisbursed.isGreaterThanZero()) {
                // Pre-creation scenario (calculateLoanSchedule API) - use all expected tranches
                principalToBeScheduled = totalMultiDisbursed;
            } else if (loanApplicationTerms.getApprovedPrincipal().isGreaterThanZero()) {
                principalToBeScheduled = loanApplicationTerms.getApprovedPrincipal();
            } else {
                principalToBeScheduled = loanApplicationTerms.getPrincipal();
            }
        } else {
            principalToBeScheduled = loanApplicationTerms.getPrincipal();
        }
        Money result = principalToBeScheduled.minus(loanApplicationTerms.getDownPaymentAmount());
        return result;
    }

    /**
     * Override to handle both existing loans and pre-creation schedule calculation.
     *
     * For existing loans: Only process actually disbursed tranches For pre-creation (calculateLoanSchedule API):
     * Process all expected tranches
     *
     * The parent method processes ALL tranches in disburseDetailMap (including undisbursed ones), which causes the
     * schedule to include future tranches in principal calculations for existing loans. This fix ensures: - Existing
     * loans: only actually disbursed tranches are added to outstanding balance - Pre-creation: all expected tranches
     * are included in the schedule preview
     */
    @Override
    protected void processDisbursements(final LoanApplicationTerms loanApplicationTerms, final BigDecimal chargesDueAtTimeOfDisbursement,
            LoanScheduleParams scheduleParams, final Collection<LoanScheduleModelPeriod> periods, final LocalDate scheduledDueDate,
            final BigDecimal totalOriginalPrincipal) {
        // Determine if this is a pre-creation scenario (no disbursed amounts but has expected tranches)
        Money totalDisbursed = loanApplicationTerms.getTotalDisbursedAmount();
        boolean isPreCreationScenario = totalDisbursed.isZero() && !loanApplicationTerms.getDisbursementDatas().isEmpty();

        for (Map.Entry<LocalDate, Money> disburseDetail : scheduleParams.getDisburseDetailMap().entrySet()) {
            LocalDate disbursementDate = disburseDetail.getKey();
            Money disbursementAmount = disburseDetail.getValue();

            // Check if all tranches on this date are actually disbursed
            // If multiple tranches share a date, they're summed in the map, so we need to check all of them
            List<DisbursementData> tranchesOnDate = loanApplicationTerms.getDisbursementDatas().stream()
                    .filter(data -> data.disbursementDate().equals(disbursementDate)).toList();

            // For pre-creation scenario, include all expected tranches
            // For existing loans, only include actually disbursed tranches
            boolean shouldProcess;
            if (isPreCreationScenario) {
                // Pre-creation: include all expected tranches
                shouldProcess = !tranchesOnDate.isEmpty();
            } else {
                // Existing loan: only include disbursed tranches
                boolean allDisbursed = !tranchesOnDate.isEmpty() && tranchesOnDate.stream().allMatch(DisbursementData::isDisbursed);
                shouldProcess = allDisbursed;
            }

            if (DateUtils.isAfter(disbursementDate, scheduleParams.getPeriodStartDate())
                    && !DateUtils.isAfter(disbursementDate, scheduledDueDate) && shouldProcess) {
                // validation check for amount not exceeds specified max
                if (loanApplicationTerms.getMaxOutstandingBalance() != null) {
                    Money maxOutstandingBalance = loanApplicationTerms.getMaxOutstandingBalanceMoney();
                    if (scheduleParams.getOutstandingBalance().plus(disbursementAmount).isGreaterThan(maxOutstandingBalance)) {
                        String errorMsg = "Outstanding balance must not exceed the amount: " + maxOutstandingBalance;
                        throw new MultiDisbursementOutstandingAmoutException(errorMsg, maxOutstandingBalance.getAmount(),
                                disbursementAmount.getAmount());
                    }
                }

                // creates and add disbursement detail to the repayments period
                // For disbursement charges on multi-tranche loans:
                // - Percentage-based charges: recalculate per tranche (percentage × trancheAmount)
                // - Flat charges: split proportionally (trancheAmount / totalAmount) × totalCharge
                final BigDecimal chargesDueAtTimeOfDisbursementForTranche = calculateDisbursementChargesForTranche(loanApplicationTerms,
                        disbursementAmount.getAmount(), chargesDueAtTimeOfDisbursement, totalOriginalPrincipal);
                final LoanScheduleModelDisbursementPeriod disbursementPeriod = LoanScheduleModelDisbursementPeriod
                        .disbursement(disbursementDate, disbursementAmount, chargesDueAtTimeOfDisbursementForTranche);
                periods.add(disbursementPeriod);

                BigDecimal downPaymentAmt = BigDecimal.ZERO;
                if (loanApplicationTerms.isDownPaymentEnabled()) {
                    // get list of disbursements on same date
                    // For pre-creation: include all expected tranches
                    // For existing loans: only include disbursed tranches
                    List<DisbursementData> disbursementsOnSameDate;
                    if (isPreCreationScenario) {
                        disbursementsOnSameDate = loanApplicationTerms.getDisbursementDatas().stream()
                                .filter(disbursementData -> DateUtils.isEqual(disbursementData.disbursementDate(), disbursementDate))
                                .toList();
                    } else {
                        disbursementsOnSameDate = loanApplicationTerms.getDisbursementDatas().stream()
                                .filter(disbursementData -> DateUtils.isEqual(disbursementData.disbursementDate(), disbursementDate)
                                        && disbursementData.isDisbursed())
                                .toList();
                    }

                    for (DisbursementData disbursementData : disbursementsOnSameDate) {
                        final LoanScheduleModelDownPaymentPeriod downPaymentPeriod = createDownPaymentPeriod(loanApplicationTerms,
                                scheduleParams, disbursementData.disbursementDate(), disbursementData.getPrincipal());
                        periods.add(downPaymentPeriod);
                        downPaymentAmt = downPaymentAmt.add(downPaymentPeriod.principalDue());
                    }
                }
                // updates actual outstanding balance with new disbursement detail
                Money remainingPrincipal = disbursementAmount.minus(downPaymentAmt);
                scheduleParams.addOutstandingBalance(remainingPrincipal);
                scheduleParams.addPrincipalToBeScheduled(remainingPrincipal);
                loanApplicationTerms.setPrincipal(loanApplicationTerms.getPrincipal().plus(remainingPrincipal));
            }
        }
    }

    /**
     * Calculate disbursement charges for a tranche, handling percentage-based and flat charges correctly.
     *
     * For multi-tranche loans with disbursement charges: - Percentage-based charges: recalculate per tranche
     * (percentage × trancheAmount) - Flat charges: split proportionally based on tranche size
     */
    private BigDecimal calculateDisbursementChargesForTranche(final LoanApplicationTerms loanApplicationTerms,
            final BigDecimal trancheAmount, final BigDecimal totalChargesDueAtTimeOfDisbursement, final BigDecimal totalOriginalPrincipal) {
        log.info("=== calculateDisbursementChargesForTranche called ===");
        log.info("TrancheAmount: {}, TotalChargesDueAtDisbursement: {}, TotalOriginalPrincipal: {}, MultiDisburse: {}", 
                trancheAmount, totalChargesDueAtTimeOfDisbursement, totalOriginalPrincipal, 
                loanApplicationTerms.isMultiDisburseLoan());
        
        if (totalChargesDueAtTimeOfDisbursement == null || totalChargesDueAtTimeOfDisbursement.compareTo(BigDecimal.ZERO) == 0
                || trancheAmount == null || trancheAmount.compareTo(BigDecimal.ZERO) == 0) {
            log.info("Early return: Zero charges or tranche amount");
            return BigDecimal.ZERO;
        }

        // If not a multi-tranche loan, use standard proportional calculation
        if (!loanApplicationTerms.isMultiDisburseLoan()) {
            log.info("Not a multi-tranche loan, using standard proportional calculation");
            return calculateChargesDueAtTimeOfDisbursementForTranche(totalOriginalPrincipal, trancheAmount,
                    totalChargesDueAtTimeOfDisbursement);
        }

        // Get loan charges from thread-local
        Set<LoanCharge> loanCharges = loanChargesThreadLocal.get();
        log.info("Thread-local loanCharges: {}", loanCharges != null ? (loanCharges.size() + " charges") : "NULL");
        if (loanCharges == null || loanCharges.isEmpty()) {
            log.warn("⚠️⚠️⚠️ Loan charges not available in thread-local for tranche: {}. Using fallback proportional calculation. "
                    + "This may indicate charges are not being passed correctly during loan creation. ⚠️⚠️⚠️", trancheAmount);
            // Fallback to proportional calculation if charges not available
            Money totalMultiDisbursed = loanApplicationTerms.getTotalMultiDisbursedAmount();
            BigDecimal principalForCalculation = totalMultiDisbursed.isGreaterThanZero() ? totalMultiDisbursed.getAmount()
                    : totalOriginalPrincipal;
            BigDecimal fallbackResult = calculateChargesDueAtTimeOfDisbursementForTranche(principalForCalculation, trancheAmount,
                    totalChargesDueAtTimeOfDisbursement);
            log.info("Fallback calculation result: {}", fallbackResult);
            return fallbackResult;
        }

        // Calculate charges per tranche based on charge type
        BigDecimal totalTrancheCharges = BigDecimal.ZERO;
        Money trancheAmountMoney = Money.of(loanApplicationTerms.getCurrency(), trancheAmount);
        
        log.info("Processing {} charges for tranche {}", loanCharges.size(), trancheAmount);

        for (LoanCharge loanCharge : loanCharges) {
            if (!loanCharge.isDueAtDisbursement()) {
                log.info("Skipping charge {} - not due at disbursement", loanCharge.getCharge().getName());
                continue;
            }

            log.info("Processing disbursement charge: ID={}, Name={}, Percentage={}, Amount={}", 
                    loanCharge.getId(), loanCharge.getCharge().getName(), loanCharge.getPercentage(), loanCharge.amount());
            
            BigDecimal chargeAmount = BigDecimal.ZERO;
            boolean isPercentageBased = loanCharge.getChargeCalculation().isPercentageBased() && loanCharge.getPercentage() != null;
            log.info("Charge type: PercentageBased={}, Percentage={}", isPercentageBased, loanCharge.getPercentage());

            // For percentage-based disbursement charges, recalculate per tranche
            if (isPercentageBased) {
                // Calculate: percentage × trancheAmount
                chargeAmount = trancheAmountMoney.getAmount().multiply(loanCharge.getPercentage()).divide(BigDecimal.valueOf(100), 6,
                        java.math.RoundingMode.HALF_UP);
                log.info("✓ Percentage-based charge calculated: {}% × {} = {}", loanCharge.getPercentage(), trancheAmount, chargeAmount);
            } else {
                // For flat charges, split proportionally
                // Use approved principal or total multi-disbursed amount as base
                Money sanctionedAmount = loanApplicationTerms.getApprovedPrincipal();
                if (sanctionedAmount == null || sanctionedAmount.isZero()) {
                    Money totalMultiDisbursed = loanApplicationTerms.getTotalMultiDisbursedAmount();
                    sanctionedAmount = totalMultiDisbursed.isGreaterThanZero() ? totalMultiDisbursed : loanApplicationTerms.getPrincipal();
                }

                MonetaryCurrency currency = MonetaryCurrency.fromCurrencyData(loanApplicationTerms.getCurrency());
                Money totalChargeAmount = loanCharge.getAmount(currency);
                if (sanctionedAmount.isGreaterThanZero() && totalChargeAmount.isGreaterThanZero()) {
                    // Calculate proportional fee: (trancheAmount / sanctionedAmount) * totalChargeAmount
                    chargeAmount = totalChargeAmount.getAmount().multiply(trancheAmountMoney.getAmount())
                            .divide(sanctionedAmount.getAmount(), 6, java.math.RoundingMode.HALF_UP);
                    log.info("✓ Flat charge calculated proportionally: ({} / {}) × {} = {}", 
                            trancheAmount, sanctionedAmount.getAmount(), totalChargeAmount.getAmount(), chargeAmount);
                }
            }

            // Add tax if applicable
            if (loanCharge.hasTax()) {
                MonetaryCurrency currency = MonetaryCurrency.fromCurrencyData(loanApplicationTerms.getCurrency());
                Money totalTaxAmount = loanCharge.getTaxAmount(currency);
                if (totalTaxAmount.isGreaterThanZero()) {
                    BigDecimal taxToAdd = BigDecimal.ZERO;
                    BigDecimal chargeAmountBeforeTax = chargeAmount;
                    // For percentage-based charges, calculate tax proportionally based on charge ratio
                    if (loanCharge.getChargeCalculation().isPercentageBased()) {
                        Money totalChargeAmount = loanCharge.getAmount(currency);
                        if (totalChargeAmount.isGreaterThanZero()) {
                            // Tax ratio = trancheChargeAmount / totalChargeAmount
                            BigDecimal taxRatio = chargeAmount.divide(totalChargeAmount.getAmount(), 6, java.math.RoundingMode.HALF_UP);
                            taxToAdd = totalTaxAmount.getAmount().multiply(taxRatio);
                            chargeAmount = chargeAmount.add(taxToAdd);
                        }
                    } else {
                        // For flat charges, split tax proportionally
                        Money sanctionedAmount = loanApplicationTerms.getApprovedPrincipal();
                        if (sanctionedAmount == null || sanctionedAmount.isZero()) {
                            Money totalMultiDisbursed = loanApplicationTerms.getTotalMultiDisbursedAmount();
                            sanctionedAmount = totalMultiDisbursed.isGreaterThanZero() ? totalMultiDisbursed
                                    : loanApplicationTerms.getPrincipal();
                        }
                        if (sanctionedAmount.isGreaterThanZero()) {
                            taxToAdd = totalTaxAmount.getAmount().multiply(trancheAmountMoney.getAmount())
                                    .divide(sanctionedAmount.getAmount(), 6, java.math.RoundingMode.HALF_UP);
                            chargeAmount = chargeAmount.add(taxToAdd);
                        }
                    }
                }
            }

            totalTrancheCharges = totalTrancheCharges.add(chargeAmount);
            log.info("Charge amount after tax: {}, Total tranche charges so far: {}", chargeAmount, totalTrancheCharges);
        }

        log.info("=== calculateDisbursementChargesForTranche result: {} ===", totalTrancheCharges);
        return totalTrancheCharges;
    }

    /**
     * Helper method to calculate charges due at time of disbursement for a tranche. Copied from parent class since it's
     * private.
     */
    private BigDecimal calculateChargesDueAtTimeOfDisbursementForTranche(final BigDecimal totalLoanPrincipal,
            final BigDecimal tranchePrincipal, final BigDecimal totalChargesDueAtTimeOfDisbursement) {
        if (totalChargesDueAtTimeOfDisbursement == null || totalLoanPrincipal == null || tranchePrincipal == null
                || totalLoanPrincipal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return tranchePrincipal.multiply(totalChargesDueAtTimeOfDisbursement).divide(totalLoanPrincipal, MoneyHelper.getMathContext());
    }

    /**
     * Override to handle first disbursement charges correctly for multi-tranche loans. For percentage-based
     * disbursement charges, recalculate per tranche instead of using proportional splitting.
     */
    @Override
    protected List<LoanScheduleModelPeriod> createNewLoanScheduleListWithDisbursementDetails(
            final LoanApplicationTerms loanApplicationTerms, final LoanScheduleParams loanScheduleParams,
            final BigDecimal chargesDueAtTimeOfDisbursement, final BigDecimal totalOriginalPrincipal) {
        List<LoanScheduleModelPeriod> periods = new ArrayList<>();
        if (!loanApplicationTerms.isMultiDisburseLoan()) {
            // For single disbursement loans, use parent implementation
            return super.createNewLoanScheduleListWithDisbursementDetails(loanApplicationTerms, loanScheduleParams,
                    chargesDueAtTimeOfDisbursement, totalOriginalPrincipal);
        }

        // For multi-tranche loans, handle first disbursement (on period start date)
        if (loanApplicationTerms.getDisbursementDatas().isEmpty()) {
            loanApplicationTerms.getDisbursementDatas()
                    .add(new DisbursementData(1L, loanApplicationTerms.getExpectedDisbursementDate(),
                            loanApplicationTerms.getExpectedDisbursementDate(), loanApplicationTerms.getPrincipal().getAmount(), null, null,
                            null, null, null));
        }

        for (DisbursementData disbursementData : loanApplicationTerms.getDisbursementDatas()) {
            if (disbursementData.disbursementDate().equals(loanScheduleParams.getPeriodStartDate())) {
                final Money principalDisbursed = Money.of(loanScheduleParams.getCurrency(), disbursementData.getPrincipal());
                // Use the same calculation method for first disbursement
                final BigDecimal chargesDueAtTimeOfDisbursementForTranche = calculateDisbursementChargesForTranche(loanApplicationTerms,
                        disbursementData.getPrincipal(), chargesDueAtTimeOfDisbursement, totalOriginalPrincipal);
                final LoanScheduleModelDisbursementPeriod disbursementPeriod = LoanScheduleModelDisbursementPeriod
                        .disbursement(disbursementData.disbursementDate(), principalDisbursed, chargesDueAtTimeOfDisbursementForTranche);
                periods.add(disbursementPeriod);

                if (loanApplicationTerms.isDownPaymentEnabled()) {
                    final LoanScheduleModelDownPaymentPeriod downPaymentPeriod = createDownPaymentPeriod(loanApplicationTerms,
                            loanScheduleParams, disbursementData.disbursementDate(), disbursementData.getPrincipal());
                    periods.add(downPaymentPeriod);
                }
            }
        }

        return periods;
    }

    @Override
    public PrincipalInterest calculatePrincipalInterestComponentsForPeriod(final PaymentPeriodsInOneYearCalculator calculator,
            final BigDecimal interestCalculationGraceOnRepaymentPeriodFraction, final Money totalCumulativePrincipal,
            final Money totalCumulativeInterest, final Money totalInterestDueForLoan, final Money cumulatingInterestPaymentDueToGrace,
            final Money outstandingBalance, final LoanApplicationTerms loanApplicationTerms, final int periodNumber, final MathContext mc,
            final TreeMap<LocalDate, Money> principalVariation, final Map<LocalDate, Money> compoundingMap, final LocalDate periodStartDate,
            final LocalDate periodEndDate, final Collection<LoanTermVariationsData> termVariations) {
        return super.calculatePrincipalInterestComponentsForPeriod(calculator, interestCalculationGraceOnRepaymentPeriodFraction,
                totalCumulativePrincipal, totalCumulativeInterest, totalInterestDueForLoan, cumulatingInterestPaymentDueToGrace,
                outstandingBalance, loanApplicationTerms, periodNumber, mc, principalVariation, compoundingMap, periodStartDate,
                periodEndDate, termVariations);
    }
}
