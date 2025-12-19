/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.loanschedule.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.workingdays.data.AdjustedDateDetailsDTO;
import org.apache.fineract.organisation.workingdays.domain.RepaymentRescheduleType;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementData;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.LoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleModelDownPaymentPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleParams;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.AbstractCumulativeLoanScheduleGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelDisbursementPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelRepaymentPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanTermVariationParams;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.PaymentPeriodsInOneYearCalculator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.PrincipalInterest;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.RecalculationDetail;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ScheduleCurrentPeriodParams;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.exception.MultiDisbursementEmiAmountException;
import org.apache.fineract.portfolio.loanaccount.loanschedule.exception.MultiDisbursementOutstandingAmoutException;
import org.apache.fineract.portfolio.loanaccount.loanschedule.exception.ScheduleDateException;
import org.apache.fineract.portfolio.loanproduct.domain.RepaymentStartDateType;
import org.apache.fineract.portfolio.tax.domain.TaxGroupMappings;
import org.apache.fineract.portfolio.tax.service.TaxUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FactorRateLoanScheduleGenerator extends AbstractCumulativeLoanScheduleGenerator {

    private final ScheduledDateGenerator scheduledDateGenerator;
    private final PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator;

    @Override
    public LoanScheduleModel generate(final MathContext mc, final LoanApplicationTerms loanApplicationTerms,
            final Set<LoanCharge> loanCharges, final HolidayDetailDTO holidayDetailDTO) {
        return generate(mc, loanApplicationTerms, loanCharges, holidayDetailDTO, null);
    }

    private LoanScheduleModel generate(final MathContext mc, final LoanApplicationTerms loanApplicationTerms,
            final Set<LoanCharge> loanCharges, final HolidayDetailDTO holidayDetailDTO, final LoanScheduleParams loanScheduleParams) {

        final BigDecimal totalOriginalPrincipal = loanApplicationTerms.getPrincipal().getAmount();
        // Adjust principal based on factor rate
        final BigDecimal factorRate = loanApplicationTerms.getFactorRate();
        final Money principalMoney = loanApplicationTerms.getPrincipal();
        final LoanCharge factorRateInstallmentFee = loanCharges.stream().filter(LoanCharge::isInstalmentFee).findFirst().orElse(null);

        Money factorRateTaxAmount = Money.zero(loanApplicationTerms.getCurrency());
        Money factorRateFeeAmount = Money.zero(loanApplicationTerms.getCurrency());
        if (factorRateInstallmentFee != null && factorRateInstallmentFee.isActive()) {
            final LocalDate chargeDate = factorRateInstallmentFee.getDueDate() != null ? factorRateInstallmentFee.getDueDate()
                    : DateUtils.getBusinessLocalDate();
            final Set<TaxGroupMappings> taxGroupMappings = factorRateInstallmentFee.getCharge().getTaxGroup() != null
                    ? factorRateInstallmentFee.getCharge().getTaxGroup().getTaxGroupMappings()
                    : Collections.emptySet();
            final BigDecimal taxPercentage = TaxUtils.determineTaxPercentageValue(chargeDate, taxGroupMappings);
            factorRateFeeAmount = determineFactorRateFee(factorRate, principalMoney, taxPercentage, mc);
            factorRateTaxAmount = factorRateFeeAmount.multipliedBy(taxPercentage).dividedBy(100, mc);
        }

        final Money factorRateFeePerInstallment = factorRateFeeAmount.dividedBy(loanApplicationTerms.getNumberOfRepayments(), mc);
        final Money factorRateTaxPerInstallment = factorRateTaxAmount.dividedBy(loanApplicationTerms.getNumberOfRepayments(), mc);

        // generate list of proposed schedule due dates
        LocalDate loanEndDate = getScheduledDateGenerator().getLastRepaymentDate(loanApplicationTerms, holidayDetailDTO);
        LoanTermVariationsData lastDueDateVariation = loanApplicationTerms.getLoanTermVariations()
                .fetchLoanTermDueDateVariationsData(loanEndDate);
        if (lastDueDateVariation != null) {
            loanEndDate = lastDueDateVariation.getDateValue();
        }
        loanApplicationTerms.updateLoanEndDate(loanEndDate);

        // determine the total charges due at time of disbursement
        final BigDecimal chargesDueAtTimeOfDisbursement = deriveTotalChargesDueAtTimeOfDisbursement(loanCharges);

        // setup variables for tracking important facts required for loan
        // schedule generation.

        final CurrencyData currency = loanApplicationTerms.getCurrency();
        final MonetaryCurrency monetaryCurrency = MonetaryCurrency.fromCurrencyData(currency);
        LoanScheduleParams scheduleParams;
        LocalDate periodStartDate = RepaymentStartDateType.DISBURSEMENT_DATE.equals(loanApplicationTerms.getRepaymentStartDateType())
                ? loanApplicationTerms.getExpectedDisbursementDate()
                : loanApplicationTerms.getSubmittedOnDate();
        if (loanScheduleParams == null) {
            scheduleParams = LoanScheduleParams.createLoanScheduleParams(currency, Money.of(currency, chargesDueAtTimeOfDisbursement),
                    periodStartDate, getPrincipalToBeScheduled(loanApplicationTerms), mc);
        } else if (!loanScheduleParams.isPartialUpdate()) {
            scheduleParams = LoanScheduleParams.createLoanScheduleParams(currency, Money.of(currency, chargesDueAtTimeOfDisbursement),
                    periodStartDate, getPrincipalToBeScheduled(loanApplicationTerms), loanScheduleParams, mc);
        } else {
            scheduleParams = loanScheduleParams;
        }

        final Collection<RecalculationDetail> transactions = scheduleParams.getRecalculationDetails();
        final LoanRepaymentScheduleTransactionProcessor loanRepaymentScheduleTransactionProcessor = scheduleParams
                .getLoanRepaymentScheduleTransactionProcessor();

        List<LoanScheduleModelPeriod> periods = new ArrayList<>();
        if (!scheduleParams.isPartialUpdate()) {
            periods = createNewLoanScheduleListWithDisbursementDetails(loanApplicationTerms, scheduleParams, chargesDueAtTimeOfDisbursement,
                    totalOriginalPrincipal);
        }

        // Determine the total interest owed over the full loan for FLAT
        // interest method .
        if (!scheduleParams.isPartialUpdate() && !loanApplicationTerms.isEqualAmortization()) {
            Money totalInterestChargedForFullLoanTerm = loanApplicationTerms
                    .calculateTotalInterestCharged(getPaymentPeriodsInOneYearCalculator(), mc);

            loanApplicationTerms.updateTotalInterestDue(totalInterestChargedForFullLoanTerm);

        }

        boolean isFirstRepayment = true;
        LocalDate lastRepaymentDate = RepaymentStartDateType.DISBURSEMENT_DATE.equals(loanApplicationTerms.getRepaymentStartDateType())
                ? loanApplicationTerms.getExpectedDisbursementDate()
                : loanApplicationTerms.getSubmittedOnDate();
        LocalDate firstRepaymentDate = getScheduledDateGenerator().generateNextRepaymentDate(lastRepaymentDate, loanApplicationTerms,
                isFirstRepayment);
        final LocalDate idealDisbursementDate = getScheduledDateGenerator().idealDisbursementDateBasedOnFirstRepaymentDate(
                loanApplicationTerms.getLoanTermPeriodFrequencyType(), loanApplicationTerms.getRepaymentEvery(), firstRepaymentDate,
                loanApplicationTerms.getLoanCalendar(), loanApplicationTerms.getHolidayDetailDTO(), loanApplicationTerms);

        if (!scheduleParams.isPartialUpdate()) {
            Money calculatedAmortizableAmount = loanApplicationTerms.getPrincipal().minus(loanApplicationTerms.getDownPaymentAmount());
            // Set Fixed Principal Amount
            updateAmortization(mc, loanApplicationTerms, scheduleParams.getPeriodNumber(), calculatedAmortizableAmount);

            if (loanApplicationTerms.isMultiDisburseLoan()) {
                /* fetches the first tranche amount and also updates other tranche details to map */
                Money disburseAmt = Money.of(currency, getDisbursementAmount(loanApplicationTerms, scheduleParams.getPeriodStartDate(),
                        scheduleParams.getDisburseDetailMap(), scheduleParams.applyInterestRecalculation()));
                Money downPaymentAmt = Money.zero(currency);
                if (loanApplicationTerms.isDownPaymentEnabled()) {
                    downPaymentAmt = Money.of(currency, MathUtil.percentageOf(disburseAmt.getAmount(),
                            loanApplicationTerms.getDisbursedAmountPercentageForDownPayment(), 19));
                    if (loanApplicationTerms.getInstallmentAmountInMultiplesOf() != null) {
                        downPaymentAmt = Money.roundToMultiplesOf(downPaymentAmt, loanApplicationTerms.getInstallmentAmountInMultiplesOf());
                    }
                }
                Money remainingPrincipalAmt = disburseAmt.minus(downPaymentAmt);
                scheduleParams.setPrincipalToBeScheduled(remainingPrincipalAmt);
                scheduleParams.setOutstandingBalance(remainingPrincipalAmt);
                scheduleParams.setOutstandingBalanceAsPerRest(remainingPrincipalAmt);
                loanApplicationTerms.setPrincipal(remainingPrincipalAmt);
            } else if (loanApplicationTerms.isDownPaymentEnabled()) {
                Money downPaymentAmt = Money.of(currency, MathUtil.percentageOf(loanApplicationTerms.getPrincipal().getAmount(),
                        loanApplicationTerms.getDisbursedAmountPercentageForDownPayment(), 19));
                if (loanApplicationTerms.getInstallmentAmountInMultiplesOf() != null) {
                    downPaymentAmt = Money.roundToMultiplesOf(downPaymentAmt, loanApplicationTerms.getInstallmentAmountInMultiplesOf());
                }
                final Money remainingPrincipalAmt = loanApplicationTerms.getPrincipal().minus(downPaymentAmt);
                loanApplicationTerms.setPrincipal(remainingPrincipalAmt);
            }
        }

        // charges which depends on total loan interest will be added to this
        // set and handled separately after all installments generated
        final Set<LoanCharge> nonCompoundingCharges = separateTotalCompoundingPercentageCharges(loanCharges);

        LocalDate currentDate = DateUtils.getBusinessLocalDate();
        LocalDate lastRestDate = currentDate;
        if (loanApplicationTerms.getRestCalendarInstance() != null) {
            lastRestDate = getNextRestScheduleDate(currentDate.minusDays(1), loanApplicationTerms, holidayDetailDTO);
        }

        boolean isNextRepaymentAvailable = true;
        boolean extendTermForDailyRepayments = false;

        if (holidayDetailDTO.getWorkingDays().getExtendTermForDailyRepayments()
                && loanApplicationTerms.getRepaymentPeriodFrequencyType() == PeriodFrequencyType.DAYS
                && loanApplicationTerms.getRepaymentEvery() == 1) {
            holidayDetailDTO.getWorkingDays().setRepaymentReschedulingType(RepaymentRescheduleType.MOVE_TO_NEXT_WORKING_DAY.getValue());
            extendTermForDailyRepayments = true;
        }

        final Collection<LoanTermVariationsData> interestRates = loanApplicationTerms.getLoanTermVariations().getInterestRateChanges();
        final Collection<LoanTermVariationsData> interestRatesForInstallments = loanApplicationTerms.getLoanTermVariations()
                .getInterestRateFromInstallment();

        // this block is to start the schedule generation from specified date
        if (scheduleParams.isPartialUpdate()) {
            if (loanApplicationTerms.isMultiDisburseLoan()) {
                loanApplicationTerms.setPrincipal(scheduleParams.getPrincipalToBeScheduled());
            }

            applyLoanVariationsForPartialScheduleGenerate(loanApplicationTerms, scheduleParams, interestRates,
                    interestRatesForInstallments);
            if (!DateUtils.isAfter(firstRepaymentDate, scheduleParams.getActualRepaymentDate())) {
                isFirstRepayment = false;
            }
        }

        while (!scheduleParams.getOutstandingBalance().isZero() || !scheduleParams.getDisburseDetailMap().isEmpty()) {
            LocalDate previousRepaymentDate = scheduleParams.getActualRepaymentDate();
            final LocalDate actualRepaymentDate = getScheduledDateGenerator()
                    .generateNextRepaymentDate(scheduleParams.getActualRepaymentDate(), loanApplicationTerms, isFirstRepayment);
            scheduleParams.setActualRepaymentDate(actualRepaymentDate);
            AdjustedDateDetailsDTO adjustedDateDetailsDTO = getScheduledDateGenerator()
                    .adjustRepaymentDate(scheduleParams.getActualRepaymentDate(), loanApplicationTerms, holidayDetailDTO);
            scheduleParams.setActualRepaymentDate(adjustedDateDetailsDTO.getChangedActualRepaymentDate());
            isFirstRepayment = false;
            LocalDate scheduledDueDate = adjustedDateDetailsDTO.getChangedScheduleDate();

            // calculated interest start date for the period
            LocalDate periodStartDateApplicableForInterest = calculateInterestStartDateForPeriod(loanApplicationTerms,
                    scheduleParams.getPeriodStartDate(), idealDisbursementDate, firstRepaymentDate,
                    loanApplicationTerms.isInterestChargedFromDateSameAsDisbursalDateEnabled(),
                    loanApplicationTerms.getExpectedDisbursementDate());

            // Loan Schedule Exceptions that need to be applied for Loan Account
            LoanTermVariationParams termVariationParams = applyLoanTermVariations(loanApplicationTerms, scheduleParams,
                    previousRepaymentDate, scheduledDueDate, interestRatesForInstallments, getPaymentPeriodsInOneYearCalculator(), mc);

            scheduledDueDate = termVariationParams.scheduledDueDate();
            if (!loanApplicationTerms.isFirstRepaymentDateAllowedOnHoliday()) {
                AdjustedDateDetailsDTO adjustedDateDetailsDTO1 = getScheduledDateGenerator().adjustRepaymentDate(scheduledDueDate,
                        loanApplicationTerms, holidayDetailDTO);
                scheduledDueDate = adjustedDateDetailsDTO1.getChangedScheduleDate();
            }

            // Updates total days in term
            scheduleParams.addLoanTermInDays(DateUtils.getExactDifferenceInDays(scheduleParams.getPeriodStartDate(), scheduledDueDate));
            if (termVariationParams.skipPeriod()) {
                continue;
            }

            if (DateUtils.isAfter(scheduleParams.getPeriodStartDate(), scheduledDueDate)) {
                throw new ScheduleDateException("Due date can't be before period start date", scheduledDueDate);
            }

            if (extendTermForDailyRepayments) {
                scheduleParams.setActualRepaymentDate(scheduledDueDate);
            }

            // this block is to generate the schedule till the specified
            // date(used for calculating preclosure)
            boolean isCompletePeriod = true;
            if (scheduleParams.getScheduleTillDate() != null
                    && !DateUtils.isBefore(scheduledDueDate, scheduleParams.getScheduleTillDate())) {
                if (!DateUtils.isEqual(scheduledDueDate, scheduleParams.getScheduleTillDate())) {
                    isCompletePeriod = false;
                }
                scheduledDueDate = scheduleParams.getScheduleTillDate();
                isNextRepaymentAvailable = false;
            }

            if (loanApplicationTerms.isInterestBearingAndInterestRecalculationEnabled()) {
                populateCompoundingDatesInPeriod(scheduleParams.getPeriodStartDate(), scheduledDueDate, loanApplicationTerms,
                        holidayDetailDTO, scheduleParams, loanCharges, monetaryCurrency, mc);
            }

            // populates the collection with transactions till the due date of
            // the period for interest recalculation enabled loans
            Collection<RecalculationDetail> applicableTransactions = getApplicableTransactionsForPeriod(
                    scheduleParams.applyInterestRecalculation(), scheduledDueDate, transactions);

            final BigDecimal interestCalculationGraceOnRepaymentPeriodFraction = getPaymentPeriodsInOneYearCalculator()
                    .calculatePortionOfRepaymentPeriodInterestChargingGrace(periodStartDateApplicableForInterest, scheduledDueDate,
                            loanApplicationTerms.getInterestChargedFromLocalDate(), loanApplicationTerms.getLoanTermPeriodFrequencyType(),
                            loanApplicationTerms.getRepaymentEvery(), mc);
            ScheduleCurrentPeriodParams currentPeriodParams = new ScheduleCurrentPeriodParams(currency,
                    interestCalculationGraceOnRepaymentPeriodFraction);

            if (loanApplicationTerms.isMultiDisburseLoan()) {
                processDisbursements(loanApplicationTerms, chargesDueAtTimeOfDisbursement, scheduleParams, periods, scheduledDueDate,
                        totalOriginalPrincipal);
            }

            // process repayments to the schedule as per the repayment
            // transaction processor configuration
            // will add a new schedule with interest till the transaction date
            // for a loan repayment which falls between the
            // two periods for interest first repayment strategies
            handleRecalculationForNonDueDateTransactions(mc, loanApplicationTerms, loanCharges, holidayDetailDTO, scheduleParams, periods,
                    loanApplicationTerms.getTotalInterestDue(), idealDisbursementDate, firstRepaymentDate, lastRestDate, scheduledDueDate,
                    periodStartDateApplicableForInterest, applicableTransactions, currentPeriodParams);

            if (currentPeriodParams.isSkipCurrentLoop()) {
                continue;
            }
            periodStartDateApplicableForInterest = calculateInterestStartDateForPeriod(loanApplicationTerms,
                    scheduleParams.getPeriodStartDate(), idealDisbursementDate, firstRepaymentDate,
                    loanApplicationTerms.isInterestChargedFromDateSameAsDisbursalDateEnabled(),
                    loanApplicationTerms.getExpectedDisbursementDate());

            // backup for pre-close transaction
            updateCompoundingDetails(scheduleParams, periodStartDateApplicableForInterest);

            // 5 determine principal,interest of repayment period
            PrincipalInterest principalInterestForThisPeriod = calculatePrincipalInterestComponentsForPeriod(
                    getPaymentPeriodsInOneYearCalculator(), currentPeriodParams.getInterestCalculationGraceOnRepaymentPeriodFraction(),
                    scheduleParams.getTotalCumulativePrincipal().minus(scheduleParams.getReducePrincipal()),
                    scheduleParams.getTotalCumulativeInterest(), loanApplicationTerms.getTotalInterestDue(),
                    scheduleParams.getTotalOutstandingInterestPaymentDueToGrace(), scheduleParams.getOutstandingBalanceAsPerRest(),
                    loanApplicationTerms, scheduleParams.getPeriodNumber(), mc, mergeVariationsToMap(loanApplicationTerms, scheduleParams),
                    scheduleParams.getCompoundingMap(), periodStartDateApplicableForInterest, scheduledDueDate, interestRates);

            // will check for EMI amount greater than interest calculated
            if (loanApplicationTerms.getFixedEmiAmount() != null
                    && loanApplicationTerms.getFixedEmiAmount().compareTo(principalInterestForThisPeriod.interest().getAmount()) < 0) {
                String errorMsg = "EMI amount must be greater than : " + principalInterestForThisPeriod.interest().getAmount();
                throw new MultiDisbursementEmiAmountException(errorMsg, principalInterestForThisPeriod.interest().getAmount(),
                        loanApplicationTerms.getFixedEmiAmount());
            }

            // update cumulative fields for principal & interest
            currentPeriodParams.setInterestForThisPeriod(principalInterestForThisPeriod.interest());
            Money lastTotalOutstandingInterestPaymentDueToGrace = scheduleParams.getTotalOutstandingInterestPaymentDueToGrace();
            scheduleParams.setTotalOutstandingInterestPaymentDueToGrace(principalInterestForThisPeriod.interestPaymentDueToGrace());
            currentPeriodParams.setPrincipalForThisPeriod(principalInterestForThisPeriod.principal());

            // applies early payments on principal portion
            updatePrincipalPortionBasedOnPreviousEarlyPayments(currency, scheduleParams, currentPeriodParams);

            // updates amounts with current earlyPaidAmount
            updateAmountsBasedOnCurrentEarlyPayments(mc, loanApplicationTerms, scheduleParams, currentPeriodParams);

            if (scheduleParams.getOutstandingBalance().isLessThanZero() || !isNextRepaymentAvailable) {
                currentPeriodParams.plusPrincipalForThisPeriod(scheduleParams.getOutstandingBalance());
                scheduleParams.setOutstandingBalance(Money.zero(currency));
            }

            if (!isNextRepaymentAvailable) {
                scheduleParams.getDisburseDetailMap().clear();
            }

            // applies charges for the period
            applyChargesForCurrentPeriod(loanCharges, monetaryCurrency, scheduleParams, scheduledDueDate, currentPeriodParams,
                    factorRateFeePerInstallment, factorRateTaxPerInstallment, mc);

            // sum up real totalInstallmentDue from components
            final Money totalInstallmentDue = currentPeriodParams.fetchTotalAmountForPeriod();

            // if previous installment is last then add interest to same
            // installment
            if (currentPeriodParams.getLastInstallment() != null && currentPeriodParams.getPrincipalForThisPeriod().isZero()) {
                currentPeriodParams.getLastInstallment().addInterestAmount(currentPeriodParams.getInterestForThisPeriod());
                continue;
            }

            // create repayment period from parts
            LoanScheduleModelPeriod installment = LoanScheduleModelRepaymentPeriod.repayment(scheduleParams.getInstalmentNumber(),
                    scheduleParams.getPeriodStartDate(), scheduledDueDate, currentPeriodParams.getPrincipalForThisPeriod(),
                    scheduleParams.getOutstandingBalance(), currentPeriodParams.getInterestForThisPeriod(),
                    currentPeriodParams.getFeeChargesForInstallment(), currentPeriodParams.getTaxChargesForInstallment(),
                    currentPeriodParams.getPenaltyChargesForInstallment(), totalInstallmentDue, !isCompletePeriod, mc);
            if (principalInterestForThisPeriod.getRescheduleInterestPortion() != null) {
                installment.setRescheduleInterestPortion(principalInterestForThisPeriod.getRescheduleInterestPortion().getAmount());
            }
            addLoanRepaymentScheduleInstallment(scheduleParams.getInstallments(), installment);
            // apply loan transactions on installments to identify early/late
            // payments for interest recalculation
            installment = handleRecalculationForTransactions(mc, loanApplicationTerms, holidayDetailDTO, monetaryCurrency, scheduleParams,
                    loanRepaymentScheduleTransactionProcessor, loanApplicationTerms.getTotalInterestDue(), lastRestDate, scheduledDueDate,
                    periodStartDateApplicableForInterest, applicableTransactions, currentPeriodParams,
                    lastTotalOutstandingInterestPaymentDueToGrace, installment, loanCharges);

            if (loanApplicationTerms.getCurrentPeriodFixedEmiAmount() != null) {
                installment.setEMIFixedSpecificToInstallmentTrue();
            }

            periods.add(installment);

            // Updates principal paid map with efective date for reducing
            // the amount from outstanding balance(interest calculation)
            updateAmountsWithEffectiveDate(loanApplicationTerms, holidayDetailDTO, scheduleParams, scheduledDueDate, currentPeriodParams,
                    installment, lastRestDate);

            // handle cumulative fields

            scheduleParams.addTotalCumulativePrincipal(currentPeriodParams.getPrincipalForThisPeriod());
            scheduleParams.addTotalRepaymentExpected(totalInstallmentDue);
            scheduleParams.addTotalCumulativeInterest(currentPeriodParams.getInterestForThisPeriod());
            scheduleParams.setPeriodStartDate(scheduledDueDate);
            scheduleParams.incrementInstalmentNumber();
            scheduleParams.incrementPeriodNumber();
            if (termVariationParams.recalculateAmounts()) {
                loanApplicationTerms.setCurrentPeriodFixedEmiAmount(null);
                loanApplicationTerms.setCurrentPeriodFixedPrincipalAmount(null);
                adjustInstallmentOrPrincipalAmount(loanApplicationTerms, scheduleParams.getTotalCumulativePrincipal(),
                        scheduleParams.getPeriodNumber(), mc);
            }
        }

        // this condition is to add the interest from grace period if not
        // already applied.
        if (scheduleParams.getTotalOutstandingInterestPaymentDueToGrace().isGreaterThanZero()) {
            LoanScheduleModelPeriod installment = periods.getLast();
            installment.addInterestAmount(scheduleParams.getTotalOutstandingInterestPaymentDueToGrace());
            scheduleParams.addTotalRepaymentExpected(scheduleParams.getTotalOutstandingInterestPaymentDueToGrace());
            scheduleParams.addTotalCumulativeInterest(scheduleParams.getTotalOutstandingInterestPaymentDueToGrace());
            scheduleParams.setTotalOutstandingInterestPaymentDueToGrace(Money.zero(currency));
        }

        // determine fees and penalties for charges which depends on total
        // loan interest
        updatePeriodsWithCharges(monetaryCurrency, scheduleParams, periods, nonCompoundingCharges, mc);

        // this block is to add extra re-payment schedules with interest portion
        // if the loan not paid with in loan term

        if (scheduleParams.getScheduleTillDate() != null) {
            currentDate = scheduleParams.getScheduleTillDate();
        }
        if (scheduleParams.applyInterestRecalculation() && !scheduleParams.getLatePaymentMap().isEmpty()
                && DateUtils.isAfter(currentDate, scheduleParams.getPeriodStartDate())) {
            Money totalInterest = addInterestOnlyRepaymentScheduleForCurrentDate(mc, loanApplicationTerms, holidayDetailDTO,
                    monetaryCurrency, periods, currentDate, loanRepaymentScheduleTransactionProcessor, transactions, loanCharges,
                    scheduleParams);
            scheduleParams.addTotalCumulativeInterest(totalInterest);
        }

        loanApplicationTerms.resetFixedEmiAmount();
        final BigDecimal totalPrincipalPaid = BigDecimal.ZERO;
        final BigDecimal totalOutstanding = BigDecimal.ZERO;

        updateCompoundingDetails(periods, scheduleParams, loanApplicationTerms);
        final Money totalTaxChargesCharged = scheduleParams.getTotalTaxChargesCharged() != null ? scheduleParams.getTotalTaxChargesCharged()
                : Money.zero(currency);
        return LoanScheduleModel.from(periods, currency, scheduleParams.getLoanTermInDays(),
                scheduleParams.getPrincipalToBeScheduled().plus(loanApplicationTerms.getDownPaymentAmount()),
                scheduleParams.getTotalCumulativePrincipal().plus(loanApplicationTerms.getDownPaymentAmount()).getAmount(),
                totalPrincipalPaid, scheduleParams.getTotalCumulativeInterest().getAmount(),
                scheduleParams.getTotalFeeChargesCharged().getAmount(), totalTaxChargesCharged.getAmount(),
                scheduleParams.getTotalPenaltyChargesCharged().getAmount(), scheduleParams.getTotalRepaymentExpected().getAmount(),
                totalOutstanding);
    }

    private Money determineFactorRateFee(final BigDecimal factorRate, final Money principal, final BigDecimal taxPercentage,
            final MathContext mc) {
        if (!MathUtil.isGreaterThan(factorRate, BigDecimal.ONE)) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.factor.rate.must.be.greater.than.or.equal.to.one",
                    "Factor rate must be greater than 1");
        }
        final BigDecimal hundred = new BigDecimal("100");
        final BigDecimal twoHundred = new BigDecimal("200");
        final BigDecimal numerator = hundred.multiply(principal.getAmount(), mc).multiply(factorRate.subtract(BigDecimal.ONE, mc), mc);
        final BigDecimal denominator = twoHundred.add(taxPercentage, mc).subtract(factorRate.multiply(hundred.add(taxPercentage, mc), mc),
                mc);
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.factor.rate.invalid.denominator",
                    "The calculation for factor rate fee results in a division by zero due to invalid denominator.");
        }
        final BigDecimal result = numerator.divide(denominator, mc);
        return Money.of(principal.getCurrency(), result);
    }

    @Override
    public ScheduledDateGenerator getScheduledDateGenerator() {
        return this.scheduledDateGenerator;
    }

    @Override
    public PaymentPeriodsInOneYearCalculator getPaymentPeriodsInOneYearCalculator() {
        return this.paymentPeriodsInOneYearCalculator;
    }

    @Override
    public PrincipalInterest calculatePrincipalInterestComponentsForPeriod(final PaymentPeriodsInOneYearCalculator calculator,
            final BigDecimal interestCalculationGraceOnRepaymentPeriodFraction, final Money totalCumulativePrincipal,
            @SuppressWarnings("unused") final Money totalCumulativeInterest,
            @SuppressWarnings("unused") final Money totalInterestDueForLoan, final Money cumulatingInterestPaymentDueToGrace,
            final Money outstandingBalance, final LoanApplicationTerms loanApplicationTerms, final int periodNumber, final MathContext mc,
            final TreeMap<LocalDate, Money> principalVariation, final Map<LocalDate, Money> compoundingMap, final LocalDate periodStartDate,
            final LocalDate periodEndDate, final Collection<LoanTermVariationsData> termVariations) {

        LocalDate interestStartDate = periodStartDate;
        Money interestForThisInstallment = totalCumulativePrincipal.zero();
        Money compoundedInterest = totalCumulativePrincipal.zero();
        Money balanceForInterestCalculation = outstandingBalance;
        Money cumulatingInterestDueToGrace = cumulatingInterestPaymentDueToGrace;
        Map<LocalDate, BigDecimal> interestRates = new HashMap<>(termVariations.size());

        for (LoanTermVariationsData loanTermVariation : termVariations) {
            if (loanTermVariation.getTermVariationType().isInterestRateVariation()
                    && loanTermVariation.isApplicable(periodStartDate, periodEndDate)) {
                LocalDate fromDate = loanTermVariation.getTermVariationApplicableFrom();
                if (fromDate == null) {
                    fromDate = periodStartDate;
                }
                interestRates.put(fromDate, loanTermVariation.getDecimalValue());
                if (!principalVariation.containsKey(fromDate)) {
                    principalVariation.put(fromDate, balanceForInterestCalculation.zero());
                }
            }
        }

        if (principalVariation != null) {

            for (Map.Entry<LocalDate, Money> principal : principalVariation.entrySet()) {

                if (!DateUtils.isAfter(principal.getKey(), periodEndDate)) {
                    int interestForDays = DateUtils.getExactDifferenceInDays(interestStartDate, principal.getKey());
                    if (interestForDays > 0) {
                        final PrincipalInterest result = loanApplicationTerms.calculateTotalInterestForPeriod(calculator,
                                interestCalculationGraceOnRepaymentPeriodFraction, periodNumber, mc, cumulatingInterestDueToGrace,
                                balanceForInterestCalculation, interestStartDate, principal.getKey());
                        interestForThisInstallment = interestForThisInstallment.plus(result.interest());
                        cumulatingInterestDueToGrace = result.interestPaymentDueToGrace();
                        interestStartDate = principal.getKey();

                    }
                    Money compoundFee = totalCumulativePrincipal.zero();
                    if (compoundingMap.containsKey(principal.getKey())) {
                        Money interestToBeCompounded = totalCumulativePrincipal.zero();
                        // for interest compounding
                        if (loanApplicationTerms.getInterestRecalculationCompoundingMethod().isInterestCompoundingEnabled()) {
                            interestToBeCompounded = interestForThisInstallment.minus(compoundedInterest);
                            balanceForInterestCalculation = balanceForInterestCalculation.plus(interestToBeCompounded);
                            compoundedInterest = interestForThisInstallment;
                        }
                        // fee compounding will be done after calculation
                        compoundFee = compoundingMap.get(principal.getKey());
                        compoundingMap.put(principal.getKey(), interestToBeCompounded.plus(compoundFee));
                    }
                    if (!loanApplicationTerms.isPrincipalCompoundingDisabledForOverdueLoans()) {
                        balanceForInterestCalculation = balanceForInterestCalculation.plus(principal.getValue());
                    }
                    balanceForInterestCalculation = balanceForInterestCalculation.plus(compoundFee);

                    if (interestRates.containsKey(principal.getKey())) {
                        loanApplicationTerms.updateAnnualNominalInterestRate(interestRates.get(principal.getKey()));
                    }
                }
            }
        }

        final PrincipalInterest result = loanApplicationTerms.calculateTotalInterestForPeriod(calculator,
                interestCalculationGraceOnRepaymentPeriodFraction, periodNumber, mc, cumulatingInterestDueToGrace,
                balanceForInterestCalculation, interestStartDate, periodEndDate);

        interestForThisInstallment = interestForThisInstallment.plus(result.interest());
        cumulatingInterestDueToGrace = result.interestPaymentDueToGrace();

        if (loanApplicationTerms.isInterestToBeRecoveredFirstWhenGreaterThanEMIEnabled()
                && loanApplicationTerms.isInterestTobeApproppriated()) {
            interestForThisInstallment = interestForThisInstallment.add(loanApplicationTerms.getInterestTobeApproppriated());
            loanApplicationTerms.setInterestTobeApproppriated(interestForThisInstallment.zero());
        }

        Money interestForPeriod = interestForThisInstallment;
        if (interestForPeriod.isGreaterThanZero()) {
            interestForPeriod = interestForPeriod.minus(cumulatingInterestPaymentDueToGrace);
        } else {
            interestForPeriod = cumulatingInterestDueToGrace.minus(cumulatingInterestPaymentDueToGrace);
        }

        Money principalForThisInstallment = loanApplicationTerms.calculateTotalPrincipalForPeriod(calculator, outstandingBalance,
                periodNumber, mc, interestForPeriod);
        if (loanApplicationTerms.isInterestToBeRecoveredFirstWhenGreaterThanEMIEnabled() && principalForThisInstallment.isLessThanZero()
                && !loanApplicationTerms.isLastRepaymentPeriod(periodNumber)) {
            loanApplicationTerms.setInterestTobeApproppriated(principalForThisInstallment.abs());
            interestForThisInstallment = interestForThisInstallment.minus(loanApplicationTerms.getInterestTobeApproppriated());
            principalForThisInstallment = principalForThisInstallment.zero();
        }

        // update cumulative fields for principal & interest
        final Money interestBroughtFowardDueToGrace = cumulatingInterestDueToGrace;
        final Money totalCumulativePrincipalToDate = totalCumulativePrincipal.plus(principalForThisInstallment);

        // adjust if needed
        principalForThisInstallment = loanApplicationTerms.adjustPrincipalIfLastRepaymentPeriod(principalForThisInstallment,
                totalCumulativePrincipalToDate, periodNumber);

        PrincipalInterest principalInterest = new PrincipalInterest(principalForThisInstallment, interestForThisInstallment,
                interestBroughtFowardDueToGrace);
        principalInterest.setRescheduleInterestPortion(loanApplicationTerms.getInterestTobeApproppriated());
        return principalInterest;
    }

    private void applyChargesForCurrentPeriod(final Set<LoanCharge> loanCharges, final MonetaryCurrency currency,
            LoanScheduleParams scheduleParams, LocalDate scheduledDueDate, ScheduleCurrentPeriodParams currentPeriodParams,
            final Money factorRateFeePerInstallment, final Money factorRateTaxPerInstallment, final MathContext mc) {
        final PrincipalInterest principalInterest = new PrincipalInterest(currentPeriodParams.getPrincipalForThisPeriod(),
                currentPeriodParams.getInterestForThisPeriod(), null);
        currentPeriodParams.setFeeChargesForInstallment(factorRateFeePerInstallment);
        currentPeriodParams.setTaxChargesForInstallment(factorRateTaxPerInstallment);
        final Money penaltyChargesForInstallment = cumulativePenaltyChargesDueWithin(scheduleParams.getPeriodStartDate(), scheduledDueDate,
                loanCharges, currency, principalInterest, scheduleParams.getPrincipalToBeScheduled(),
                scheduleParams.getTotalCumulativeInterest(), true, scheduleParams.isFirstPeriod(), mc);
        currentPeriodParams.setPenaltyChargesForInstallment(penaltyChargesForInstallment);
        scheduleParams.addTotalFeeChargesCharged(currentPeriodParams.getFeeChargesForInstallment());
        scheduleParams.addTotalTaxChargesCharged(currentPeriodParams.getTaxChargesForInstallment());
        scheduleParams.addTotalPenaltyChargesCharged(currentPeriodParams.getPenaltyChargesForInstallment());
    }

    /**
     * Override to calculate proportional charges per tranche for multi-tranche loans. For multi-tranche loans, charges
     * should be allocated proportionally based on tranche size.
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
        } else {
            // For multi-tranche loans, calculate proportional charges per tranche
            if (loanApplicationTerms.getDisbursementDatas().isEmpty()) {
                loanApplicationTerms.getDisbursementDatas()
                        .add(new DisbursementData(1L, loanApplicationTerms.getExpectedDisbursementDate(),
                                loanApplicationTerms.getExpectedDisbursementDate(), loanApplicationTerms.getPrincipal().getAmount(), null,
                                null, null, null, null));
            }

            // Get approved principal (sanctioned amount) for proportional calculation
            Money sanctionedAmount = loanApplicationTerms.getApprovedPrincipal();
            if (sanctionedAmount == null || sanctionedAmount.isZero()) {
                // Fallback to total principal if approved principal is not set
                sanctionedAmount = loanApplicationTerms.getPrincipal();
            }

            for (DisbursementData disbursementData : loanApplicationTerms.getDisbursementDatas()) {
                if (disbursementData.disbursementDate().equals(loanScheduleParams.getPeriodStartDate())) {
                    // Calculate proportional charges for this tranche
                    BigDecimal proportionalCharges = calculateProportionalChargesForTranche(disbursementData.getPrincipal(),
                            sanctionedAmount.getAmount(), chargesDueAtTimeOfDisbursement);

                    final LoanScheduleModelDisbursementPeriod disbursementPeriod = LoanScheduleModelDisbursementPeriod.disbursement(
                            disbursementData.disbursementDate(),
                            Money.of(loanScheduleParams.getCurrency(), disbursementData.getPrincipal()), proportionalCharges);
                    periods.add(disbursementPeriod);
                    if (loanApplicationTerms.isDownPaymentEnabled()) {
                        final LoanScheduleModelDownPaymentPeriod downPaymentPeriod = createDownPaymentPeriod(loanApplicationTerms,
                                loanScheduleParams, loanApplicationTerms.getExpectedDisbursementDate(), disbursementData.getPrincipal());
                        periods.add(downPaymentPeriod);
                    }
                }
            }
        }

        return periods;
    }

    /**
     * Override to calculate proportional charges per tranche during schedule generation.
     */
    @Override
    protected void processDisbursements(final LoanApplicationTerms loanApplicationTerms, final BigDecimal chargesDueAtTimeOfDisbursement,
            LoanScheduleParams scheduleParams, final Collection<LoanScheduleModelPeriod> periods, final LocalDate scheduledDueDate,
            final BigDecimal totalOriginalPrincipal) {
        // Get approved principal (sanctioned amount) for proportional calculation
        Money sanctionedAmount = loanApplicationTerms.getApprovedPrincipal();
        if (sanctionedAmount == null || sanctionedAmount.isZero()) {
            // Fallback to total principal if approved principal is not set
            sanctionedAmount = loanApplicationTerms.getPrincipal();
        }

        for (Map.Entry<LocalDate, Money> disburseDetail : scheduleParams.getDisburseDetailMap().entrySet()) {
            if (DateUtils.isAfter(disburseDetail.getKey(), scheduleParams.getPeriodStartDate())
                    && !DateUtils.isAfter(disburseDetail.getKey(), scheduledDueDate)) {
                // validation check for amount not exceeds specified max
                // amount as per the configuration
                if (loanApplicationTerms.getMaxOutstandingBalance() != null) {
                    Money maxOutstandingBalance = loanApplicationTerms.getMaxOutstandingBalanceMoney();
                    if (scheduleParams.getOutstandingBalance().plus(disburseDetail.getValue()).isGreaterThan(maxOutstandingBalance)) {
                        String errorMsg = "Outstanding balance must not exceed the amount: " + maxOutstandingBalance;
                        throw new MultiDisbursementOutstandingAmoutException(errorMsg, maxOutstandingBalance.getAmount(),
                                disburseDetail.getValue());
                    }
                }

                // Calculate proportional charges for this tranche
                BigDecimal proportionalCharges = calculateProportionalChargesForTranche(disburseDetail.getValue().getAmount(),
                        sanctionedAmount.getAmount(), chargesDueAtTimeOfDisbursement);

                // creates and add disbursement detail to the repayments
                // period
                final LoanScheduleModelDisbursementPeriod disbursementPeriod = LoanScheduleModelDisbursementPeriod
                        .disbursement(disburseDetail.getKey(), disburseDetail.getValue(), proportionalCharges);
                periods.add(disbursementPeriod);

                BigDecimal downPaymentAmt = BigDecimal.ZERO;
                if (loanApplicationTerms.isDownPaymentEnabled()) {
                    // get list of disbursements done on same day and create down payment periods
                    List<DisbursementData> disbursementsOnSameDate = loanApplicationTerms.getDisbursementDatas().stream()
                            .filter(disbursementData -> DateUtils.isEqual(disbursementData.disbursementDate(), disburseDetail.getKey()))
                            .toList();
                    for (DisbursementData disbursementData : disbursementsOnSameDate) {
                        final LoanScheduleModelDownPaymentPeriod downPaymentPeriod = createDownPaymentPeriod(loanApplicationTerms,
                                scheduleParams, disbursementData.disbursementDate(), disbursementData.getPrincipal());
                        periods.add(downPaymentPeriod);
                        downPaymentAmt = downPaymentAmt.add(downPaymentPeriod.principalDue());
                    }
                }
                // updates actual outstanding balance with new
                // disbursement detail
                Money remainingPrincipal = disburseDetail.getValue().minus(downPaymentAmt);
                scheduleParams.addOutstandingBalance(remainingPrincipal);
                scheduleParams.addPrincipalToBeScheduled(remainingPrincipal);
                loanApplicationTerms.setPrincipal(loanApplicationTerms.getPrincipal().plus(remainingPrincipal));
            }
        }
    }

    /**
     * Calculates proportional charges for a specific tranche based on the formula: proportionalCharges = (trancheAmount
     * / sanctionedAmount) * totalCharges
     *
     * @param trancheAmount
     *            the amount of the current tranche
     * @param sanctionedAmount
     *            the total sanctioned/approved loan amount
     * @param totalCharges
     *            the total charges due at disbursement (calculated on full loan amount)
     * @return the proportional charges for this tranche
     */
    private BigDecimal calculateProportionalChargesForTranche(final BigDecimal trancheAmount, final BigDecimal sanctionedAmount,
            final BigDecimal totalCharges) {
        if (sanctionedAmount == null || sanctionedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // If sanctioned amount is zero or null, return full charges (fallback for single disbursement)
            return totalCharges;
        }

        if (trancheAmount == null || trancheAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Use MathContext with precision of 19 (standard for financial calculations) and HALF_UP rounding
        MathContext mc = new MathContext(19, RoundingMode.HALF_UP);

        // Calculate proportional charges: (trancheAmount / sanctionedAmount) * totalCharges
        BigDecimal proportionalCharges = totalCharges.multiply(trancheAmount, mc).divide(sanctionedAmount, mc.getPrecision(),
                RoundingMode.HALF_UP);

        return proportionalCharges;
    }
}
