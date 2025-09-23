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

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.data.LocCashMarginType;
import com.crediblex.fineract.portfolio.loc.data.LocInterestChargeTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.eclipse.persistence.exceptions.ValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LineOfCreditDataValidator {

    private final FromJsonHelper fromApiJsonHelper;
    private final JdbcTemplate jdbcTemplate;

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
     * Validates that a line of credit limit can be reduced. Blocks reduction of LOC credit limit if the new limit is
     * less than the currently utilized balance.
     *
     * @param lineOfCreditId
     *            the ID of the line of credit to validate
     * @param newMaximumAmount
     *            the new maximum amount to validate
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
            BigDecimal consumedAmount = (BigDecimal) result.get("consumed_amount");

            // Check if the new limit is less than the currently utilized balance
            if (newMaximumAmount.compareTo(consumedAmount) < 0) {
                baseDataValidator.reset().parameter("maximumAmount").value(newMaximumAmount)
                        .failWithCode("line.of.credit.cannot.reduce.limit.below.utilized.balance", "Cannot reduce line of credit limit to "
                                + newMaximumAmount + " because it is less than the currently utilized balance of " + consumedAmount);
            }

        } catch (ValidationException e) {
            baseDataValidator.reset().parameter("maximumAmount").value(newMaximumAmount).failWithCode(
                    "line.of.credit.limit.reduction.validation.error",
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
