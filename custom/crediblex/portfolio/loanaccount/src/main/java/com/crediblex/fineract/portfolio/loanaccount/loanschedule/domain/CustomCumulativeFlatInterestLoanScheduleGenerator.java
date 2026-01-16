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
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
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
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.CumulativeFlatInterestLoanScheduleGenerator;
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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
public class CustomCumulativeFlatInterestLoanScheduleGenerator extends CumulativeFlatInterestLoanScheduleGenerator {

    public CustomCumulativeFlatInterestLoanScheduleGenerator(ScheduledDateGenerator scheduledDateGenerator,
            PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator) {
        super(scheduledDateGenerator, paymentPeriodsInOneYearCalculator);
    }

    @Override
    public LoanScheduleModel generate(final MathContext mc, final LoanApplicationTerms loanApplicationTerms,
            final Set<LoanCharge> loanCharges, final HolidayDetailDTO holidayDetailDTO) {
        return generateInternal(mc, loanApplicationTerms, loanCharges, holidayDetailDTO, null);
    }

    @Override
    public PrincipalInterest calculatePrincipalInterestComponentsForPeriod(final PaymentPeriodsInOneYearCalculator calculator,
            final BigDecimal interestCalculationGraceOnRepaymentPeriodFraction, final Money totalCumulativePrincipal,
            Money totalCumulativeInterest, Money totalInterestDueForLoan, final Money cumulatingInterestPaymentDueToGrace,
            final Money outstandingBalance, final LoanApplicationTerms loanApplicationTerms, final int periodNumber, final MathContext mc,
            @SuppressWarnings("unused") TreeMap<LocalDate, Money> principalVariation,
            @SuppressWarnings("unused") Map<LocalDate, Money> compoundingMap, LocalDate periodStartDate, LocalDate periodEndDate,
            @SuppressWarnings("unused") Collection<LoanTermVariationsData> termVariations) {

        if (Boolean.TRUE.equals(loanApplicationTerms.getIsReceivableLineOfCredit())) {
            return calculateFlatInterestWithDeduction(calculator, interestCalculationGraceOnRepaymentPeriodFraction,
                    totalCumulativePrincipal, totalCumulativeInterest, totalInterestDueForLoan, cumulatingInterestPaymentDueToGrace,
                    loanApplicationTerms, periodNumber, mc, periodStartDate, periodEndDate);
        }

        final PrincipalInterest result = loanApplicationTerms.calculateTotalInterestForPeriod(calculator,
                interestCalculationGraceOnRepaymentPeriodFraction, periodNumber, mc, cumulatingInterestPaymentDueToGrace,
                outstandingBalance, periodStartDate, periodEndDate);
        Money interestForThisInstallment = result.interest();

        Money principalForThisInstallment = loanApplicationTerms.calculateTotalPrincipalForPeriod(calculator, outstandingBalance,
                periodNumber, mc, interestForThisInstallment);

        // update cumulative fields for principal & interest
        final Money interestBroughtForwardDueToGrace = result.interestPaymentDueToGrace();
        final Money totalCumulativePrincipalToDate = totalCumulativePrincipal.plus(principalForThisInstallment);
        final Money totalCumulativeInterestToDate = totalCumulativeInterest.plus(interestForThisInstallment);

        // adjust if needed
        principalForThisInstallment = loanApplicationTerms.adjustPrincipalIfLastRepaymentPeriod(principalForThisInstallment,
                totalCumulativePrincipalToDate, periodNumber);

        // totalCumulativeInterest from partial schedule generation for multi
        // rescheduling
        /*
         * if (loanApplicationTerms.getPartialTotalCumulativeInterest() != null &&
         * loanApplicationTerms.getTotalInterestDue() != null) { totalInterestDueForLoan =
         * loanApplicationTerms.getTotalInterestDue(); totalInterestDueForLoan =
         * totalInterestDueForLoan.plus(loanApplicationTerms. getPartialTotalCumulativeInterest()); }
         */
        interestForThisInstallment = loanApplicationTerms.adjustInterestIfLastRepaymentPeriod(interestForThisInstallment,
                totalCumulativeInterestToDate, totalInterestDueForLoan, periodNumber);

        if (loanApplicationTerms.getIsReceivableLineOfCredit()) {
            principalForThisInstallment = principalForThisInstallment.minus(interestForThisInstallment);
        }

        return new PrincipalInterest(principalForThisInstallment, interestForThisInstallment, interestBroughtForwardDueToGrace);
    }

    /**
     * NEW METHOD: Calculate flat interest with deduction from fixed installment
     *
     * For Receivable Line of Credit / Discounted Loans: - Total Interest = Principal × Rate × Time (calculated once) -
     * Total Interest is DEDUCTED from disbursement upfront - Amount Disbursed = Principal - Total Interest - Fixed
     * Installment = Principal / Number of Periods - Interest per Period = Total Interest / Number of Periods - Actual
     * Principal per Period = Fixed Installment - Interest per Period
     *
     * Example: $10,000 loan, 12% annual, 10 months - Total Interest = $1,000 - Disbursed = $9,000 (customer receives
     * this) - Fixed Installment = $1,000 - Interest per period = $100 - Principal per period = $900 - After 10 periods:
     * $900 × 10 = $9,000 (matches disbursement!)
     */
    private PrincipalInterest calculateFlatInterestWithDeduction(final PaymentPeriodsInOneYearCalculator calculator,
            final BigDecimal interestCalculationGraceOnRepaymentPeriodFraction, final Money totalCumulativeInterest,
            final Money totalInterestDueForLoan, final Money cumulatingInterestPaymentDueToGrace, final Money outstandingBalance,
            final LoanApplicationTerms loanApplicationTerms, final int periodNumber, final MathContext mc, final LocalDate periodStartDate,
            final LocalDate periodEndDate) {

        Money nominalPrincipal = loanApplicationTerms.getPrincipal();
        Money disbursalPrincipal = loanApplicationTerms.getDisbursedPrincipal();
        int totalPeriods = loanApplicationTerms.getNumberOfRepayments();

        // Step 2: Calculate FIXED installment amount (based on NOMINAL principal)
        Money fixedInstallmentAmount = nominalPrincipal.dividedBy(totalPeriods, mc);

        // Handle installment multiples if configured
        if (loanApplicationTerms.getInstallmentAmountInMultiplesOf() != null) {
            fixedInstallmentAmount = Money.roundToMultiplesOf(fixedInstallmentAmount,
                    loanApplicationTerms.getInstallmentAmountInMultiplesOf());
        }

        if (loanApplicationTerms.getIsReceivableLineOfCredit()) {
            loanApplicationTerms
                    .setDisbursedPrincipal(Money.of(nominalPrincipal.getCurrency(), loanApplicationTerms.getAmountAfterAdvance()));
        }

        final PrincipalInterest result = loanApplicationTerms.calculateTotalInterestForPeriod(calculator,
                interestCalculationGraceOnRepaymentPeriodFraction, periodNumber, mc, cumulatingInterestPaymentDueToGrace,
                outstandingBalance, periodStartDate, periodEndDate);

        // We dont care because LOC is always 1 period
        if (loanApplicationTerms.getIsReceivableLineOfCredit()) {
            loanApplicationTerms.setDisbursedPrincipal(disbursalPrincipal);
        }

        Money interestForThisInstallment = result.interest();
        Money cumulatingInterestDueToGrace = result.interestPaymentDueToGrace();

        Money principalForThisInstallment = fixedInstallmentAmount;

        // Safety check: Principal cannot be negative
        if (principalForThisInstallment.isLessThanZero()) {
            // Interest exceeds fixed installment - set principal to zero
            principalForThisInstallment = principalForThisInstallment.zero();
        }

        final Money interestBroughtForwardDueToGrace = cumulatingInterestDueToGrace;

        // Adjust interest for last period if needed (standard flat rate adjustment)
        interestForThisInstallment = loanApplicationTerms.adjustInterestIfLastRepaymentPeriod(interestForThisInstallment,
                totalCumulativeInterest.plus(interestForThisInstallment), totalInterestDueForLoan, periodNumber);

        return new PrincipalInterest(principalForThisInstallment, interestForThisInstallment, interestBroughtForwardDueToGrace);
    }

    private LoanScheduleModel generateInternal(final MathContext mc, final LoanApplicationTerms loanApplicationTerms,
            final Set<LoanCharge> loanCharges, final HolidayDetailDTO holidayDetailDTO, final LoanScheduleParams loanScheduleParams) {

        final BigDecimal totalOriginalPrincipal = loanApplicationTerms.getPrincipal().getAmount();
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

        if (loanApplicationTerms.getIsReceivableLineOfCredit()) {
            scheduleParams.addTotalRepaymentExpected(Money.of(currency, chargesDueAtTimeOfDisbursement.negate()));
        }

        while (!scheduleParams.getOutstandingBalance().isZero() || !scheduleParams.getDisburseDetailMap().isEmpty()) {
            LocalDate previousRepaymentDate = scheduleParams.getActualRepaymentDate();
            scheduleParams.setActualRepaymentDate(getScheduledDateGenerator()
                    .generateNextRepaymentDate(scheduleParams.getActualRepaymentDate(), loanApplicationTerms, isFirstRepayment));
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

            if (Boolean.TRUE.equals(loanApplicationTerms.getIsReceivableLineOfCredit())) {
                Money adjustedPrincipal = scheduleParams.getOutstandingBalance()
                        .minus(principalInterestForThisPeriod.interestPaymentDueToGrace().add(principalInterestForThisPeriod.interest()));

                if (adjustedPrincipal.isLessThanZero()) {
                    adjustedPrincipal = Money.zero(adjustedPrincipal.getCurrency());
                }

                scheduleParams.setOutstandingBalance(adjustedPrincipal);

            }

            // applies early payments on principal portion
            updatePrincipalPortionBasedOnPreviousEarlyPayments(currency, scheduleParams, currentPeriodParams);

            // updates amounts with current earlyPaidAmount
            updateAmountsBasedOnCurrentEarlyPayments(mc, loanApplicationTerms, scheduleParams, currentPeriodParams);

            if (scheduleParams.getOutstandingBalance().isLessThanZero() || !isNextRepaymentAvailable) {
                currentPeriodParams.plusPrincipalForThisPeriod(scheduleParams.getOutstandingBalance());
                scheduleParams.setOutstandingBalance(Money.zero(currency));
            }

            // Add it here to cater for fee calculations based on this principal
            if (loanApplicationTerms.getIsReceivableLineOfCredit()) {
                currentPeriodParams.plusPrincipalForThisPeriod(principalInterestForThisPeriod.interestPaymentDueToGrace());
            }

            if (!isNextRepaymentAvailable) {
                scheduleParams.getDisburseDetailMap().clear();
            }

            // applies charges for the period
            if (loanApplicationTerms.getIsReceivableLineOfCredit()) {
                applyChargesForCurrentPeriod(Money.of(currency, loanApplicationTerms.getAmountAfterAdvance()), loanCharges,
                        monetaryCurrency, scheduleParams, scheduledDueDate, currentPeriodParams, mc);

            } else {
                applyChargesForCurrentPeriod(loanCharges, monetaryCurrency, scheduleParams, scheduledDueDate, currentPeriodParams, mc);
            }

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
            LoanScheduleModelPeriod installment = periods.get(periods.size() - 1);
            installment.addInterestAmount(scheduleParams.getTotalOutstandingInterestPaymentDueToGrace());
            // We want the total due to be the principal only for line of credit receivable
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
        if (scheduleParams.applyInterestRecalculation() && scheduleParams.getLatePaymentMap().size() > 0
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
        return LoanScheduleModel.from(periods, currency, scheduleParams.getLoanTermInDays(),
                scheduleParams.getPrincipalToBeScheduled().plus(loanApplicationTerms.getDownPaymentAmount()),
                scheduleParams.getTotalCumulativePrincipal().plus(loanApplicationTerms.getDownPaymentAmount()).getAmount(),
                totalPrincipalPaid, scheduleParams.getTotalCumulativeInterest().getAmount(),
                scheduleParams.getTotalFeeChargesCharged().getAmount(), scheduleParams.getTotalTaxChargesCharged().getAmount(),
                scheduleParams.getTotalPenaltyChargesCharged().getAmount(), scheduleParams.getTotalRepaymentExpected().getAmount(),
                totalOutstanding);
    }

    protected void applyChargesForCurrentPeriod(final Money originalPrincipalPortial, final Set<LoanCharge> loanCharges,
            final MonetaryCurrency currency, LoanScheduleParams scheduleParams, LocalDate scheduledDueDate,
            ScheduleCurrentPeriodParams currentPeriodParams, final MathContext mc) {
        PrincipalInterest principalInterest = new PrincipalInterest(originalPrincipalPortial,
                currentPeriodParams.getInterestForThisPeriod(), null);
        currentPeriodParams.setFeeChargesForInstallment(cumulativeFeeChargesDueWithin(scheduleParams.getPeriodStartDate(), scheduledDueDate,
                loanCharges, currency, principalInterest, scheduleParams.getPrincipalToBeScheduled(),
                scheduleParams.getTotalCumulativeInterest(), true, scheduleParams.isFirstPeriod(), mc));
        currentPeriodParams.setPenaltyChargesForInstallment(cumulativePenaltyChargesDueWithin(scheduleParams.getPeriodStartDate(),
                scheduledDueDate, loanCharges, currency, principalInterest, scheduleParams.getPrincipalToBeScheduled(),
                scheduleParams.getTotalCumulativeInterest(), true, scheduleParams.isFirstPeriod(), mc));
        scheduleParams.addTotalFeeChargesCharged(currentPeriodParams.getFeeChargesForInstallment());
        scheduleParams.addTotalPenaltyChargesCharged(currentPeriodParams.getPenaltyChargesForInstallment());
    }

    /**
     * Override to fix issue where LOC Receivable installment fees are recalculated from installment principal
     * (disbursed amount) instead of using the charge's total amount (calculated from proposed principal). This ensures
     * fees remain consistent after approval: 10% of loan amount, not 10% of disbursed amount.
     */
    @Override
    protected Money cumulativeFeeChargesDueWithin(final LocalDate periodStart, final LocalDate periodEnd, final Set<LoanCharge> loanCharges,
            final MonetaryCurrency monetaryCurrency, final PrincipalInterest principalInterestForThisPeriod, final Money principalDisbursed,
            final Money totalInterestChargedForFullLoanTerm, boolean isInstallmentChargeApplicable, final boolean isFirstPeriod,
            final MathContext mc) {

        Money cumulative = Money.zero(monetaryCurrency);

        for (final LoanCharge loanCharge : loanCharges) {
            if (!loanCharge.isDueAtDisbursement() && loanCharge.isFeeCharge()) {
                boolean isDue = loanCharge.isDueInPeriod(periodStart, periodEnd, isFirstPeriod);
                boolean isLocReceivableInstallmentFee = loanCharge.isInstalmentFee() && isInstallmentChargeApplicable
                        && loanCharge.getLoan() != null && loanCharge.getLoan().isReceivableLocLoan()
                        && loanCharge.getChargeCalculation().isPercentageBased();
                boolean taxAlreadyIncluded = false;

                if (loanCharge.isInstalmentFee() && isInstallmentChargeApplicable) {
                    // For LOC Receivable loans with percentage-based installment fees, use the charge's total amount
                    // (which is calculated from proposed principal) divided by installments, instead of recalculating
                    // from installment principal (which uses disbursed amount)
                    if (isLocReceivableInstallmentFee) {
                        // Use charge's total amount (including tax) divided by number of installments
                        // This ensures consistent fee calculation: 10% of loan amount, not 10% of disbursed amount
                        int numberOfInstallments = loanCharge.getLoan().fetchNumberOfInstallmensAfterExceptions();
                        if (numberOfInstallments > 0) {
                            // For LOC Receivable, always recalculate from amountPercentageAppliedTo (proposed
                            // principal)
                            // instead of using stored charge amount, which might be temporarily incorrect during
                            // schedule regeneration (e.g., during approval when schedule is regenerated)
                            BigDecimal totalChargeAmount;
                            if (loanCharge.getAmountPercentageAppliedTo() != null && loanCharge.getPercentage() != null) {
                                // Recalculate from amountPercentageAppliedTo to ensure we use proposed principal
                                BigDecimal baseAmount = loanCharge.getAmountPercentageAppliedTo();
                                BigDecimal chargeBaseAmount = baseAmount.multiply(loanCharge.getPercentage())
                                        .divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);

                                // Recalculate tax from the correct base amount (chargeBaseAmount) to ensure consistency
                                // The stored tax amount might be incorrect during approval if it was calculated from
                                // the wrong base amount (disbursed principal instead of proposed principal)
                                if (loanCharge.hasTax() && loanCharge.getCharge() != null && loanCharge.getCharge().getTaxGroup() != null
                                        && loanCharge.getCharge().getTaxGroup().getTaxGroupMappings() != null
                                        && !loanCharge.getCharge().getTaxGroup().getTaxGroupMappings().isEmpty()) {
                                    LocalDate chargeDate = loanCharge.getDueDate() != null ? loanCharge.getDueDate()
                                            : DateUtils.getBusinessLocalDate();
                                    Set<TaxGroupMappings> taxGroupMappings = loanCharge.getCharge().getTaxGroup().getTaxGroupMappings();
                                    // Calculate total amount with tax, then subtract base to get tax amount
                                    BigDecimal totalWithTax = TaxUtils.addTaxToAmount(chargeBaseAmount, chargeDate, taxGroupMappings,
                                            chargeBaseAmount.scale());
                                    BigDecimal recalculatedTaxAmount = totalWithTax != null ? totalWithTax.subtract(chargeBaseAmount)
                                            : BigDecimal.ZERO;
                                    totalChargeAmount = chargeBaseAmount.add(recalculatedTaxAmount);
                                } else {
                                    totalChargeAmount = chargeBaseAmount;
                                }
                            } else {
                                // Fallback to stored amount if amountPercentageAppliedTo is not available
                                totalChargeAmount = loanCharge.getAmountWithTaxes();
                            }

                            Money chargeAmountPerInstallment = Money.of(monetaryCurrency, totalChargeAmount).dividedBy(numberOfInstallments,
                                    mc);
                            cumulative = cumulative.plus(chargeAmountPerInstallment);
                            taxAlreadyIncluded = true; // Tax is already included in totalChargeAmount
                        }
                    } else {
                        // For non-LOC loans or non-percentage charges, use standard calculation
                        // Reimplement calculateInstallmentCharge logic since it's private in base class
                        if (loanCharge.getChargeCalculation().isPercentageBased()) {
                            BigDecimal amount = BigDecimal.ZERO;
                            if (loanCharge.getChargeCalculation().isPercentageOfAmountAndInterest()) {
                                amount = amount.add(principalInterestForThisPeriod.principal().getAmount())
                                        .add(principalInterestForThisPeriod.interest().getAmount());
                            } else if (loanCharge.getChargeCalculation().isPercentageOfInterest()) {
                                amount = amount.add(principalInterestForThisPeriod.interest().getAmount());
                            } else {
                                amount = amount.add(principalInterestForThisPeriod.principal().getAmount());
                            }
                            BigDecimal loanChargeAmt = amount.multiply(loanCharge.getPercentage()).divide(BigDecimal.valueOf(100), mc);
                            cumulative = cumulative.plus(loanChargeAmt);
                        } else {
                            cumulative = cumulative.plus(loanCharge.amountOrPercentage());
                        }
                    }
                } else if (loanCharge.isOverdueInstallmentCharge() && isDue && loanCharge.getChargeCalculation().isPercentageBased()) {
                    cumulative = cumulative.plus(loanCharge.chargeAmount());
                } else if (isDue && loanCharge.getChargeCalculation().isPercentageBased()) {
                    // Reimplement calculateSpecificDueDateChargeWithPercentage logic since it's private in base class
                    BigDecimal amount = BigDecimal.ZERO;
                    if (loanCharge.getChargeCalculation().isPercentageOfAmountAndInterest()) {
                        amount = amount.add(principalDisbursed.getAmount()).add(totalInterestChargedForFullLoanTerm.getAmount());
                    } else if (loanCharge.getChargeCalculation().isPercentageOfInterest()) {
                        amount = amount.add(totalInterestChargedForFullLoanTerm.getAmount());
                    } else {
                        amount = amount.add(principalDisbursed.getAmount());
                    }
                    BigDecimal loanChargeAmt = amount.multiply(loanCharge.getPercentage()).divide(BigDecimal.valueOf(100), mc);
                    cumulative = cumulative.plus(loanChargeAmt);
                } else if (isDue) {
                    cumulative = cumulative.plus(loanCharge.amount());
                }

                // Add tax separately, but skip if already included (for LOC Receivable installment fees)
                if (loanCharge.hasTax() && !taxAlreadyIncluded) {
                    cumulative = cumulative.plus(loanCharge.getTaxAmount());
                }
            }
        }

        return cumulative;
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

}
