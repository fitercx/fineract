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
package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanDpdStrategySwitch;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanDpdStrategySwitchRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.IntSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.springframework.stereotype.Service;

/**
 * Keeps a loan's stored repayment strategy in step with how far past due it is (LMS-139).
 *
 * <p>
 * A loan on a product that opted in is switched to "Principal, Interest, Penalties, Fees Order" once its max DPD
 * exceeds the configured threshold, so collections pay down principal first. Once it recovers to at or below the
 * threshold it is switched back to whatever strategy it had before, read from {@code m_loan_dpd_strategy_switch}.
 *
 * <p>
 * Evaluation is idempotent and only ever rewrites {@code m_loan.loan_transaction_strategy_code}. Already posted
 * transactions are never reversed or reallocated: the switch changes how <em>subsequent</em> money is applied.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DpdStrategySwitchService {

    private static final String SWITCHED_STRATEGY_CODE = PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE;
    private static final String SWITCHED_STRATEGY_NAME = PrincipalInterestPenaltyFeesOrderLoanRepaymentScheduleTransactionProcessor.STRATEGY_NAME;

    private final DpdStrategySwitchConfigService configService;
    private final DpdMaxDaysPastDueService maxDaysPastDueService;
    private final LoanDpdStrategySwitchRepository switchRepository;

    /**
     * Re-evaluates the switch for {@code loan} and returns the strategy code that should be used from this moment on.
     * The loan entity is updated in place when the state changes, so the new strategy applies to the transaction being
     * processed as well as to later ones.
     */
    public String resolveEffectiveStrategyCode(final Loan loan, final LocalDate asOfDate) {
        if (loan == null || loan.getId() == null) {
            return loan == null ? null : loan.getTransactionProcessingStrategyCode();
        }
        return evaluate(loan, asOfDate, () -> maxDaysPastDueService.calculateMaxDpd(loan.getId(), asOfDate));
    }

    /**
     * Re-evaluates the switch against the loan's <em>in-memory</em> schedule, for callers that run after the schedule
     * has already been changed within the current transaction: the COB business step, and the transaction processor
     * once it has finished allocating.
     *
     * <p>
     * Without this the revert always lags by one transaction, because a repayment that brings a loan current only
     * updates the schedule after the strategy has been resolved for that same repayment.
     */
    public String reevaluate(final Loan loan, final LocalDate asOfDate) {
        if (loan == null || loan.getId() == null) {
            return loan == null ? null : loan.getTransactionProcessingStrategyCode();
        }
        return evaluate(loan, asOfDate, () -> maxDaysPastDueService.calculateMaxDpd(loan, asOfDate));
    }

    private String evaluate(final Loan loan, final LocalDate asOfDate, final IntSupplier maxDpdSupplier) {
        final LoanProduct product = loan.loanProduct();
        final boolean optedIn = configService.isGloballyEnabled() && product != null && product.isEnableDpdStrategySwitch();
        if (!optedIn) {
            // Covers both the global kill switch and a product that was opted out after loans had already switched.
            return revertIfSwitched(loan, asOfDate);
        }

        final int threshold = configService.getThresholdDays();
        final int maxDpd = maxDpdSupplier.getAsInt();
        return maxDpd > threshold ? switchIfNeeded(loan, maxDpd, asOfDate) : revertIfSwitched(loan, asOfDate);
    }

    /** @return the current switch record for the loan, if it has ever been switched. */
    public Optional<LoanDpdStrategySwitch> findSwitchState(final Long loanId) {
        return loanId == null ? Optional.empty() : switchRepository.findByLoanId(loanId);
    }

    public int getThresholdDays() {
        return configService.getThresholdDays();
    }

    public int calculateMaxDpd(final Long loanId, final LocalDate asOfDate) {
        return maxDaysPastDueService.calculateMaxDpd(loanId, asOfDate);
    }

    private String switchIfNeeded(final Loan loan, final int maxDpd, final LocalDate asOfDate) {
        final Optional<LoanDpdStrategySwitch> existing = switchRepository.findByLoanId(loan.getId());

        if (existing.isPresent() && existing.get().isSwitched()) {
            // Already switched. Re-apply to the loan defensively in case the strategy was changed elsewhere, but never
            // overwrite the recorded original with the switched strategy.
            if (!SWITCHED_STRATEGY_CODE.equals(loan.getTransactionProcessingStrategyCode())) {
                loan.updateTransactionProcessingStrategy(SWITCHED_STRATEGY_CODE, SWITCHED_STRATEGY_NAME);
            }
            return SWITCHED_STRATEGY_CODE;
        }

        final String originalStrategyCode = loan.getTransactionProcessingStrategyCode();
        if (SWITCHED_STRATEGY_CODE.equals(originalStrategyCode)) {
            // The product already repays in this order, so there is nothing to switch and nothing to revert later.
            return SWITCHED_STRATEGY_CODE;
        }
        final String originalStrategyName = loan.getTransactionProcessingStrategyName();

        final LoanDpdStrategySwitch record = existing.orElse(null);
        if (record == null) {
            switchRepository.save(LoanDpdStrategySwitch.newSwitch(loan.getId(), originalStrategyCode, originalStrategyName,
                    SWITCHED_STRATEGY_CODE, maxDpd, asOfDate));
        } else {
            record.markSwitched(originalStrategyCode, originalStrategyName, SWITCHED_STRATEGY_CODE, maxDpd, asOfDate);
            switchRepository.save(record);
        }

        loan.updateTransactionProcessingStrategy(SWITCHED_STRATEGY_CODE, SWITCHED_STRATEGY_NAME);
        log.info("DPD strategy switch applied to loan {}: {} -> {} (max DPD {})", loan.getId(), originalStrategyCode,
                SWITCHED_STRATEGY_CODE, maxDpd);
        return SWITCHED_STRATEGY_CODE;
    }

    private String revertIfSwitched(final Loan loan, final LocalDate asOfDate) {
        final Optional<LoanDpdStrategySwitch> existing = switchRepository.findByLoanId(loan.getId());
        if (existing.isEmpty() || !existing.get().isSwitched()) {
            return loan.getTransactionProcessingStrategyCode();
        }

        final LoanDpdStrategySwitch record = existing.get();
        loan.updateTransactionProcessingStrategy(record.getOriginalStrategyCode(), record.getOriginalStrategyName());
        record.markReverted(asOfDate);
        switchRepository.save(record);
        log.info("DPD strategy switch reverted on loan {}: {} -> {}", loan.getId(), SWITCHED_STRATEGY_CODE,
                record.getOriginalStrategyCode());
        return record.getOriginalStrategyCode();
    }
}
