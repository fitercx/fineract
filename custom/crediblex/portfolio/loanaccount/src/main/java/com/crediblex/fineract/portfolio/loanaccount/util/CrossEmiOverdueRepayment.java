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
package com.crediblex.fineract.portfolio.loanaccount.util;

import com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;

/**
 * The cross-EMI strategy is only for catching up overdue installments: overdue principal, then overdue interest, then
 * overdue fees, then overdue LPI. It cannot be selected on a current loan, and a repayment cannot exceed what those
 * overdue installments still owe. Once they are clear, another repayment waits until the strategy is changed.
 */
public final class CrossEmiOverdueRepayment {

    public static final String REQUIRES_OVERDUE = "validation.msg.loan.repayment.strategy.cross.emi.requires.overdue";
    public static final String OVERDUE_CLEARED = "validation.msg.loan.repayment.cross.emi.overdue.cleared";
    public static final String AMOUNT_EXCEEDS_OVERDUE = "validation.msg.loan.repayment.cross.emi.amount.exceeds.overdue";

    public static final String REQUIRES_OVERDUE_MESSAGE = "This repayment strategy can be used only while the loan has overdue installments.";
    public static final String OVERDUE_CLEARED_MESSAGE = "Overdue installments are cleared. Change the repayment strategy before another repayment can be recorded.";
    public static final String AMOUNT_EXCEEDS_OVERDUE_MESSAGE = "The amount is greater than the overdue amount.";

    private CrossEmiOverdueRepayment() {}

    public static boolean isThisStrategy(final String strategyCode) {
        return StringUtils.equalsIgnoreCase(strategyCode,
                CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE);
    }

    public static boolean hasOverdueOutstanding(final Loan loan, final LocalDate asOf) {
        if (loan == null || loan.getCurrency() == null || asOf == null) {
            return false;
        }
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        if (installments == null) {
            return false;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (!CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor.isOverdue(installment, asOf)) {
                continue;
            }
            if (installment.getPrincipalOutstanding(currency).isGreaterThanZero()
                    || installment.getInterestOutstanding(currency).isGreaterThanZero()
                    || installment.getFeeChargesOutstanding(currency).isGreaterThanZero()
                    || installment.getTaxChargesOutstanding(currency).isGreaterThanZero()
                    || installment.getPenaltyChargesOutstanding(currency).isGreaterThanZero()) {
                return true;
            }
        }
        return false;
    }

    public static Money overdueOutstanding(final Loan loan, final LocalDate asOf) {
        final MonetaryCurrency currency = loan.getCurrency();
        Money total = Money.zero(currency);
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        if (installments == null || asOf == null) {
            return total;
        }
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (!CrossEmiPrincipalInterestFeesLoanRepaymentScheduleTransactionProcessor.isOverdue(installment, asOf)) {
                continue;
            }
            total = total.plus(installment.getPrincipalOutstanding(currency)).plus(installment.getInterestOutstanding(currency))
                    .plus(installment.getFeeChargesOutstanding(currency)).plus(installment.getTaxChargesOutstanding(currency))
                    .plus(installment.getPenaltyChargesOutstanding(currency));
        }
        return total;
    }

    public static void validateCanSelect(final Loan loan, final LocalDate asOf) {
        if (!hasOverdueOutstanding(loan, asOf)) {
            throw error(REQUIRES_OVERDUE, REQUIRES_OVERDUE_MESSAGE, "transactionProcessingStrategyCode");
        }
    }

    public static void validateRepayment(final Loan loan, final LocalDate transactionDate, final BigDecimal amount) {
        if (loan == null || !isThisStrategy(loan.getTransactionProcessingStrategyCode()) || amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        final Money overdue = overdueOutstanding(loan, transactionDate);
        if (!overdue.isGreaterThanZero()) {
            throw error(OVERDUE_CLEARED, OVERDUE_CLEARED_MESSAGE, "transactionAmount");
        }
        final Money payment = Money.of(loan.getCurrency(), amount);
        if (payment.isGreaterThan(overdue)) {
            throw error(AMOUNT_EXCEEDS_OVERDUE, AMOUNT_EXCEEDS_OVERDUE_MESSAGE, "transactionAmount");
        }
    }

    private static PlatformApiDataValidationException error(final String code, final String message, final String parameter) {
        return new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(code, message, parameter)));
    }
}
