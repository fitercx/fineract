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

package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.repository.LineOfCreditRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Service
@Slf4j
public class LineOfCreditWritePlatformServiceImpl implements LineOfCreditWritePlatformService {

    private final PlatformSecurityContext context;
    private final LineOfCreditRepository lineOfCreditRepository;
    private final ClientRepositoryWrapper clientRepository;
    private final LineOfCreditDataValidator dataValidator;

    @Autowired
    public LineOfCreditWritePlatformServiceImpl(PlatformSecurityContext context,
                                                LineOfCreditRepository lineOfCreditRepository,
                                                ClientRepositoryWrapper clientRepository,
                                                LineOfCreditDataValidator dataValidator) {
        this.context = context;
        this.lineOfCreditRepository = lineOfCreditRepository;
        this.clientRepository = clientRepository;
        this.dataValidator = dataValidator;
    }

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

            return CommandProcessingResult.resourceResult(savedLineOfCredit.getClient().getId());

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
                                                                             "Line of credit not found", "lineOfCreditId"));

            final Map<String, Object> changes = lineOfCredit.update(command);

            if (!changes.isEmpty()) {
                this.lineOfCreditRepository.save(lineOfCredit);
            }

            return CommandProcessingResult.resourceResult(lineOfCreditId, command.commandId(), changes);

        } catch (final Exception e) {
            log.error("Error occurred while updating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.update.failed", 
                                                        "Line of credit update failed", "lineOfCreditId");
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult activateLineOfCredit(Long lineOfCreditId, JsonCommand command) {
        try {
            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.line.of.credit.not.found", 
                                                                             "Line of credit not found", "lineOfCreditId"));

            lineOfCredit.activate();
            this.lineOfCreditRepository.save(lineOfCredit);

            return CommandProcessingResult.resourceResult(lineOfCreditId);

        } catch (final Exception e) {
            log.error("Error occurred while activating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.activation.failed", 
                                                        "Line of credit activation failed", "lineOfCreditId");
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult deactivateLineOfCredit(Long lineOfCreditId, JsonCommand command) {
        try {
            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.line.of.credit.not.found", 
                                                                             "Line of credit not found", "lineOfCreditId"));

            lineOfCredit.deactivate();
            this.lineOfCreditRepository.save(lineOfCredit);

            return CommandProcessingResult.resourceResult(lineOfCreditId);

        } catch (final Exception e) {
            log.error("Error occurred while deactivating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.deactivation.failed", 
                                                        "Line of credit deactivation failed", "lineOfCreditId");
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteLineOfCredit(Long lineOfCreditId) {
        try {
            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.line.of.credit.not.found", 
                                                                             "Line of credit not found", "lineOfCreditId"));

            this.lineOfCreditRepository.delete(lineOfCredit);

            return CommandProcessingResult.resourceResult(lineOfCreditId);

        } catch (final Exception e) {
            log.error("Error occurred while deleting line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.deletion.failed", 
                                                        "Line of credit deletion failed", "lineOfCreditId");
        }
    }
} 