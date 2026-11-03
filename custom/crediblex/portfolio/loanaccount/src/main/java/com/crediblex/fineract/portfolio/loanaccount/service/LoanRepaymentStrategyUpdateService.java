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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;
import org.apache.fineract.portfolio.loanproduct.data.TransactionProcessingStrategyData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the repayment strategy stored on an active loan. Posted transactions are left as allocated. The command is
 * maker-checker enabled, so a checker approves it before this method's write is committed when maker-checker is on.
 */
@Service
@RequiredArgsConstructor
public class LoanRepaymentStrategyUpdateService {

    public static final String TRANSACTION_PROCESSING_STRATEGY_CODE = "transactionProcessingStrategyCode";
    public static final String NOTE = "note";
    static final int MAX_NOTE_LENGTH = 500;

    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanRepaymentScheduleTransactionProcessorFactory processorFactory;

    @Transactional(readOnly = true)
    public Map<String, Object> template(final Long loanId) {
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
        final String currentCode = loan.getTransactionProcessingStrategyCode();
        final Map<String, Object> template = new LinkedHashMap<>();
        template.put("transactionProcessingStrategyCode", currentCode);
        template.put("transactionProcessingStrategyName", loan.getTransactionProcessingStrategyName());
        template.put("transactionProcessingStrategyOptions", selectableStrategies());
        if (isAdvancedAllocation(currentCode)) {
            template.put("unsupportedReason",
                    "This loan uses advanced payment allocation. Its repayment strategy cannot be changed from here.");
        } else if (!loan.isOpen()) {
            template.put("unsupportedReason", "Repayment strategy can be changed only while the loan is active.");
        }
        return template;
    }

    @Transactional
    public CommandProcessingResult update(final Long loanId, final JsonCommand command) {
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
        if (!loan.isOpen()) {
            throw validationError("validation.msg.loan.repayment.strategy.not.active",
                    "Repayment strategy can be changed only while the loan is active.", TRANSACTION_PROCESSING_STRATEGY_CODE);
        }

        final String requestedCode = command.stringValueOfParameterNamed(TRANSACTION_PROCESSING_STRATEGY_CODE);
        if (StringUtils.isBlank(requestedCode)) {
            throw validationError("validation.msg.loan.repayment.strategy.required", "Repayment strategy is required.",
                    TRANSACTION_PROCESSING_STRATEGY_CODE);
        }

        final String currentCode = loan.getTransactionProcessingStrategyCode();
        if (isAdvancedAllocation(currentCode) || isAdvancedAllocation(requestedCode)) {
            throw validationError("validation.msg.loan.repayment.strategy.advanced.allocation",
                    "Advanced payment allocation cannot be selected or replaced by this change.", TRANSACTION_PROCESSING_STRATEGY_CODE);
        }

        final TransactionProcessingStrategyData strategy = findStrategy(requestedCode);
        if (strategy.getCode().equalsIgnoreCase(currentCode)) {
            throw validationError("validation.msg.loan.repayment.strategy.unchanged", "The loan already uses this repayment strategy.",
                    TRANSACTION_PROCESSING_STRATEGY_CODE);
        }

        final String note = StringUtils.trimToNull(command.stringValueOfParameterNamedAllowingNull(NOTE));
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw validationError("validation.msg.loan.repayment.strategy.note.too.long",
                    "The note cannot be longer than " + MAX_NOTE_LENGTH + " characters.", NOTE);
        }

        final String previousCode = currentCode;
        final String previousName = loan.getTransactionProcessingStrategyName();
        loan.updateTransactionProcessingStrategy(strategy.getCode(), strategy.getName());
        this.loanRepositoryWrapper.saveAndFlush(loan);

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(TRANSACTION_PROCESSING_STRATEGY_CODE, strategy.getCode());
        changes.put("transactionProcessingStrategyName", strategy.getName());
        changes.put("previousTransactionProcessingStrategyCode", previousCode);
        changes.put("previousTransactionProcessingStrategyName", previousName);
        if (note != null) {
            changes.put(NOTE, note);
        }

        return new CommandProcessingResultBuilder().withCommandId(command.getCommandId()).withEntityId(loan.getId())
                .withLoanId(loan.getId()).with(changes).build();
    }

    private List<Map<String, String>> selectableStrategies() {
        final List<Map<String, String>> options = new ArrayList<>();
        for (final TransactionProcessingStrategyData strategy : this.processorFactory.getStrategies()) {
            if (strategy == null || isAdvancedAllocation(strategy.getCode())) {
                continue;
            }
            final Map<String, String> option = new LinkedHashMap<>();
            option.put("code", strategy.getCode());
            option.put("name", strategy.getName());
            options.add(option);
        }
        return options;
    }

    private TransactionProcessingStrategyData findStrategy(final String requestedCode) {
        for (final TransactionProcessingStrategyData strategy : this.processorFactory.getStrategies()) {
            if (strategy != null && requestedCode.equalsIgnoreCase(strategy.getCode())) {
                return strategy;
            }
        }
        throw validationError("validation.msg.loan.repayment.strategy.unknown",
                "Repayment strategy " + requestedCode + " is not supported.", TRANSACTION_PROCESSING_STRATEGY_CODE);
    }

    private static boolean isAdvancedAllocation(final String strategyCode) {
        return LoanProductConstants.ADVANCED_PAYMENT_ALLOCATION_STRATEGY.equalsIgnoreCase(strategyCode);
    }

    private static PlatformApiDataValidationException validationError(final String code, final String message, final String parameter) {
        return new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(code, message, parameter)));
    }
}
