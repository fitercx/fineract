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
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LineOfCreditDataValidator {

    private final FromJsonHelper fromApiJsonHelper;

    public void validateForCreate(String json) {
        if (json == null) {
            throw new InvalidJsonException();
        }
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("line.of.credit");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final Long clientId = this.fromApiJsonHelper.extractLongNamed("clientId", element);
        baseDataValidator.reset().parameter("clientId").value(clientId).notNull().integerGreaterThanZero();

        final String name = this.fromApiJsonHelper.extractStringNamed("name", element);
        baseDataValidator.reset().parameter("name").value(name).notBlank().notExceedingLengthOf(100);

        final String productType = this.fromApiJsonHelper.extractStringNamed("productType", element);
        baseDataValidator.reset().parameter("productType").value(productType).notBlank().notExceedingLengthOf(50);

        final BigDecimal maximumAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("maximumAmount", element);
        baseDataValidator.reset().parameter("maximumAmount").value(maximumAmount).notNull().positiveAmount();

        final LocalDate startDate = this.fromApiJsonHelper.extractLocalDateNamed("startDate", element);
        baseDataValidator.reset().parameter("startDate").value(startDate).notNull();

        final LocalDate endDate = this.fromApiJsonHelper.extractLocalDateNamed("endDate", element);
        baseDataValidator.reset().parameter("endDate").value(endDate).notNull();

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            baseDataValidator.reset().parameter("endDate").value(endDate).failWithCode("end.date.cannot.be.before.start.date");
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateForUpdate(String json) {
        if (json == null) {
            throw new InvalidJsonException();
        }

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("line.of.credit");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        if (this.fromApiJsonHelper.parameterExists("name", element)) {
            final String name = this.fromApiJsonHelper.extractStringNamed("name", element);
            baseDataValidator.reset().parameter("name").value(name).notBlank().notExceedingLengthOf(100);
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
            final LocalDate startDate = this.fromApiJsonHelper.extractLocalDateNamed("startDate", element);
            baseDataValidator.reset().parameter("startDate").value(startDate).notNull();
        }

        if (this.fromApiJsonHelper.parameterExists("endDate", element)) {
            final LocalDate endDate = this.fromApiJsonHelper.extractLocalDateNamed("endDate", element);
            baseDataValidator.reset().parameter("endDate").value(endDate).notNull();
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
