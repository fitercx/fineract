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
package org.apache.fineract.portfolio.loanaccount.data;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AdjustInstallmentDateDataValidator {

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public AdjustInstallmentDateDataValidator(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateForAdjustInstallmentDate(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json,
                getSupportedParameters());

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource("loan.adjust.installment.date");

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final Integer installmentNumber = this.fromApiJsonHelper.extractIntegerSansLocaleNamed("installmentNumber", element);
        baseDataValidator.reset().parameter("installmentNumber").value(installmentNumber).notNull().integerGreaterThanZero();

        final LocalDate newDueDate = this.fromApiJsonHelper.extractLocalDateNamed("newDueDate", element);
        baseDataValidator.reset().parameter("newDueDate").value(newDueDate).notNull();

        final LocalDate adjustmentDate = this.fromApiJsonHelper.extractLocalDateNamed("adjustmentDate", element);
        baseDataValidator.reset().parameter("adjustmentDate").value(adjustmentDate).notNull();

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private List<String> getSupportedParameters() {
        final List<String> params = new ArrayList<>();
        params.add("installmentNumber");
        params.add("newDueDate");
        params.add("adjustmentDate");
        params.add("locale");
        params.add("dateFormat");
        return params;
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist",
                    "Validation errors exist.", dataValidationErrors);
        }
    }
}

