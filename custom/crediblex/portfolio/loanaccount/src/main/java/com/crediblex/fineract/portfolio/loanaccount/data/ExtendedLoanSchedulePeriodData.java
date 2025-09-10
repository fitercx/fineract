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

package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;

public class ExtendedLoanSchedulePeriodData extends LoanSchedulePeriodData {

    public enum Status {
        DISBURSEMENT, SCHEDULED, DUE, OVERDUE, LATE_FEE_APPLIED, PAID, PARTIAL_PAID
    }

    public final Status status;

    public static LoanSchedulePeriodData paymentsSummaryPeriod(
            final Integer periodNumber,
            final LocalDate dueDate,
            final Boolean isComplete,
            final BigDecimal principalDue,
            final BigDecimal penaltyChargesDue,
            final BigDecimal totalPaidForPeriod,
            final BigDecimal totalOutstandingForPeriod,
            final BigDecimal interestOutstanding) {

        return builder()
                .period(periodNumber)
                .dueDate(dueDate)
                .complete(isComplete)
                .principalDue(principalDue)
                .penaltyChargesDue(penaltyChargesDue)
                .totalPaidForPeriod(totalPaidForPeriod)
                .totalOutstandingForPeriod(totalOutstandingForPeriod)
                .interestOutstanding(interestOutstanding)
                .build();
    }


    public ExtendedLoanSchedulePeriodData(Integer period, LocalDate fromDate, LocalDate dueDate, LocalDate obligationsMetOnDate,
            Boolean complete, Integer daysInPeriod, BigDecimal principalDisbursed, BigDecimal principalOriginalDue, BigDecimal principalDue,
            BigDecimal principalPaid, BigDecimal principalWrittenOff, BigDecimal principalOutstanding,
            BigDecimal principalLoanBalanceOutstanding, BigDecimal interestOriginalDue, BigDecimal interestDue, BigDecimal interestPaid,
            BigDecimal interestWaived, BigDecimal interestWrittenOff, BigDecimal interestOutstanding, BigDecimal feeChargesDue,
            BigDecimal feeChargesPaid, BigDecimal feeChargesWaived, BigDecimal feeChargesWrittenOff, BigDecimal feeChargesOutstanding,
            BigDecimal penaltyChargesDue, BigDecimal penaltyChargesPaid, BigDecimal penaltyChargesWaived,
            BigDecimal penaltyChargesWrittenOff, BigDecimal penaltyChargesOutstanding, BigDecimal totalOriginalDueForPeriod,
            BigDecimal totalDueForPeriod, BigDecimal totalPaidForPeriod, BigDecimal totalPaidInAdvanceForPeriod,
            BigDecimal totalPaidLateForPeriod, BigDecimal totalWaivedForPeriod, BigDecimal totalWrittenOffForPeriod,
            BigDecimal totalOutstandingForPeriod, BigDecimal totalOverdue, BigDecimal totalActualCostOfLoanForPeriod,
            BigDecimal totalInstallmentAmountForPeriod, BigDecimal totalCredits, BigDecimal totalAccruedInterest, boolean downPaymentPeriod,
            Status status) {
        super(period, fromDate, dueDate, obligationsMetOnDate, complete, daysInPeriod, principalDisbursed, principalOriginalDue,
                principalDue, principalPaid, principalWrittenOff, principalOutstanding, principalLoanBalanceOutstanding,
                interestOriginalDue, interestDue, interestPaid, interestWaived, interestWrittenOff, interestOutstanding, feeChargesDue,
                feeChargesPaid, feeChargesWaived, feeChargesWrittenOff, feeChargesOutstanding, penaltyChargesDue, penaltyChargesPaid,
                penaltyChargesWaived, penaltyChargesWrittenOff, penaltyChargesOutstanding, totalOriginalDueForPeriod, totalDueForPeriod,
                totalPaidForPeriod, totalPaidInAdvanceForPeriod, totalPaidLateForPeriod, totalWaivedForPeriod, totalWrittenOffForPeriod,
                totalOutstandingForPeriod, totalOverdue, totalActualCostOfLoanForPeriod, totalInstallmentAmountForPeriod, totalCredits,
                totalAccruedInterest, downPaymentPeriod);
        this.status = status;
    }

    public ExtendedLoanSchedulePeriodData(LoanSchedulePeriodData periodData, Status status) {
        this(periodData.getPeriod(), periodData.getFromDate(), periodData.getDueDate(), periodData.getObligationsMetOnDate(),
                periodData.getComplete(), periodData.getDaysInPeriod(), periodData.getPrincipalDisbursed(),
                periodData.getPrincipalOriginalDue(), periodData.getPrincipalDue(), periodData.getPrincipalPaid(),
                periodData.getPrincipalWrittenOff(), periodData.getPrincipalOutstanding(), periodData.getPrincipalLoanBalanceOutstanding(),
                periodData.getInterestOriginalDue(), periodData.getInterestDue(), periodData.getInterestPaid(),
                periodData.getInterestWaived(), periodData.getInterestWrittenOff(), periodData.getInterestOutstanding(),
                periodData.getFeeChargesDue(), periodData.getFeeChargesPaid(), periodData.getFeeChargesWaived(),
                periodData.getFeeChargesWrittenOff(), periodData.getFeeChargesOutstanding(), periodData.getPenaltyChargesDue(),
                periodData.getPenaltyChargesPaid(), periodData.getPenaltyChargesWaived(), periodData.getPenaltyChargesWrittenOff(),
                periodData.getPenaltyChargesOutstanding(), periodData.getTotalOriginalDueForPeriod(), periodData.getTotalDueForPeriod(),
                periodData.getTotalPaidForPeriod(), periodData.getTotalPaidInAdvanceForPeriod(), periodData.getTotalPaidLateForPeriod(),
                periodData.getTotalWaivedForPeriod(), periodData.getTotalWrittenOffForPeriod(), periodData.getTotalOutstandingForPeriod(),
                periodData.getTotalOverdue(), periodData.getTotalActualCostOfLoanForPeriod(),
                periodData.getTotalInstallmentAmountForPeriod(), periodData.getTotalCredits(), periodData.getTotalAccruedInterest(),
                periodData.isDownPaymentPeriod(), status);
    }

}
