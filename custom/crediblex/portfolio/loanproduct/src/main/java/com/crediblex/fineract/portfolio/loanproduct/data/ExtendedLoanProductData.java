package com.crediblex.fineract.portfolio.loanproduct.data;

import lombok.Getter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.data.StringEnumOptionData;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.delinquency.data.DelinquencyBucketData;
import org.apache.fineract.portfolio.loanproduct.data.AdvancedPaymentData;
import org.apache.fineract.portfolio.loanproduct.data.CreditAllocationData;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductBorrowerCycleVariationData;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductData;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductGuaranteeData;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductInterestRecalculationData;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductConfigurableAttributes;
import org.apache.fineract.portfolio.rate.data.RateData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtendedLoanProductData extends LoanProductData {

    @Getter
    private Map<String,Object> additionalProperties = new HashMap<>();

    public ExtendedLoanProductData(Long id, String name, String shortName, String description, CurrencyData currency, BigDecimal principal, BigDecimal minPrincipal, BigDecimal maxPrincipal, BigDecimal tolerance, Integer numberOfRepayments, Integer minNumberOfRepayments, Integer maxNumberOfRepayments, Integer repaymentEvery, BigDecimal interestRatePerPeriod, BigDecimal minInterestRatePerPeriod, BigDecimal maxInterestRatePerPeriod, BigDecimal annualInterestRate, EnumOptionData repaymentFrequencyType, EnumOptionData interestRateFrequencyType, EnumOptionData amortizationType, EnumOptionData interestType, EnumOptionData interestCalculationPeriodType, Boolean allowPartialPeriodInterestCalculation, Long fundId, String fundName, String transactionProcessingStrategyCode, String transactionProcessingStrategyName, Integer graceOnPrincipalPayment, Integer recurringMoratoriumOnPrincipalPeriods, Integer graceOnInterestPayment, Integer graceOnInterestCharged, Collection<ChargeData> charges, EnumOptionData accountingType, boolean includeInBorrowerCycle, boolean useBorrowerCycle, LocalDate startDate, LocalDate closeDate, String status, String externalId, Collection<LoanProductBorrowerCycleVariationData> principalVariations, Collection<LoanProductBorrowerCycleVariationData> interestRateVariations, Collection<LoanProductBorrowerCycleVariationData> numberOfRepaymentVariations, Boolean multiDisburseLoan, Integer maxTrancheCount, BigDecimal outstandingLoanBalance, Boolean disallowExpectedDisbursements, Boolean allowApprovedDisbursedAmountsOverApplied, String overAppliedCalculationType, Integer overAppliedNumber, Integer graceOnArrearsAgeing, Integer overdueDaysForNPA, EnumOptionData daysInMonthType, EnumOptionData daysInYearType, boolean isInterestRecalculationEnabled, LoanProductInterestRecalculationData interestRecalculationData, Integer minimumDaysBetweenDisbursalAndFirstRepayment, boolean holdGuaranteeFunds, LoanProductGuaranteeData loanProductGuaranteeData, BigDecimal principalThresholdForLastInstallment, boolean accountMovesOutOfNPAOnlyOnArrearsCompletion, boolean canDefineInstallmentAmount, Integer installmentAmountInMultiplesOf, LoanProductConfigurableAttributes allowAttributeOverrides, boolean isLinkedToFloatingInterestRates, Integer floatingRateId, String floatingRateName, BigDecimal interestRateDifferential, BigDecimal minDifferentialLendingRate, BigDecimal defaultDifferentialLendingRate, BigDecimal maxDifferentialLendingRate, boolean isFloatingInterestRateCalculationAllowed, boolean isVariableInstallmentsAllowed, Integer minimumGapBetweenInstallments, Integer maximumGapBetweenInstallments, boolean syncExpectedWithDisbursementDate, boolean canUseForTopup, boolean isEqualAmortization, Collection<RateData> rateOptions, Collection<RateData> rates, boolean isRatesEnabled, BigDecimal fixedPrincipalPercentagePerInstallment, Collection<DelinquencyBucketData> delinquencyBucketOptions, DelinquencyBucketData delinquencyBucket, Integer dueDaysForRepaymentEvent, Integer overDueDaysForRepaymentEvent, boolean enableDownPayment, BigDecimal disbursedAmountPercentageForDownPayment, boolean enableAutoRepaymentForDownPayment, Collection<AdvancedPaymentData> paymentAllocation, Collection<CreditAllocationData> creditAllocation, EnumOptionData repaymentStartDateType, boolean enableInstallmentLevelDelinquency, EnumOptionData loanScheduleType, EnumOptionData loanScheduleProcessingType, Integer fixedLength, boolean enableAccrualActivityPosting, List<StringEnumOptionData> supportedInterestRefundTypes, StringEnumOptionData chargeOffBehaviour, boolean interestRecognitionOnDisbursementDate, StringEnumOptionData daysInYearCustomStrategy, boolean enableIncomeCapitalization, StringEnumOptionData capitalizedIncomeCalculationType, StringEnumOptionData capitalizedIncomeStrategy) {
        super(id, name, shortName, description, currency, principal, minPrincipal, maxPrincipal, tolerance, numberOfRepayments, minNumberOfRepayments, maxNumberOfRepayments, repaymentEvery, interestRatePerPeriod, minInterestRatePerPeriod, maxInterestRatePerPeriod, annualInterestRate, repaymentFrequencyType, interestRateFrequencyType, amortizationType, interestType, interestCalculationPeriodType, allowPartialPeriodInterestCalculation, fundId, fundName, transactionProcessingStrategyCode, transactionProcessingStrategyName, graceOnPrincipalPayment, recurringMoratoriumOnPrincipalPeriods, graceOnInterestPayment, graceOnInterestCharged, charges, accountingType, includeInBorrowerCycle, useBorrowerCycle, startDate, closeDate, status, externalId, principalVariations, interestRateVariations, numberOfRepaymentVariations, multiDisburseLoan, maxTrancheCount, outstandingLoanBalance, disallowExpectedDisbursements, allowApprovedDisbursedAmountsOverApplied, overAppliedCalculationType, overAppliedNumber, graceOnArrearsAgeing, overdueDaysForNPA, daysInMonthType, daysInYearType, isInterestRecalculationEnabled, interestRecalculationData, minimumDaysBetweenDisbursalAndFirstRepayment, holdGuaranteeFunds, loanProductGuaranteeData, principalThresholdForLastInstallment, accountMovesOutOfNPAOnlyOnArrearsCompletion, canDefineInstallmentAmount, installmentAmountInMultiplesOf, allowAttributeOverrides, isLinkedToFloatingInterestRates, floatingRateId, floatingRateName, interestRateDifferential, minDifferentialLendingRate, defaultDifferentialLendingRate, maxDifferentialLendingRate, isFloatingInterestRateCalculationAllowed, isVariableInstallmentsAllowed, minimumGapBetweenInstallments, maximumGapBetweenInstallments, syncExpectedWithDisbursementDate, canUseForTopup, isEqualAmortization, rateOptions, rates, isRatesEnabled, fixedPrincipalPercentagePerInstallment, delinquencyBucketOptions, delinquencyBucket, dueDaysForRepaymentEvent, overDueDaysForRepaymentEvent, enableDownPayment, disbursedAmountPercentageForDownPayment, enableAutoRepaymentForDownPayment, paymentAllocation, creditAllocation, repaymentStartDateType, enableInstallmentLevelDelinquency, loanScheduleType, loanScheduleProcessingType, fixedLength, enableAccrualActivityPosting, supportedInterestRefundTypes, chargeOffBehaviour, interestRecognitionOnDisbursementDate, daysInYearCustomStrategy, enableIncomeCapitalization, capitalizedIncomeCalculationType, capitalizedIncomeStrategy);
    }

    public static ExtendedLoanProductData fromLoanProductData(LoanProductData data) {
        if (data == null) {
            return null;
        }
        if (data instanceof ExtendedLoanProductData) {
            return (ExtendedLoanProductData) data;
        }
       return new ExtendedLoanProductData(
                data.getId(),
                data.getName(),
                data.getShortName(),
                data.getDescription(),
                data.getCurrency(),
                data.getPrincipal(),
                data.getMinPrincipal(),
                data.getMaxPrincipal(),
                data.getInArrearsTolerance(),
                data.getNumberOfRepayments(),
                data.getMinNumberOfRepayments(),
                data.getMaxNumberOfRepayments(),
                data.getRepaymentEvery(),
                data.getInterestRatePerPeriod(),
                data.getMinInterestRatePerPeriod(),
                data.getMaxInterestRatePerPeriod(),
                data.getAnnualInterestRate(),
                data.getRepaymentFrequencyType(),
                data.getInterestRateFrequencyType(),
                data.getAmortizationType(),
                data.getInterestType(),
                data.getInterestCalculationPeriodType(),
                data.getAllowPartialPeriodInterestCalculation(),
                data.getFundId(),
                data.getFundName(),
                data.getTransactionProcessingStrategyCode(),
                data.getTransactionProcessingStrategyName(),
                data.getGraceOnPrincipalPayment(),
                data.getRecurringMoratoriumOnPrincipalPeriods(),
                data.getGraceOnInterestPayment(),
                data.getGraceOnInterestCharged(),
                data.getCharges(),
                data.getAccountingRule(),
                data.isIncludeInBorrowerCycle(),
                data.isUseBorrowerCycle(),
                data.getStartDate(),
                data.getCloseDate(),
                data.getStatus(),
                data.getExternalId(),
                data.getPrincipalVariationsForBorrowerCycle(),
                data.getInterestRateVariationsForBorrowerCycle(),
                data.getNumberOfRepaymentVariationsForBorrowerCycle(),
                data.getMultiDisburseLoan(),
                data.getMaxTrancheCount(),
                data.getOutstandingLoanBalance(),
                data.getDisallowExpectedDisbursements(),
                data.getAllowApprovedDisbursedAmountsOverApplied(),
                data.getOverAppliedCalculationType(),
                data.getOverAppliedNumber(),
                data.getGraceOnArrearsAgeing(),
                data.getOverdueDaysForNPA(),
                data.getDaysInMonthType(),
                data.getDaysInYearType(),
                data.isInterestRecalculationEnabled(),
                data.getInterestRecalculationData(),
                data.getMinimumDaysBetweenDisbursalAndFirstRepayment(),
                Boolean.TRUE.equals(data.getHoldGuaranteeFunds()),
                data.getProductGuaranteeData(),
                data.getPrincipalThresholdForLastInstallment(),
                Boolean.TRUE.equals(data.getAccountMovesOutOfNPAOnlyOnArrearsCompletion()),
                data.isCanDefineInstallmentAmount(),
                data.getInstallmentAmountInMultiplesOf(),
                data.getAllowAttributeOverrides(),
                data.isLinkedToFloatingInterestRates(),
                data.getFloatingRateId(),
                data.getFloatingRateName(),
                data.getInterestRateDifferential(),
                data.getMinDifferentialLendingRate(),
                data.getDefaultDifferentialLendingRate(),
                data.getMaxDifferentialLendingRate(),
                data.isFloatingInterestRateCalculationAllowed(),
                data.isAllowVariableInstallments(),
                data.getMinimumGap(),
                data.getMaximumGap(),
                data.isSyncExpectedWithDisbursementDate(),
                data.isCanUseForTopup(),
                data.isEqualAmortization(),
                data.getRateOptions(),
                data.getRates(),
                data.isRatesEnabled(),
                data.getFixedPrincipalPercentagePerInstallment(),
                data.getDelinquencyBucketOptions(),
                data.getDelinquencyBucket(),
                data.getDueDaysForRepaymentEvent(),
                data.getOverDueDaysForRepaymentEvent(),
                data.isEnableDownPayment(),
                data.getDisbursedAmountPercentageForDownPayment(),
                data.isEnableAutoRepaymentForDownPayment(),
                data.getPaymentAllocation(),
                data.getCreditAllocation(),
                data.getRepaymentStartDateType(),
                data.isEnableInstallmentLevelDelinquency(),
                data.getLoanScheduleType(),
                data.getLoanScheduleProcessingType(),
                data.getFixedLength(),
                data.isEnableAccrualActivityPosting(),
                data.getSupportedInterestRefundTypes(),
                data.getChargeOffBehaviour(),
                data.isInterestRecognitionOnDisbursementDate(),
                data.getDaysInYearCustomStrategy(),
                Boolean.TRUE.equals(data.getEnableIncomeCapitalization()),
                data.getCapitalizedIncomeCalculationType(),
                data.getCapitalizedIncomeStrategy()
        );
    }
}
