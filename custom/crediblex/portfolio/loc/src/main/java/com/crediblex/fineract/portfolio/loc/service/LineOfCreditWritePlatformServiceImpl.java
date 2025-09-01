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

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.repository.LineOfCreditRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class LineOfCreditWritePlatformServiceImpl implements LineOfCreditWritePlatformService {

    private final PlatformSecurityContext context;
    private final LineOfCreditRepository lineOfCreditRepository;
    private final ClientRepositoryWrapper clientRepository;
    private final LineOfCreditDataValidator dataValidator;

    @Autowired
    public LineOfCreditWritePlatformServiceImpl(PlatformSecurityContext context, LineOfCreditRepository lineOfCreditRepository,
            ClientRepositoryWrapper clientRepository, LineOfCreditDataValidator dataValidator) {
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
            final BigDecimal approvedCreditFacilityAmount = command.hasParameter("approvedCreditFacilityAmount") ? 
                command.bigDecimalValueOfParameterNamed("approvedCreditFacilityAmount") : null;
            final String externalId = command.hasParameter("externalId") ? 
                command.stringValueOfParameterNamed("externalId") : null;
            final LocalDate activationDate = command.hasParameter("activationDate") ? 
                command.localDateValueOfParameterNamed("activationDate") : null;
            final String currency = command.hasParameter("currency") ? 
                command.stringValueOfParameterNamed("currency") : null;
            final BigDecimal advancePercentage = command.hasParameter("advancePercentage") ? 
                command.bigDecimalValueOfParameterNamed("advancePercentage") : null;
            final Integer tenorDays = command.hasParameter("tenorDays") ? 
                command.integerValueOfParameterNamed("tenorDays") : null;
            final String approvedBuyers = command.hasParameter("approvedBuyers") ? 
                command.stringValueOfParameterNamed("approvedBuyers") : null;
            final BigDecimal processingFeePctLoc = command.hasParameter("processingFeePctLoc") ? 
                command.bigDecimalValueOfParameterNamed("processingFeePctLoc") : null;
            final String cashMarginType = command.hasParameter("cashMarginType") ? 
                command.stringValueOfParameterNamed("cashMarginType") : null;
            final BigDecimal cashMarginValue = command.hasParameter("cashMarginValue") ? 
                command.bigDecimalValueOfParameterNamed("cashMarginValue") : null;
            final String invHandlingFeeBasis = command.hasParameter("invHandlingFeeBasis") ? 
                command.stringValueOfParameterNamed("invHandlingFeeBasis") : null;
            final BigDecimal invHandlingFeePct = command.hasParameter("invHandlingFeePct") ? 
                command.bigDecimalValueOfParameterNamed("invHandlingFeePct") : null;
            final BigDecimal invHandlingFeeMinAmount = command.hasParameter("invHandlingFeeMinAmount") ? 
                command.bigDecimalValueOfParameterNamed("invHandlingFeeMinAmount") : null;
            final String invHandlingFeeCurrency = command.hasParameter("invHandlingFeeCurrency") ? 
                command.stringValueOfParameterNamed("invHandlingFeeCurrency") : null;
            final LocalDate interimReviewDate = command.hasParameter("interimReviewDate") ? 
                command.localDateValueOfParameterNamed("interimReviewDate") : null;
            final String rateType = command.hasParameter("rateType") ? 
                command.stringValueOfParameterNamed("rateType") : null;
            final BigDecimal annualInterestRate = command.hasParameter("annualInterestRate") ? 
                command.bigDecimalValueOfParameterNamed("annualInterestRate") : null;
            final String isInterestUpfrontOrPostDisbursal = command.hasParameter("isInterestUpfrontOrPostDisbursal") ? 
                command.stringValueOfParameterNamed("isInterestUpfrontOrPostDisbursal") : null;
            final String clientCompanyName = command.hasParameter("clientCompanyName") ? 
                command.stringValueOfParameterNamed("clientCompanyName") : null;
            final String clientContactPersonName = command.hasParameter("clientContactPersonName") ? 
                command.stringValueOfParameterNamed("clientContactPersonName") : null;
            final String clientContactPersonPhone = command.hasParameter("clientContactPersonPhone") ? 
                command.stringValueOfParameterNamed("clientContactPersonPhone") : null;
            final String clientContactPersonEmail = command.hasParameter("clientContactPersonEmail") ? 
                command.stringValueOfParameterNamed("clientContactPersonEmail") : null;
            final String authorizedSignatoryName = command.hasParameter("authorizedSignatoryName") ? 
                command.stringValueOfParameterNamed("authorizedSignatoryName") : null;
            final String authorizedSignatoryPhone = command.hasParameter("authorizedSignatoryPhone") ? 
                command.stringValueOfParameterNamed("authorizedSignatoryPhone") : null;
            final String authorizedSignatoryEmail = command.hasParameter("authorizedSignatoryEmail") ? 
                command.stringValueOfParameterNamed("authorizedSignatoryEmail") : null;
            final String va = command.hasParameter("va") ? 
                command.stringValueOfParameterNamed("va") : null;
            final String distributionPartner = command.hasParameter("distributionPartner") ? 
                command.stringValueOfParameterNamed("distributionPartner") : null;
            final BigDecimal bankTransferFee = command.hasParameter("bankTransferFee") ? 
                command.bigDecimalValueOfParameterNamed("bankTransferFee") : null;
            final String specialConditions = command.hasParameter("specialConditions") ? 
                command.stringValueOfParameterNamed("specialConditions") : null;
            final BigDecimal latePaymentFee = command.hasParameter("latePaymentFee") ? 
                command.bigDecimalValueOfParameterNamed("latePaymentFee") : null;

            final LineOfCredit lineOfCredit = new LineOfCredit(client, name, productType, maximumAmount, startDate, endDate,
                    approvedCreditFacilityAmount, externalId, activationDate, currency, advancePercentage, tenorDays, approvedBuyers,
                    processingFeePctLoc, cashMarginType, cashMarginValue, invHandlingFeeBasis, invHandlingFeePct, invHandlingFeeMinAmount,
                    invHandlingFeeCurrency, interimReviewDate, rateType, annualInterestRate, isInterestUpfrontOrPostDisbursal,
                    clientCompanyName, clientContactPersonName, clientContactPersonPhone, clientContactPersonEmail, authorizedSignatoryName,
                    authorizedSignatoryPhone, authorizedSignatoryEmail, va, distributionPartner, bankTransferFee, specialConditions, latePaymentFee);
            final LineOfCredit savedLineOfCredit = this.lineOfCreditRepository.save(lineOfCredit);

            return new CommandProcessingResultBuilder().withEntityId(savedLineOfCredit.getId()).build();
        } catch (final PlatformApiDataValidationException e) {
            // Re-throw validation exceptions as-is
            log.error("Validation error occurred: {}", e.getMessage());
            throw e;
        } catch (final Exception e) {
            log.error("Error occurred while creating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.creation.failed", "Line of credit creation failed",
                    e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult createLineOfCredit(LineOfCreditRequest request) {
        try {
            // Validate the request
            this.dataValidator.validateForCreate(request.toJson());

            // Get client
            final Client client = this.clientRepository.findOneWithNotFoundDetection(request.getClientId());

            // Convert LineOfCreditRequest to LineOfCredit entity
            final LineOfCredit lineOfCredit = convertRequestToEntity(request, client);

            // Save the entity
            final LineOfCredit savedLineOfCredit = this.lineOfCreditRepository.save(lineOfCredit);

            return new CommandProcessingResultBuilder().withEntityId(savedLineOfCredit.getId()).build();
        } catch (final PlatformApiDataValidationException e) {
            // Re-throw validation exceptions as-is
            log.error("Validation error occurred: {}", e.getMessage());
            throw e;
        } catch (final Exception e) {
            log.error("Error occurred while creating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.creation.failed", "Line of credit creation failed",
                    e.getMessage(), e);
        }
    }

    /**
     * Converts LineOfCreditRequest to LineOfCredit entity
     */
    private LineOfCredit convertRequestToEntity(LineOfCreditRequest request, Client client) {
        // Parse dates
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(request.getDateFormat(), Locale.forLanguageTag(request.getLocale()));
        final LocalDate startDate = LocalDate.parse(request.getStartDate(), formatter);
        final LocalDate endDate = LocalDate.parse(request.getEndDate(), formatter);
        
        // Parse activation date if provided
        LocalDate activationDate = null;
        if (request.getActivationDate() != null && !request.getActivationDate().trim().isEmpty()) {
            activationDate = LocalDate.parse(request.getActivationDate(), formatter);
        }
        
        // Parse interim review date if provided
        LocalDate interimReviewDate = null;
        if (request.getInterimReviewDate() != null && !request.getInterimReviewDate().trim().isEmpty()) {
            interimReviewDate = LocalDate.parse(request.getInterimReviewDate(), formatter);
        }

        // Convert string amounts to BigDecimal
        final BigDecimal maximumAmount = new BigDecimal(request.getMaximumAmount().replace(",", ""));
        final BigDecimal approvedCreditFacilityAmount = request.getApprovedCreditFacilityAmount() != null ? 
            new BigDecimal(request.getApprovedCreditFacilityAmount().replace(",", "")) : null;
        final BigDecimal advancePercentage = request.getAdvancePercentage() != null ? 
            new BigDecimal(request.getAdvancePercentage()) : null;
        final BigDecimal processingFeePctLoc = request.getProcessingFeePctLoc() != null ? 
            new BigDecimal(request.getProcessingFeePctLoc()) : null;
        final BigDecimal cashMarginValue = request.getCashMarginValue() != null ? 
            new BigDecimal(request.getCashMarginValue().replace(",", "")) : null;
        final BigDecimal invHandlingFeePct = request.getInvHandlingFeePct() != null ? 
            new BigDecimal(request.getInvHandlingFeePct()) : null;
        final BigDecimal invHandlingFeeMinAmount = request.getInvHandlingFeeMinAmount() != null ? 
            new BigDecimal(request.getInvHandlingFeeMinAmount().replace(",", "")) : null;
        final BigDecimal annualInterestRate = request.getAnnualInterestRate() != null ? 
            new BigDecimal(request.getAnnualInterestRate()) : null;
        final BigDecimal bankTransferFee = request.getBankTransferFee() != null ? 
            new BigDecimal(request.getBankTransferFee()) : null;
        final BigDecimal latePaymentFee = request.getLatePaymentFee() != null ? 
            new BigDecimal(request.getLatePaymentFee()) : null;

        // ProductType is already a string
        final String productType = request.getProductType();

        return new LineOfCredit(
            client, 
            request.getName(), 
            productType, 
            maximumAmount, 
            startDate, 
            endDate,
            approvedCreditFacilityAmount, 
            request.getExternalId(), 
            activationDate, 
            request.getCurrency(), 
            advancePercentage, 
            request.getTenorDays(), 
            request.getApprovedBuyers(),
            processingFeePctLoc, 
            request.getCashMarginType(), 
            cashMarginValue, 
            request.getInvHandlingFeeBasis(), 
            invHandlingFeePct, 
            invHandlingFeeMinAmount,
            request.getInvHandlingFeeCurrency(), 
            interimReviewDate, 
            request.getRateType(), 
            annualInterestRate, 
            request.getIsInterestUpfrontOrPostDisbursal(),
            request.getClientCompanyName(), 
            request.getClientContactPersonName(), 
            request.getClientContactPersonPhone(), 
            request.getClientContactPersonEmail(), 
            request.getAuthorizedSignatoryName(),
            request.getAuthorizedSignatoryPhone(), 
            request.getAuthorizedSignatoryEmail(), 
            request.getVa(), 
            request.getDistributionPartner(), 
            bankTransferFee, 
            request.getSpecialConditions(), 
            latePaymentFee
        );
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


            // Create a map with the updated line of credit data
            final Map<String, Object> responseData = new HashMap<>();
            responseData.put("changes", changes);

            return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(changes).build();
        } catch (final Exception e) {
            log.error("Error occurred while updating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.update.failed", "Line of credit update failed",
                    "lineOfCreditId", e);
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

            log.info("Line of credit activated successfully with ID: {}", lineOfCreditId);

            // Create a map with the activated line of credit data
            final Map<String, Object> responseData = new HashMap<>();
            responseData.put("action", "ACTIVATED");

            return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(responseData).build();

        } catch (final Exception e) {
            log.error("Error occurred while activating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.activation.failed", "Line of credit activation failed",
                    "lineOfCreditId", e);
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

            // Create a map with the deactivated line of credit data
            final Map<String, Object> responseData = new HashMap<>();
            responseData.put("action", "DEACTIVATED");

            return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(responseData).build();

        } catch (final Exception e) {
            log.error("Error occurred while deactivating line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.deactivation.failed",
                    "Line of credit deactivation failed", "lineOfCreditId", e);
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteLineOfCredit(Long lineOfCreditId) {
        try {
            final LineOfCredit lineOfCredit = this.lineOfCreditRepository.findById(lineOfCreditId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.line.of.credit.not.found",
                            "Line of credit not found", "lineOfCreditId"));

            // Store the data before deletion
            final Map<String, Object> responseData = new HashMap<>();
            responseData.put("resourceId", lineOfCredit.getId());
            responseData.put("action", "DELETED");

            this.lineOfCreditRepository.delete(lineOfCredit);

            return new CommandProcessingResultBuilder().withEntityId(lineOfCreditId).with(responseData).build();

        } catch (final Exception e) {
            log.error("Error occurred while deleting line of credit", e);
            throw new PlatformApiDataValidationException("error.msg.line.of.credit.deletion.failed", "Line of credit deletion failed",
                    "lineOfCreditId", e);
        }
    }
}
