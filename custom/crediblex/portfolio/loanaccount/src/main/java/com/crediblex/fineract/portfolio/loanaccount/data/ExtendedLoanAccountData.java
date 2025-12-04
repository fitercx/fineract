package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.data.StringEnumOptionData;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.delinquency.data.DelinquencyRangeData;
import org.apache.fineract.portfolio.group.data.GroupGeneralData;
import org.apache.fineract.portfolio.loanaccount.data.CollectionData;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.apache.fineract.portfolio.loanaccount.data.LoanApplicationTimelineData;
import org.apache.fineract.portfolio.loanaccount.data.LoanInterestRecalculationData;
import org.apache.fineract.portfolio.loanaccount.data.LoanStatusEnumData;
import org.apache.fineract.portfolio.loanaccount.data.LoanSummaryData;

@Getter
public class ExtendedLoanAccountData extends LoanAccountData {

    public void addCustomParameter(String name, Object value) {
        additionalProperties.put(name, value);
    }

    public static ExtendedLoanAccountData basicLoanDetails(final Long id, final String accountNo, final LoanStatusEnumData status,
            final ExternalId externalId, final Long clientId, final String clientAccountNo, final String clientName,
            final Long clientOfficeId, final ExternalId clientExternalId, final GroupGeneralData group, final EnumOptionData loanType,
            final Long loanProductId, final String loanProductName, final String loanProductDescription,
            final boolean isLoanProductLinkedToFloatingRate, final Long fundId, final String fundName, final Long loanPurposeId,
            final String loanPurposeName, final Long loanOfficerId, final String loanOfficerName, final CurrencyData currencyData,
            final BigDecimal proposedPrincipal, final BigDecimal principal, final BigDecimal approvedPrincipal,
            final BigDecimal netDisbursalAmount, final BigDecimal totalOverpaid, final BigDecimal inArrearsTolerance,
            final Integer termFrequency, final EnumOptionData termPeriodFrequencyType, final Integer numberOfRepayments,
            final Integer repaymentEvery, final EnumOptionData repaymentFrequencyType, EnumOptionData repaymentFrequencyNthDayType,
            EnumOptionData repaymentFrequencyDayOfWeekType, final String transactionStrategy, final String transactionStrategyName,
            final EnumOptionData amortizationType, final BigDecimal interestRatePerPeriod, final EnumOptionData interestRateFrequencyType,
            final BigDecimal annualInterestRate, final EnumOptionData interestType, final boolean isFloatingInterestRate,
            final BigDecimal interestRateDifferential, final EnumOptionData interestCalculationPeriodType,
            Boolean allowPartialPeriodInterestCalcualtion, final LocalDate expectedFirstRepaymentOnDate,
            final Integer graceOnPrincipalPayment, final Integer recurringMoratoriumOnPrincipalPeriods,
            final Integer graceOnInterestPayment, final Integer graceOnInterestCharged, final LocalDate interestChargedFromDate,
            final LoanApplicationTimelineData timeline, final LoanSummaryData loanSummary,
            final BigDecimal feeChargesDueAtDisbursementCharged, final Boolean syncDisbursementWithMeeting, final Integer loanCounter,
            final Integer loanProductCounter, final Boolean multiDisburseLoan, Boolean canDefineInstallmentAmount,
            final BigDecimal fixedEmiAmont, final BigDecimal outstandingLoanBalance, final Boolean inArrears,
            final Integer graceOnArrearsAgeing, final Integer penaltyGracePeriod, final Boolean isNPA, final EnumOptionData daysInMonthType,
            final EnumOptionData daysInYearType, final boolean isInterestRecalculationEnabled,
            final LoanInterestRecalculationData interestRecalculationData, final Boolean createStandingInstructionAtDisbursement,
            final Boolean isVariableInstallmentsAllowed, Integer minimumGap, Integer maximumGap, final EnumOptionData subStatus,
            final boolean canUseForTopup, final boolean isTopup, final Long closureLoanId, final String closureLoanAccountNo,
            final BigDecimal topupAmount, final boolean isEqualAmortization, final BigDecimal fixedPrincipalPercentagePerInstallment,
            final DelinquencyRangeData delinquencyRange, final boolean disallowExpectedDisbursements, final boolean fraud,
            LocalDate lastClosedBusinessDate, LocalDate overpaidOnDate, final boolean chargedOff, final boolean enableDownPayment,
            final BigDecimal disbursedAmountPercentageForDownPayment, final boolean enableAutoRepaymentForDownPayment,
            final boolean enableInstallmentLevelDelinquency, final EnumOptionData loanScheduleType,
            final EnumOptionData loanScheduleProcessingType, final Integer fixedLength, final StringEnumOptionData chargeOffBehaviour,
            final boolean isInterestRecognitionOnDisbursementDate, final StringEnumOptionData daysInYearCustomStrategy,
            final boolean enableIncomeCapitalization, final StringEnumOptionData capitalizedIncomeCalculationType,
            final StringEnumOptionData capitalizedIncomeStrategy, final BigDecimal factorRateLoanAmount) {

        final CollectionData delinquent = CollectionData.template();

        ExtendedLoanAccountData extendedLoanAccountData = new ExtendedLoanAccountData();
        return (ExtendedLoanAccountData) extendedLoanAccountData.setId(id).setAccountNo(accountNo).setStatus(status)
                .setExternalId(externalId).setClientId(clientId).setClientAccountNo(clientAccountNo).setClientName(clientName)
                .setClientOfficeId(clientOfficeId).setClientExternalId(clientExternalId).setGroup(group).setLoanType(loanType)
                .setLoanProductId(loanProductId).setLoanProductName(loanProductName).setLoanProductDescription(loanProductDescription)
                .setLoanProductLinkedToFloatingRate(isLoanProductLinkedToFloatingRate).setFundId(fundId).setFundName(fundName)
                .setLoanPurposeId(loanPurposeId).setLoanPurposeName(loanPurposeName).setLoanOfficerId(loanOfficerId)
                .setLoanOfficerName(loanOfficerName).setCurrency(currencyData).setProposedPrincipal(proposedPrincipal)
                .setPrincipal(principal).setApprovedPrincipal(approvedPrincipal).setNetDisbursalAmount(netDisbursalAmount)
                .setTotalOverpaid(totalOverpaid).setInArrearsTolerance(inArrearsTolerance).setTermFrequency(termFrequency)
                .setTermPeriodFrequencyType(termPeriodFrequencyType).setNumberOfRepayments(numberOfRepayments)
                .setRepaymentEvery(repaymentEvery).setRepaymentFrequencyType(repaymentFrequencyType)
                .setRepaymentFrequencyNthDayType(repaymentFrequencyNthDayType)
                .setRepaymentFrequencyDayOfWeekType(repaymentFrequencyDayOfWeekType)
                .setTransactionProcessingStrategyCode(transactionStrategy).setTransactionProcessingStrategyName(transactionStrategyName)
                .setAmortizationType(amortizationType).setInterestRatePerPeriod(interestRatePerPeriod)
                .setInterestRateFrequencyType(interestRateFrequencyType).setAnnualInterestRate(annualInterestRate)
                .setInterestType(interestType).setFloatingInterestRate(isFloatingInterestRate)
                .setInterestRateDifferential(interestRateDifferential).setInterestCalculationPeriodType(interestCalculationPeriodType)
                .setAllowPartialPeriodInterestCalculation(allowPartialPeriodInterestCalcualtion)
                .setExpectedFirstRepaymentOnDate(expectedFirstRepaymentOnDate).setGraceOnPrincipalPayment(graceOnPrincipalPayment)
                .setRecurringMoratoriumOnPrincipalPeriods(recurringMoratoriumOnPrincipalPeriods)
                .setGraceOnInterestPayment(graceOnInterestPayment).setGraceOnInterestCharged(graceOnInterestCharged)
                .setInterestChargedFromDate(interestChargedFromDate).setTimeline(timeline).setSummary(loanSummary)
                .setFeeChargesAtDisbursementCharged(feeChargesDueAtDisbursementCharged)
                .setSyncDisbursementWithMeeting(syncDisbursementWithMeeting).setLoanCounter(loanCounter)
                .setLoanProductCounter(loanProductCounter).setMultiDisburseLoan(multiDisburseLoan)
                .setCanDefineInstallmentAmount(canDefineInstallmentAmount).setFixedEmiAmount(fixedEmiAmont)
                .setMaxOutstandingLoanBalance(outstandingLoanBalance).setInArrears(inArrears).setGraceOnArrearsAgeing(graceOnArrearsAgeing)
                .setPenaltyGracePeriod(penaltyGracePeriod).setIsNPA(isNPA).setDaysInMonthType(daysInMonthType)
                .setDaysInYearType(daysInYearType).setInterestRecalculationEnabled(isInterestRecalculationEnabled)
                .setInterestRecalculationData(interestRecalculationData)
                .setCreateStandingInstructionAtDisbursement(createStandingInstructionAtDisbursement)
                .setIsVariableInstallmentsAllowed(isVariableInstallmentsAllowed).setMinimumGap(minimumGap).setMaximumGap(maximumGap)
                .setSubStatus(subStatus).setCanUseForTopup(canUseForTopup).setTopup(isTopup).setClosureLoanId(closureLoanId)
                .setClosureLoanAccountNo(closureLoanAccountNo).setTopupAmount(topupAmount).setIsEqualAmortization(isEqualAmortization)
                .setFixedPrincipalPercentagePerInstallment(fixedPrincipalPercentagePerInstallment).setDelinquent(delinquent)
                .setDelinquencyRange(delinquencyRange).setDisallowExpectedDisbursements(disallowExpectedDisbursements).setFraud(fraud)
                .setLastClosedBusinessDate(lastClosedBusinessDate).setOverpaidOnDate(overpaidOnDate).setChargedOff(chargedOff)
                .setEnableDownPayment(enableDownPayment).setDisbursedAmountPercentageForDownPayment(disbursedAmountPercentageForDownPayment)
                .setEnableAutoRepaymentForDownPayment(enableAutoRepaymentForDownPayment)
                .setEnableInstallmentLevelDelinquency(enableInstallmentLevelDelinquency).setLoanScheduleType(loanScheduleType)
                .setLoanScheduleProcessingType(loanScheduleProcessingType).setFixedLength(fixedLength)
                .setChargeOffBehaviour(chargeOffBehaviour).setInterestRecognitionOnDisbursementDate(isInterestRecognitionOnDisbursementDate)
                .setDaysInYearCustomStrategy(daysInYearCustomStrategy).setEnableIncomeCapitalization(enableIncomeCapitalization)
                .setCapitalizedIncomeCalculationType(capitalizedIncomeCalculationType)
                .setCapitalizedIncomeStrategy(capitalizedIncomeStrategy).setFactorRateLoanAmount(factorRateLoanAmount);
    }
}
