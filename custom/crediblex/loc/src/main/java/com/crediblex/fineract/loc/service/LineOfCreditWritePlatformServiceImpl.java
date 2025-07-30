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

package com.crediblex.fineract.loc.service;

import com.crediblex.fineract.loc.domain.LineOfCredit;
import com.crediblex.fineract.loc.domain.LineOfCreditTransaction;
import com.crediblex.fineract.loc.repository.LineOfCreditRepository;
import com.crediblex.fineract.loc.repository.LineOfCreditTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LineOfCreditWritePlatformServiceImpl implements LineOfCreditWritePlatformService {

    private final PlatformSecurityContext context;
    private final LineOfCreditRepository lineOfCreditRepository;
    private final LineOfCreditTransactionRepository transactionRepository;
    private final ClientRepositoryWrapper clientRepository;
    private final LineOfCreditDataValidator dataValidator;

    @Override
    @Transactional
    public CommandProcessingResult createLineOfCredit(JsonCommand command) {
        try {
            this.dataValidator.validateForCreate(command.json());

            final Long clientId = command.longValueOfParameterNamed("clientId");
            final Client client = this.clientRepository.findOneWithNotFoundDetection(clientId);

            final String name = command.stringValueOfParameterNamed("name");
            final String productType = command.stringValueOfParameterNamed("productType");
            final BigDecimal maximumAmount = command.bigDecimalValueOfParameterNamed("maximumAmount");
            final LocalDate startDate = command.localDateValueOfParameterNamed("startDate");
            final LocalDate endDate = command.localDateValueOfParameterNamed("endDate");

            final LineOfCredit lineOfCredit = new LineOfCredit(client, name, productType, maximumAmount, startDate, endDate);
            final LineOfCredit savedLineOfCredit = this.lineOfCreditRepository.save(lineOfCredit);

            // Log activation transaction
            logTransaction(savedLineOfCredit, LineOfCreditTransaction.TransactionType.ACTIVATION, 
                          BigDecimal.ZERO, maximumAmount, maximumAmount, "Initial activation", null);

            return CommandProcessingResult.resourceResult(savedLineOfCredit.getId());

        } catch (final Exception e) {
            log.error("Error occurred while creating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.creation.failed", 
                                                        "Line of credit creation failed", e.getMessage());
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult updateLineOfCredit(Long lineOfCreditId, JsonCommand command) {
        try {
            this.dataValidator.validateForUpdate(command.json());

            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.line.of.credit.not.found", 
                                                                             "Line of credit not found", lineOfCreditId));

            final Map<String, Object> changes = lineOfCredit.update(command);

            if (!changes.isEmpty()) {
                this.lineOfCreditRepository.save(lineOfCredit);
            }

            return CommandProcessingResult.resourceResult(lineOfCreditId, changes);

        } catch (final Exception e) {
            log.error("Error occurred while updating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.update.failed", 
                                                        "Line of credit update failed", e.getMessage());
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult activateLineOfCredit(Long lineOfCreditId, JsonCommand command) {
        try {
            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.line.of.credit.not.found", 
                                                                             "Line of credit not found", lineOfCreditId));

            lineOfCredit.activate();
            this.lineOfCreditRepository.save(lineOfCredit);

            // Log activation transaction
            logTransaction(lineOfCredit, LineOfCreditTransaction.TransactionType.ACTIVATION, 
                          BigDecimal.ZERO, lineOfCredit.getAvailableBalance(), lineOfCredit.getAvailableBalance(), 
                          "Line of credit activated", null);

            return CommandProcessingResult.resourceResult(lineOfCreditId);

        } catch (final Exception e) {
            log.error("Error occurred while activating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.activation.failed", 
                                                        "Line of credit activation failed", e.getMessage());
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult deactivateLineOfCredit(Long lineOfCreditId, JsonCommand command) {
        try {
            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.line.of.credit.not.found", 
                                                                             "Line of credit not found", lineOfCreditId));

            lineOfCredit.deactivate();
            this.lineOfCreditRepository.save(lineOfCredit);

            // Log deactivation transaction
            logTransaction(lineOfCredit, LineOfCreditTransaction.TransactionType.DEACTIVATION, 
                          BigDecimal.ZERO, lineOfCredit.getAvailableBalance(), lineOfCredit.getAvailableBalance(), 
                          "Line of credit deactivated", null);

            return CommandProcessingResult.resourceResult(lineOfCreditId);

        } catch (final Exception e) {
            log.error("Error occurred while deactivating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.deactivation.failed", 
                                                        "Line of credit deactivation failed", e.getMessage());
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteLineOfCredit(Long lineOfCreditId) {
        try {
            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.line.of.credit.not.found", 
                                                                             "Line of credit not found", lineOfCreditId));

            this.lineOfCreditRepository.delete(lineOfCredit);

            return CommandProcessingResult.resourceResult(lineOfCreditId);

        } catch (final Exception e) {
            log.error("Error occurred while deleting line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.deletion.failed", 
                                                        "Line of credit deletion failed", e.getMessage());
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult drawAmount(Long lineOfCreditId, JsonCommand command) {
        try {
            this.dataValidator.validateForDraw(command.json());

            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.line.of.credit.not.found", 
                                                                             "Line of credit not found", lineOfCreditId));

            final BigDecimal amount = command.bigDecimalValueOfParameterNamed("amount");
            final String referenceNumber = command.stringValueOfParameterNamed("referenceNumber");
            final String description = command.stringValueOfParameterNamed("description");

            final BigDecimal balanceBefore = lineOfCredit.getAvailableBalance();
            lineOfCredit.drawAmount(amount);
            this.lineOfCreditRepository.save(lineOfCredit);

            // Log draw transaction
            logTransaction(lineOfCredit, LineOfCreditTransaction.TransactionType.DRAW, 
                          amount, balanceBefore, lineOfCredit.getAvailableBalance(), 
                          description, referenceNumber);

            return CommandProcessingResult.resourceResult(lineOfCreditId);

        } catch (final Exception e) {
            log.error("Error occurred while drawing amount from line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.draw.failed", 
                                                        "Line of credit draw failed", e.getMessage());
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult repayAmount(Long lineOfCreditId, JsonCommand command) {
        try {
            this.dataValidator.validateForRepayment(command.json());

            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.line.of.credit.not.found", 
                                                                             "Line of credit not found", lineOfCreditId));

            final BigDecimal amount = command.bigDecimalValueOfParameterNamed("amount");
            final String referenceNumber = command.stringValueOfParameterNamed("referenceNumber");
            final String description = command.stringValueOfParameterNamed("description");

            final BigDecimal balanceBefore = lineOfCredit.getAvailableBalance();
            lineOfCredit.repayAmount(amount);
            this.lineOfCreditRepository.save(lineOfCredit);

            // Log repayment transaction
            logTransaction(lineOfCredit, LineOfCreditTransaction.TransactionType.REPAYMENT, 
                          amount, balanceBefore, lineOfCredit.getAvailableBalance(), 
                          description, referenceNumber);

            return CommandProcessingResult.resourceResult(lineOfCreditId);

        } catch (final Exception e) {
            log.error("Error occurred while repaying amount to line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.repayment.failed", 
                                                        "Line of credit repayment failed", e.getMessage());
        }
    }

    private void logTransaction(LineOfCredit lineOfCredit, LineOfCreditTransaction.TransactionType transactionType,
                               BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                               String description, String referenceNumber) {
        final LineOfCreditTransaction transaction = new LineOfCreditTransaction(
                lineOfCredit, transactionType, amount, balanceBefore, balanceAfter, referenceNumber, description);
        this.transactionRepository.save(transaction);
    }
} 