/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportData;
import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportRowData;
import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportCalculator.Period;
import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportCalculator.Totals;
import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportCalculator.Transaction;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementData;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.data.RepaymentScheduleRelatedLoanData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the loan Accrual Report (Actual + Forecast, LMS-182/LMS-185) for the accrual-report endpoint and the nightly
 * S3 export. Inputs are read through the same read services as GET
 * /loans/{id}?associations=repaymentSchedule,transactions so the figures match the frontend download; see
 * {@link AccrualReportCalculator}.
 */
@Service
@RequiredArgsConstructor
public class LoanMonthlyAccrualReportReadPlatformService {

    private final LoanReadPlatformService loanReadPlatformService;

    @Transactional(readOnly = true)
    public AccrualReportData retrieveAccrualReport(final Long loanId, final LocalDate businessDate) {
        final LoanAccountData loan = loanReadPlatformService.retrieveOne(loanId);
        final boolean forecastEnabled = AccrualReportCalculator.isForecastEnabled(loan.getStatus(), loan.getChargedOff());
        final List<Period> periods = retrieveSchedulePeriods(loanId, loan);

        final LocalDate startDate = resolveStartDate(loan, periods);
        final LocalDate maturityDate = periods.isEmpty() ? null : periods.get(periods.size() - 1).dueDate();
        if (startDate == null || maturityDate == null) {
            return new AccrualReportData(loanId, businessDate, forecastEnabled, List.of(), "0.00", forecastEnabled ? "0.00" : "", "0.00");
        }

        final List<Transaction> transactions = loanReadPlatformService.retrieveLoanTransactions(loanId, false).stream()
                .map(LoanMonthlyAccrualReportReadPlatformService::toTransaction).toList();
        final List<AccrualReportRowData> rows = AccrualReportCalculator.computeMonthlyRows(
                new AccrualReportCalculator.Input(periods, transactions, startDate, maturityDate, businessDate, forecastEnabled));
        final Totals totals = AccrualReportCalculator.computeTotals(rows, forecastEnabled);
        return new AccrualReportData(loanId, businessDate, forecastEnabled, rows, totals.interestAccruedActual(), totals.interestForecast(),
                totals.lifetimeInterest());
    }

    private List<Period> retrieveSchedulePeriods(final Long loanId, final LoanAccountData loan) {
        final RepaymentScheduleRelatedLoanData repaymentScheduleRelatedData = new RepaymentScheduleRelatedLoanData(
                loan.getTimeline().getExpectedDisbursementDate(), loan.getTimeline().getActualDisbursementDate(), loan.getCurrency(),
                loan.getPrincipal(), loan.getInArrearsTolerance(), loan.getFeeChargesAtDisbursementCharged());
        final Collection<DisbursementData> disbursementData = loanReadPlatformService.retrieveLoanDisbursementDetails(loanId);
        final LoanScheduleData schedule = loanReadPlatformService.retrieveRepaymentSchedule(loanId, repaymentScheduleRelatedData,
                disbursementData, loan.isInterestRecalculationEnabled(), LoanScheduleType.fromEnumOptionData(loan.getLoanScheduleType()));
        if (schedule == null || schedule.getPeriods() == null) {
            return List.of();
        }
        return schedule.getPeriods().stream().map(p -> new Period(p.getPeriod(), p.getFromDate(), p.getDueDate(), p.getInterestDue(),
                p.getPrincipalDisbursed(), p.getPrincipalDue(), p.getPrincipalLoanBalanceOutstanding())).toList();
    }

    /** Disbursal date, else the first period's fromDate, else its dueDate (as the frontend download). */
    private static LocalDate resolveStartDate(final LoanAccountData loan, final List<Period> periods) {
        final LocalDate actualDisbursementDate = loan.getTimeline() == null ? null : loan.getTimeline().getActualDisbursementDate();
        if (actualDisbursementDate != null) {
            return actualDisbursementDate;
        }
        if (periods.isEmpty()) {
            return null;
        }
        return periods.get(0).fromDate() != null ? periods.get(0).fromDate() : periods.get(0).dueDate();
    }

    private static Transaction toTransaction(final LoanTransactionData transaction) {
        final String typeCode = transaction.getType() == null ? null : transaction.getType().getCode();
        final boolean typeAccrual = transaction.getType() != null && transaction.getType().isAccrual();
        return new Transaction(transaction.getDate(), transaction.isReversed(), typeCode, typeAccrual, transaction.getInterestPortion());
    }
}
