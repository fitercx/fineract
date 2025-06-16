package com.crediblex.fineract.portfolio.loan.data;

import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;

import java.math.BigDecimal;
import java.time.LocalDate;


public class ExtendedLoanSchedulePeriodData extends LoanSchedulePeriodData {

    public enum Status {
        DISBURSEMENT,SCHEDULED, DUE, OVERDUE, LATE_FEE_APPLIED, PAID, PARTIAL_PAID
    }

    private final Status status;

    public ExtendedLoanSchedulePeriodData(Integer period,
                                          LocalDate fromDate,
                                          LocalDate dueDate,
                                          LocalDate obligationsMetOnDate,
                                          Boolean complete,
                                          Integer daysInPeriod,
                                          BigDecimal principalDisbursed,
                                          BigDecimal principalOriginalDue,
                                          BigDecimal principalDue,
                                          BigDecimal principalPaid,
                                          BigDecimal principalWrittenOff,
                                          BigDecimal principalOutstanding,
                                          BigDecimal principalLoanBalanceOutstanding,
                                          BigDecimal interestOriginalDue,
                                          BigDecimal interestDue,
                                          BigDecimal interestPaid,
                                          BigDecimal interestWaived,
                                          BigDecimal interestWrittenOff,
                                          BigDecimal interestOutstanding,
                                          BigDecimal feeChargesDue,
                                          BigDecimal feeChargesPaid,
                                          BigDecimal feeChargesWaived,
                                          BigDecimal feeChargesWrittenOff,
                                          BigDecimal feeChargesOutstanding,
                                          BigDecimal penaltyChargesDue,
                                          BigDecimal penaltyChargesPaid,
                                          BigDecimal penaltyChargesWaived,
                                          BigDecimal penaltyChargesWrittenOff,
                                          BigDecimal penaltyChargesOutstanding,
                                          BigDecimal totalOriginalDueForPeriod,
                                          BigDecimal totalDueForPeriod,
                                          BigDecimal totalPaidForPeriod,
                                          BigDecimal totalPaidInAdvanceForPeriod,
                                          BigDecimal totalPaidLateForPeriod,
                                          BigDecimal totalWaivedForPeriod,
                                          BigDecimal totalWrittenOffForPeriod,
                                          BigDecimal totalOutstandingForPeriod,
                                          BigDecimal totalOverdue,
                                          BigDecimal totalActualCostOfLoanForPeriod,
                                          BigDecimal totalInstallmentAmountForPeriod,
                                          BigDecimal totalCredits,
                                          BigDecimal totalAccruedInterest,
                                          boolean downPaymentPeriod, Status status) {
        super(period, fromDate, dueDate, obligationsMetOnDate, complete, daysInPeriod, principalDisbursed,
                principalOriginalDue, principalDue, principalPaid, principalWrittenOff, principalOutstanding,
                principalLoanBalanceOutstanding, interestOriginalDue, interestDue, interestPaid, interestWaived,
                interestWrittenOff, interestOutstanding, feeChargesDue, feeChargesPaid, feeChargesWaived,
                feeChargesWrittenOff, feeChargesOutstanding, penaltyChargesDue, penaltyChargesPaid,
                penaltyChargesWaived, penaltyChargesWrittenOff, penaltyChargesOutstanding, totalOriginalDueForPeriod,
                totalDueForPeriod, totalPaidForPeriod, totalPaidInAdvanceForPeriod, totalPaidLateForPeriod,
                totalWaivedForPeriod, totalWrittenOffForPeriod, totalOutstandingForPeriod, totalOverdue,
                totalActualCostOfLoanForPeriod, totalInstallmentAmountForPeriod, totalCredits,
                totalAccruedInterest, downPaymentPeriod);
        this.status = status;
    }

    public ExtendedLoanSchedulePeriodData(LoanSchedulePeriodData periodData, Status status) {
        this(
                periodData.getPeriod(),
                periodData.getFromDate(),
                periodData.getDueDate(),
                periodData.getObligationsMetOnDate(),
                periodData.getComplete(),
                periodData.getDaysInPeriod(),
                periodData.getPrincipalDisbursed(),
                periodData.getPrincipalOriginalDue(),
                periodData.getPrincipalDue(),
                periodData.getPrincipalPaid(),
                periodData.getPrincipalWrittenOff(),
                periodData.getPrincipalOutstanding(),
                periodData.getPrincipalLoanBalanceOutstanding(),
                periodData.getInterestOriginalDue(),
                periodData.getInterestDue(),
                periodData.getInterestPaid(),
                periodData.getInterestWaived(),
                periodData.getInterestWrittenOff(),
                periodData.getInterestOutstanding(),
                periodData.getFeeChargesDue(),
                periodData.getFeeChargesPaid(),
                periodData.getFeeChargesWaived(),
                periodData.getFeeChargesWrittenOff(),
                periodData.getFeeChargesOutstanding(),
                periodData.getPenaltyChargesDue(),
                periodData.getPenaltyChargesPaid(),
                periodData.getPenaltyChargesWaived(),
                periodData.getPenaltyChargesWrittenOff(),
                periodData.getPenaltyChargesOutstanding(),
                periodData.getTotalOriginalDueForPeriod(),
                periodData.getTotalDueForPeriod(),
                periodData.getTotalPaidForPeriod(),
                periodData.getTotalPaidInAdvanceForPeriod(),
                periodData.getTotalPaidLateForPeriod(),
                periodData.getTotalWaivedForPeriod(),
                periodData.getTotalWrittenOffForPeriod(),
                periodData.getTotalOutstandingForPeriod(),
                periodData.getTotalOverdue(),
                periodData.getTotalActualCostOfLoanForPeriod(),
                periodData.getTotalInstallmentAmountForPeriod(),
                periodData.getTotalCredits(),
                periodData.getTotalAccruedInterest(),
                periodData.isDownPaymentPeriod(), status
        );
    }


}
