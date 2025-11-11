/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.crediblex.fineract.portfolio.loc.service;

import static com.crediblex.fineract.portfolio.loc.api.LineOfCreditApiConstants.ADJUSTED_CREDIT_LIMIT;

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.data.LocCashMarginType;
import com.crediblex.fineract.portfolio.loc.data.LocInterestChargeTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LineOfCreditDataValidator {

    private final FromJsonHelper fromApiJsonHelper;

    public LineOfCreditRequest validateForCreate(String json) {
        if (json == null) {
            throw new InvalidJsonException();
        }
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("line.of.credit");

        LineOfCreditRequest request = fromApiJsonHelper.fromJson(json, LineOfCreditRequest.class);

        LocalDate startDate = DateUtils.parseLocalDate(request.getStartDate(), request.getDateFormat(), Locale.of(request.getLocale()));
        LocalDate endDate = DateUtils.parseLocalDate(request.getEndDate(), request.getDateFormat(), Locale.of(request.getLocale()));

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            baseDataValidator.reset().parameter("endDate").value(endDate).failWithCode("end.date.cannot.be.before.start.date");
        }

        // Validate new fields
        validateNewFields(request, baseDataValidator);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
        return request;
    }

    public void validateForUpdate(String json) {
        if (json == null) {
            throw new InvalidJsonException();
        }

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("line.of.credit");

        LineOfCreditRequest request = fromApiJsonHelper.fromJson(json, LineOfCreditRequest.class);

        validateNewFields(request, baseDataValidator);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private void validateNewFields(LineOfCreditRequest request, DataValidatorBuilder baseDataValidator) {

        // Always validate required fields for both create and edit operations
        validateRequiredFields(request, baseDataValidator);

        if (request.getCashMarginType() != null) {
            baseDataValidator.reset().parameter("cashMarginType").value(request.getCashMarginType())
                    .anyOfNotNull(LocCashMarginType.FLAT.getValue(), LocCashMarginType.PERCENTAGE.getValue());
        }

        // Validate interim review date
        if (request.getInterimReviewDate() != null) {
            LocalDate tenantDate = DateUtils.getLocalDateOfTenant();
            LocalDate interimReviewDate = DateUtils.parseLocalDate(request.getInterimReviewDate(), request.getDateFormat(),
                    Locale.of(request.getLocale()));

            if (interimReviewDate.isBefore(tenantDate)) {
                baseDataValidator.reset().parameter("interimReviewDate").value(interimReviewDate)
                        .failWithCode("interim.review.date.cannot.be.in.the.past");
            }
        }

        if (request.getInterestPaymentType() != null) {
            baseDataValidator.reset().parameter("interestPaymentType").value(request.getInterestPaymentType())
                    .anyOfNotNull(LocInterestChargeTime.UPFRONT.getValue(), LocInterestChargeTime.UPFRONT.getValue());
        }
    }

    /**
     * Validates required fields that must always be present for both create and edit operations
     */
    private void validateRequiredFields(LineOfCreditRequest request, DataValidatorBuilder baseDataValidator) {

        baseDataValidator.reset().parameter("maximumCreditLimit").value(request.getMaxCreditLimit()).notNull().longGreaterThanNumber(0L);
        // Validate annual interest rate is always provided
        baseDataValidator.reset().parameter("annualInterestRate").value(request.getAnnualInterestRate()).notNull().integerGreaterThanZero();

        // Validate advance percentage is always provided
        baseDataValidator.reset().parameter("advancePercentage").value(request.getAdvancePercentage()).notNull().notBlank();

        // Additional validation for advance percentage - ensure it's a valid percentage
        if (request.getAdvancePercentage() != null && !request.getAdvancePercentage().trim().isEmpty()) {
            try {
                BigDecimal percentage = new BigDecimal(request.getAdvancePercentage().trim());
                if (percentage.compareTo(BigDecimal.ZERO) <= 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
                    baseDataValidator.reset().parameter("advancePercentage").value(request.getAdvancePercentage())
                            .failWithCode("advance.percentage.invalid.range", "Advance percentage must be between 0 and 100");
                }
            } catch (NumberFormatException e) {
                baseDataValidator.reset().parameter("advancePercentage").value(request.getAdvancePercentage())
                        .failWithCode("advance.percentage.invalid.format", "Advance percentage must be a valid number");
            }
        }

        // Validate tenor days is always provided
        baseDataValidator.reset().parameter("tenorDays").value(request.getTenorDays()).notNull().integerGreaterThanZero();
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    dataValidationErrors);
        }
    }

    public void validateForIncreaseOrDecreaseOfCreditLimit(JsonCommand command) {

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("line.of.credit");

        BigDecimal creditLimit = command.bigDecimalValueOfParameterNamed(ADJUSTED_CREDIT_LIMIT);
        baseDataValidator.reset().parameter(ADJUSTED_CREDIT_LIMIT).value(creditLimit).notNull().longGreaterThanNumber(0L);

        if (command.hasParameter("note")) {
            String note = command.stringValueOfParameterNamed("note");
            baseDataValidator.reset().parameter("note").value(note).notExceedingLengthOf(500);
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

}
