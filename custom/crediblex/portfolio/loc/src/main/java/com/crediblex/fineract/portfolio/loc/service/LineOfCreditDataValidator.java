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

import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import com.google.gson.reflect.TypeToken;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Type;
import org.springframework.jdbc.core.JdbcTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class LineOfCreditDataValidator {

    private final FromJsonHelper fromApiJsonHelper;
    private final JdbcTemplate jdbcTemplate;

    public void validateForCreate(String json) {
        if (json == null) {
            throw new InvalidJsonException();
        }
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("line.of.credit");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        // Check for unsupported parameters - lineOfCreditId should not be in request body
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        final Set<String> supportedParameters = new HashSet<>(Arrays.asList(
            "locale", "dateFormat", "clientId", "name", "productType", "maximumAmount", "startDate", "endDate",
            "approvedCreditFacilityAmount", "externalId", "activationDate", "currency", "advancePercentage",
            "tenorDays", "approvedBuyers", "processingFeePctLoc", "cashMarginType", "cashMarginValue",
            "invHandlingFeeBasis", "invHandlingFeePct", "invHandlingFeeMinAmount", "invHandlingFeeCurrency",
            "interimReviewDate", "rateType", "annualInterestRate", "isInterestUpfrontOrPostDisbursal",
            "clientCompanyName", "clientContactPersonName", "clientContactPersonPhone", "clientContactPersonEmail",
            "authorizedSignatoryName", "authorizedSignatoryPhone", "authorizedSignatoryEmail", "va",
            "distributionPartner", "bankTransferFee", "specialConditions", "latePaymentFee"
        ));
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParameters);

        final Long clientId = this.fromApiJsonHelper.extractLongNamed("clientId", element);
        baseDataValidator.reset().parameter("clientId").value(clientId).notNull().integerGreaterThanZero();

        final String name = this.fromApiJsonHelper.extractStringNamed("name", element);
        baseDataValidator.reset().parameter("name").value(name).notBlank().notExceedingLengthOf(100);

        final String productType = this.fromApiJsonHelper.extractStringNamed("productType", element);
        baseDataValidator.reset().parameter("productType").value(productType).notBlank().notExceedingLengthOf(50);

        final BigDecimal maximumAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("maximumAmount", element);
        baseDataValidator.reset().parameter("maximumAmount").value(maximumAmount).notNull().positiveAmount();

         final String dateFormat = this.fromApiJsonHelper.extractDateFormatParameter(element.getAsJsonObject());
         final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(element.getAsJsonObject());
         final LocalDate startDate = this.fromApiJsonHelper.extractLocalDateNamed("startDate", element, dateFormat, locale);
         baseDataValidator.reset().parameter("startDate").value(startDate).notNull();

         final LocalDate endDate = this.fromApiJsonHelper.extractLocalDateNamed("endDate", element, dateFormat, locale);
         baseDataValidator.reset().parameter("endDate").value(endDate).notNull();

         if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
         baseDataValidator.reset().parameter("endDate").value(endDate).failWithCode("end.date.cannot.be.before.start.date");
         }

        // Validate new fields
        validateNewFields(element, baseDataValidator, dateFormat, locale);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateForUpdate(String json) {
        if (json == null) {
            throw new InvalidJsonException();
        }

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("line.of.credit");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        final Set<String> supportedParameters = new HashSet<>(Arrays.asList(
            "locale", "dateFormat", "name", "productType", "maximumAmount", "startDate", "endDate",
            "approvedCreditFacilityAmount", "externalId", "activationDate", "currency", "advancePercentage",
            "tenorDays", "approvedBuyers", "processingFeePctLoc", "cashMarginType", "cashMarginValue",
            "invHandlingFeeBasis", "invHandlingFeePct", "invHandlingFeeMinAmount", "invHandlingFeeCurrency",
            "interimReviewDate", "rateType", "annualInterestRate", "isInterestUpfrontOrPostDisbursal",
            "clientCompanyName", "clientContactPersonName", "clientContactPersonPhone", "clientContactPersonEmail",
            "authorizedSignatoryName", "authorizedSignatoryPhone", "authorizedSignatoryEmail", "va",
            "distributionPartner", "bankTransferFee", "specialConditions", "latePaymentFee"
        ));
        
        try {
            // Create a filtered JSON without clientId for the unsupported parameters check
            String filteredJson = json;
            if (json.contains("\"clientId\":null")) {
                filteredJson = json.replace("\"clientId\":null,", "").replace(",\"clientId\":null", "");
            }
            
            this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, filteredJson, supportedParameters);
        } catch (Exception e) {
            throw e;
        }

        if (this.fromApiJsonHelper.parameterExists("name", element)) {
            try {
                final String name = this.fromApiJsonHelper.extractStringNamed("name", element);
                baseDataValidator.reset().parameter("name").value(name).notBlank().notExceedingLengthOf(100);
            } catch (Exception e) {
                throw e;
            }
        }

        if (this.fromApiJsonHelper.parameterExists("productType", element)) {
            final String productType = this.fromApiJsonHelper.extractStringNamed("productType", element);
            baseDataValidator.reset().parameter("productType").value(productType).notBlank().notExceedingLengthOf(50);
        }

        if (this.fromApiJsonHelper.parameterExists("maximumAmount", element)) {
            final BigDecimal maximumAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("maximumAmount", element);
            baseDataValidator.reset().parameter("maximumAmount").value(maximumAmount).notNull().positiveAmount();
        }

        if (this.fromApiJsonHelper.parameterExists("startDate", element)) {
            try {
                final String dateFormat = this.fromApiJsonHelper.extractDateFormatParameter(element.getAsJsonObject());
                final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(element.getAsJsonObject());
                final LocalDate startDate = this.fromApiJsonHelper.extractLocalDateNamed("startDate", element, dateFormat, locale);
                baseDataValidator.reset().parameter("startDate").value(startDate).notNull();
            } catch (Exception e) {
                throw e;
            }
        }

        if (this.fromApiJsonHelper.parameterExists("endDate", element)) {
            try {
                final String dateFormat = this.fromApiJsonHelper.extractDateFormatParameter(element.getAsJsonObject());
                final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(element.getAsJsonObject());
                final LocalDate endDate = this.fromApiJsonHelper.extractLocalDateNamed("endDate", element, dateFormat, locale);
                baseDataValidator.reset().parameter("endDate").value(endDate).notNull();
            } catch (Exception e) {
                throw e;
            }
        }

        // Validate new fields for update
        if (this.fromApiJsonHelper.parameterExists("approvedCreditFacilityAmount", element) || 
            this.fromApiJsonHelper.parameterExists("externalId", element) ||
            this.fromApiJsonHelper.parameterExists("activationDate", element) ||
            this.fromApiJsonHelper.parameterExists("currency", element) ||
            this.fromApiJsonHelper.parameterExists("advancePercentage", element) ||
            this.fromApiJsonHelper.parameterExists("tenorDays", element) ||
            this.fromApiJsonHelper.parameterExists("approvedBuyers", element) ||
            this.fromApiJsonHelper.parameterExists("processingFeePctLoc", element) ||
            this.fromApiJsonHelper.parameterExists("cashMarginType", element) ||
            this.fromApiJsonHelper.parameterExists("cashMarginValue", element) ||
            this.fromApiJsonHelper.parameterExists("invHandlingFeeBasis", element) ||
            this.fromApiJsonHelper.parameterExists("invHandlingFeePct", element) ||
            this.fromApiJsonHelper.parameterExists("invHandlingFeeMinAmount", element) ||
            this.fromApiJsonHelper.parameterExists("invHandlingFeeCurrency", element) ||
            this.fromApiJsonHelper.parameterExists("interimReviewDate", element) ||
            this.fromApiJsonHelper.parameterExists("rateType", element) ||
            this.fromApiJsonHelper.parameterExists("annualInterestRate", element) ||
            this.fromApiJsonHelper.parameterExists("isInterestUpfrontOrPostDisbursal", element) ||
            this.fromApiJsonHelper.parameterExists("clientCompanyName", element) ||
            this.fromApiJsonHelper.parameterExists("clientContactPersonName", element) ||
            this.fromApiJsonHelper.parameterExists("clientContactPersonPhone", element) ||
            this.fromApiJsonHelper.parameterExists("clientContactPersonEmail", element) ||
            this.fromApiJsonHelper.parameterExists("authorizedSignatoryName", element) ||
            this.fromApiJsonHelper.parameterExists("authorizedSignatoryPhone", element) ||
            this.fromApiJsonHelper.parameterExists("authorizedSignatoryEmail", element) ||
            this.fromApiJsonHelper.parameterExists("va", element) ||
            this.fromApiJsonHelper.parameterExists("distributionPartner", element) ||
            this.fromApiJsonHelper.parameterExists("bankTransferFee", element) ||
            this.fromApiJsonHelper.parameterExists("specialConditions", element) ||
            this.fromApiJsonHelper.parameterExists("latePaymentFee", element)) {
            
            final String dateFormat = this.fromApiJsonHelper.extractDateFormatParameter(element.getAsJsonObject());
            final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(element.getAsJsonObject());
            validateNewFields(element, baseDataValidator, dateFormat, locale);
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private void validateNewFields(JsonElement element, DataValidatorBuilder baseDataValidator, String dateFormat, Locale locale) {
        // Validate approved credit facility amount
        if (this.fromApiJsonHelper.parameterExists("approvedCreditFacilityAmount", element)) {
            final BigDecimal approvedCreditFacilityAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("approvedCreditFacilityAmount", element);
            baseDataValidator.reset().parameter("approvedCreditFacilityAmount").value(approvedCreditFacilityAmount).positiveAmount();
        }

        // Validate external ID
        if (this.fromApiJsonHelper.parameterExists("externalId", element)) {
            final String externalId = this.fromApiJsonHelper.extractStringNamed("externalId", element);
            baseDataValidator.reset().parameter("externalId").value(externalId).notExceedingLengthOf(100);
        }

        // Validate activation date
        if (this.fromApiJsonHelper.parameterExists("activationDate", element)) {
            final LocalDate activationDate = this.fromApiJsonHelper.extractLocalDateNamed("activationDate", element, dateFormat, locale);
            baseDataValidator.reset().parameter("activationDate").value(activationDate).notNull();
        }

        // Validate currency
        if (this.fromApiJsonHelper.parameterExists("currency", element)) {
            final String currency = this.fromApiJsonHelper.extractStringNamed("currency", element);
            baseDataValidator.reset().parameter("currency").value(currency).notExceedingLengthOf(10);
        }

        // Validate advance percentage
        if (this.fromApiJsonHelper.parameterExists("advancePercentage", element)) {
            final BigDecimal advancePercentage = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("advancePercentage", element);
            baseDataValidator.reset().parameter("advancePercentage").value(advancePercentage).positiveAmount();
        }

        // Validate tenor days
        if (this.fromApiJsonHelper.parameterExists("tenorDays", element)) {
            final Integer tenorDays = this.fromApiJsonHelper.extractIntegerNamed("tenorDays", element, locale);
            baseDataValidator.reset().parameter("tenorDays").value(tenorDays).positiveAmount();
        }

        // Validate processing fee percentage
        if (this.fromApiJsonHelper.parameterExists("processingFeePctLoc", element)) {
            final BigDecimal processingFeePctLoc = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("processingFeePctLoc", element);
            baseDataValidator.reset().parameter("processingFeePctLoc").value(processingFeePctLoc).positiveAmount();
        }

        // Validate cash margin type
        if (this.fromApiJsonHelper.parameterExists("cashMarginType", element)) {
            final String cashMarginType = this.fromApiJsonHelper.extractStringNamed("cashMarginType", element);
            baseDataValidator.reset().parameter("cashMarginType").value(cashMarginType).notExceedingLengthOf(50);
        }

        // Validate cash margin value
        if (this.fromApiJsonHelper.parameterExists("cashMarginValue", element)) {
            final BigDecimal cashMarginValue = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("cashMarginValue", element);
            baseDataValidator.reset().parameter("cashMarginValue").value(cashMarginValue).positiveAmount();
        }

        // Validate invoice handling fee basis
        if (this.fromApiJsonHelper.parameterExists("invHandlingFeeBasis", element)) {
            final String invHandlingFeeBasis = this.fromApiJsonHelper.extractStringNamed("invHandlingFeeBasis", element);
            baseDataValidator.reset().parameter("invHandlingFeeBasis").value(invHandlingFeeBasis).notExceedingLengthOf(50);
        }

        // Validate invoice handling fee percentage
        if (this.fromApiJsonHelper.parameterExists("invHandlingFeePct", element)) {
            final BigDecimal invHandlingFeePct = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("invHandlingFeePct", element);
            baseDataValidator.reset().parameter("invHandlingFeePct").value(invHandlingFeePct).positiveAmount();
        }

        // Validate invoice handling fee min amount
        if (this.fromApiJsonHelper.parameterExists("invHandlingFeeMinAmount", element)) {
            final BigDecimal invHandlingFeeMinAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("invHandlingFeeMinAmount", element);
            baseDataValidator.reset().parameter("invHandlingFeeMinAmount").value(invHandlingFeeMinAmount).positiveAmount();
        }

        // Validate invoice handling fee currency
        if (this.fromApiJsonHelper.parameterExists("invHandlingFeeCurrency", element)) {
            final String invHandlingFeeCurrency = this.fromApiJsonHelper.extractStringNamed("invHandlingFeeCurrency", element);
            baseDataValidator.reset().parameter("invHandlingFeeCurrency").value(invHandlingFeeCurrency).notExceedingLengthOf(10);
        }

        // Validate interim review date
        if (this.fromApiJsonHelper.parameterExists("interimReviewDate", element)) {
            final LocalDate interimReviewDate = this.fromApiJsonHelper.extractLocalDateNamed("interimReviewDate", element, dateFormat, locale);
            baseDataValidator.reset().parameter("interimReviewDate").value(interimReviewDate).notNull();
        }

        // Validate rate type
        if (this.fromApiJsonHelper.parameterExists("rateType", element)) {
            final String rateType = this.fromApiJsonHelper.extractStringNamed("rateType", element);
            baseDataValidator.reset().parameter("rateType").value(rateType).notExceedingLengthOf(50);
        }

        // Validate annual interest rate
        if (this.fromApiJsonHelper.parameterExists("annualInterestRate", element)) {
            final BigDecimal annualInterestRate = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("annualInterestRate", element);
            baseDataValidator.reset().parameter("annualInterestRate").value(annualInterestRate).positiveAmount();
        }

        // Validate interest upfront or post disbursal
        if (this.fromApiJsonHelper.parameterExists("isInterestUpfrontOrPostDisbursal", element)) {
            final String isInterestUpfrontOrPostDisbursal = this.fromApiJsonHelper.extractStringNamed("isInterestUpfrontOrPostDisbursal", element);
            baseDataValidator.reset().parameter("isInterestUpfrontOrPostDisbursal").value(isInterestUpfrontOrPostDisbursal).notExceedingLengthOf(20);
        }

        // Validate client company name
        if (this.fromApiJsonHelper.parameterExists("clientCompanyName", element)) {
            final String clientCompanyName = this.fromApiJsonHelper.extractStringNamed("clientCompanyName", element);
            baseDataValidator.reset().parameter("clientCompanyName").value(clientCompanyName).notExceedingLengthOf(255);
        }

        // Validate client contact person name
        if (this.fromApiJsonHelper.parameterExists("clientContactPersonName", element)) {
            final String clientContactPersonName = this.fromApiJsonHelper.extractStringNamed("clientContactPersonName", element);
            baseDataValidator.reset().parameter("clientContactPersonName").value(clientContactPersonName).notExceedingLengthOf(255);
        }

        // Validate client contact person phone
        if (this.fromApiJsonHelper.parameterExists("clientContactPersonPhone", element)) {
            final String clientContactPersonPhone = this.fromApiJsonHelper.extractStringNamed("clientContactPersonPhone", element);
            baseDataValidator.reset().parameter("clientContactPersonPhone").value(clientContactPersonPhone).notExceedingLengthOf(50);
        }

        // Validate client contact person email
        if (this.fromApiJsonHelper.parameterExists("clientContactPersonEmail", element)) {
            final String clientContactPersonEmail = this.fromApiJsonHelper.extractStringNamed("clientContactPersonEmail", element);
            baseDataValidator.reset().parameter("clientContactPersonEmail").value(clientContactPersonEmail).notExceedingLengthOf(255);
        }

        // Validate authorized signatory name
        if (this.fromApiJsonHelper.parameterExists("authorizedSignatoryName", element)) {
            final String authorizedSignatoryName = this.fromApiJsonHelper.extractStringNamed("authorizedSignatoryName", element);
            baseDataValidator.reset().parameter("authorizedSignatoryName").value(authorizedSignatoryName).notExceedingLengthOf(255);
        }

        // Validate authorized signatory phone
        if (this.fromApiJsonHelper.parameterExists("authorizedSignatoryPhone", element)) {
            final String authorizedSignatoryPhone = this.fromApiJsonHelper.extractStringNamed("authorizedSignatoryPhone", element);
            baseDataValidator.reset().parameter("authorizedSignatoryPhone").value(authorizedSignatoryPhone).notExceedingLengthOf(50);
        }

        // Validate authorized signatory email
        if (this.fromApiJsonHelper.parameterExists("authorizedSignatoryEmail", element)) {
            final String authorizedSignatoryEmail = this.fromApiJsonHelper.extractStringNamed("authorizedSignatoryEmail", element);
            baseDataValidator.reset().parameter("authorizedSignatoryEmail").value(authorizedSignatoryEmail).notExceedingLengthOf(255);
        }

        // Validate va
        if (this.fromApiJsonHelper.parameterExists("va", element)) {
            final String va = this.fromApiJsonHelper.extractStringNamed("va", element);
            baseDataValidator.reset().parameter("va").value(va).notExceedingLengthOf(255);
        }

        // Validate distribution partner
        if (this.fromApiJsonHelper.parameterExists("distributionPartner", element)) {
            final String distributionPartner = this.fromApiJsonHelper.extractStringNamed("distributionPartner", element);
            baseDataValidator.reset().parameter("distributionPartner").value(distributionPartner).notExceedingLengthOf(255);
        }

        // Validate bank transfer fee
        if (this.fromApiJsonHelper.parameterExists("bankTransferFee", element)) {
            final BigDecimal bankTransferFee = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("bankTransferFee", element);
            baseDataValidator.reset().parameter("bankTransferFee").value(bankTransferFee).positiveAmount();
        }

        // Validate late payment fee
        if (this.fromApiJsonHelper.parameterExists("latePaymentFee", element)) {
            final BigDecimal latePaymentFee = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("latePaymentFee", element);
            baseDataValidator.reset().parameter("latePaymentFee").value(latePaymentFee).positiveAmount();
        }
    }

    /**
     * Validates that a line of credit can be deactivated.
     * Disallows deactivation of LOC accounts with active loan accounts linked to them.
     *
     * @param lineOfCreditId the ID of the line of credit to validate for deactivation
     */
    public void validateForDeactivation(Long lineOfCreditId) {
        if (lineOfCreditId == null) {
            throw new IllegalArgumentException("Line of credit ID cannot be null");
        }

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("line.of.credit");

        // Check if there are any active loan accounts linked to this line of credit
        String sql = "SELECT COUNT(*) FROM m_loan l WHERE l.line_of_credit_id = ? AND l.loan_status_id = 300";
        
        try {
            Integer activeLoanCount = jdbcTemplate.queryForObject(sql, Integer.class, lineOfCreditId);
            
            if (activeLoanCount != null && activeLoanCount > 0) {
                baseDataValidator.reset().parameter("lineOfCreditId").value(lineOfCreditId)
                    .failWithCode("line.of.credit.cannot.deactivate.with.active.loans", 
                        "Cannot deactivate line of credit with ID " + lineOfCreditId + 
                        " because it has " + activeLoanCount + " active loan account(s) linked to it.");
            }
        } catch (Exception e) {
            baseDataValidator.reset().parameter("lineOfCreditId").value(lineOfCreditId)
                .failWithCode("line.of.credit.deactivation.validation.error", 
                    "Error occurred while validating line of credit for deactivation: " + e.getMessage());
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    /**
     * Validates that a line of credit limit can be reduced.
     * Blocks reduction of LOC credit limit if the new limit is less than the currently utilized balance.
     *
     * @param lineOfCreditId the ID of the line of credit to validate
     * @param newMaximumAmount the new maximum amount to validate
     */
    public void validateForLimitReduction(Long lineOfCreditId, BigDecimal newMaximumAmount) {
        if (lineOfCreditId == null) {
            throw new IllegalArgumentException("Line of credit ID cannot be null");
        }
        if (newMaximumAmount == null) {
            throw new IllegalArgumentException("New maximum amount cannot be null");
        }

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("line.of.credit");

        // Get the current line of credit details to check utilized balance
        String sql = "SELECT maximum_amount, consumed_amount FROM m_line_of_credit WHERE id = ?";
        
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, lineOfCreditId);
            BigDecimal currentMaximumAmount = (BigDecimal) result.get("maximum_amount");
            BigDecimal consumedAmount = (BigDecimal) result.get("consumed_amount");
            
            // Check if the new limit is less than the currently utilized balance
            if (newMaximumAmount.compareTo(consumedAmount) < 0) {
                baseDataValidator.reset().parameter("maximumAmount").value(newMaximumAmount)
                    .failWithCode("line.of.credit.cannot.reduce.limit.below.utilized.balance", 
                        "Cannot reduce line of credit limit to " + newMaximumAmount + 
                        " because it is less than the currently utilized balance of " + consumedAmount);
            }
            
        } catch (Exception e) {
            baseDataValidator.reset().parameter("maximumAmount").value(newMaximumAmount)
                .failWithCode("line.of.credit.limit.reduction.validation.error", 
                    "Error occurred while validating line of credit limit reduction: " + e.getMessage());
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    dataValidationErrors);
        }
    }
}
