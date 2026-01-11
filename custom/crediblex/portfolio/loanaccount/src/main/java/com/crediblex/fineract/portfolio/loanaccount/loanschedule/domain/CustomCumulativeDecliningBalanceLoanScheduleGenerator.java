package com.crediblex.fineract.portfolio.loanaccount.loanschedule.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
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

    public CustomCumulativeDecliningBalanceLoanScheduleGenerator(ScheduledDateGenerator scheduledDateGenerator,
            PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator) {
        super(scheduledDateGenerator, paymentPeriodsInOneYearCalculator);
    }

    @Override
    public LoanScheduleModel generate(final MathContext mc, final LoanApplicationTerms loanApplicationTerms,
            final Set<LoanCharge> loanCharges, final HolidayDetailDTO holidayDetailDTO) {
        return super.generate(mc, loanApplicationTerms, loanCharges, holidayDetailDTO);
    }

    /**
     * Override to fix multi-tranche loan schedule calculation.
     *
     * For multi-disbursal loans, use only the total DISBURSED amount (not approved/all tranches) to calculate repayment
     * schedule. This ensures schedules reflect actual disbursed principal, not approved principal that hasn't been
     * disbursed yet.
     *
     * Fixes bug where schedule was calculated using getTotalMultiDisbursedAmount() (all tranches) instead of
     * getTotalDisbursedAmount() (only disbursed tranches).
     */
    @Override
    protected Money getPrincipalToBeScheduled(final LoanApplicationTerms loanApplicationTerms) {
        Money principalToBeScheduled;
        if (loanApplicationTerms.isMultiDisburseLoan()) {
            Money totalDisbursed = loanApplicationTerms.getTotalDisbursedAmount();

            if (totalDisbursed.isGreaterThanZero()) {
                // FIX: Use only disbursed amount, not all approved tranches
                principalToBeScheduled = totalDisbursed;
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
     * Override to exclude undisbursed tranches from outstanding balance calculation.
     *
     * The parent method processes ALL tranches in disburseDetailMap (including undisbursed ones), which causes the
     * schedule to include future tranches in principal calculations. This fix ensures only actually disbursed tranches
     * are added to outstanding balance.
     */
    @Override
    protected void processDisbursements(final LoanApplicationTerms loanApplicationTerms, final BigDecimal chargesDueAtTimeOfDisbursement,
            LoanScheduleParams scheduleParams, final Collection<LoanScheduleModelPeriod> periods, final LocalDate scheduledDueDate,
            final BigDecimal totalOriginalPrincipal) {
        for (Map.Entry<LocalDate, Money> disburseDetail : scheduleParams.getDisburseDetailMap().entrySet()) {
            // Check if all tranches on this date are actually disbursed
            // If multiple tranches share a date, they're summed in the map, so we need to check all of them
            List<DisbursementData> tranchesOnDate = loanApplicationTerms.getDisbursementDatas().stream()
                    .filter(data -> data.disbursementDate().equals(disburseDetail.getKey())).toList();

            // Only process if there are tranches on this date AND all of them are disbursed
            boolean allDisbursed = !tranchesOnDate.isEmpty() && tranchesOnDate.stream().allMatch(DisbursementData::isDisbursed);

            if (DateUtils.isAfter(disburseDetail.getKey(), scheduleParams.getPeriodStartDate())
                    && !DateUtils.isAfter(disburseDetail.getKey(), scheduledDueDate) && allDisbursed) {
                // validation check for amount not exceeds specified max
                if (loanApplicationTerms.getMaxOutstandingBalance() != null) {
                    Money maxOutstandingBalance = loanApplicationTerms.getMaxOutstandingBalanceMoney();
                    if (scheduleParams.getOutstandingBalance().plus(disburseDetail.getValue()).isGreaterThan(maxOutstandingBalance)) {
                        String errorMsg = "Outstanding balance must not exceed the amount: " + maxOutstandingBalance;
                        throw new MultiDisbursementOutstandingAmoutException(errorMsg, maxOutstandingBalance.getAmount(),
                                disburseDetail.getValue());
                    }
                }

                // creates and add disbursement detail to the repayments period
                final BigDecimal chargesDueAtTimeOfDisbursementForTranche = calculateChargesDueAtTimeOfDisbursementForTranche(
                        totalOriginalPrincipal, disburseDetail.getValue().getAmount(), chargesDueAtTimeOfDisbursement);
                final LoanScheduleModelDisbursementPeriod disbursementPeriod = LoanScheduleModelDisbursementPeriod
                        .disbursement(disburseDetail.getKey(), disburseDetail.getValue(), chargesDueAtTimeOfDisbursementForTranche);
                periods.add(disbursementPeriod);

                BigDecimal downPaymentAmt = BigDecimal.ZERO;
                if (loanApplicationTerms.isDownPaymentEnabled()) {
                    // get list of disbursements done on same date and create down payment periods
                    List<DisbursementData> disbursementsOnSameDate = loanApplicationTerms.getDisbursementDatas().stream()
                            .filter(disbursementData -> DateUtils.isEqual(disbursementData.disbursementDate(), disburseDetail.getKey())
                                    && disbursementData.isDisbursed())
                            .toList();
                    for (DisbursementData disbursementData : disbursementsOnSameDate) {
                        final LoanScheduleModelDownPaymentPeriod downPaymentPeriod = createDownPaymentPeriod(loanApplicationTerms,
                                scheduleParams, disbursementData.disbursementDate(), disbursementData.getPrincipal());
                        periods.add(downPaymentPeriod);
                        downPaymentAmt = downPaymentAmt.add(downPaymentPeriod.principalDue());
                    }
                }
                // updates actual outstanding balance with new disbursement detail (only for disbursed tranches)
                Money remainingPrincipal = disburseDetail.getValue().minus(downPaymentAmt);
                scheduleParams.addOutstandingBalance(remainingPrincipal);
                scheduleParams.addPrincipalToBeScheduled(remainingPrincipal);
                loanApplicationTerms.setPrincipal(loanApplicationTerms.getPrincipal().plus(remainingPrincipal));
            }
        }
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
