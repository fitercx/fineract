package com.crediblex.fineract.portfolio.loanaccount.data;

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
public class CredXAdjustInstallmentDateDataValidator {

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public CredXAdjustInstallmentDateDataValidator(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateForAdjustInstallmentDate(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, getSupportedParameters());

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

        // Optional: when true, schedule is regenerated and interest is recalculated following the same approach as
        // Loan Reschedule > Change Repayment Date.
        final Boolean adjustWithInterestRecalculation = this.fromApiJsonHelper.extractBooleanNamed("adjustWithInterestRecalculation",
                element);
        baseDataValidator.reset().parameter("adjustWithInterestRecalculation").value(adjustWithInterestRecalculation).ignoreIfNull()
                .validateForBooleanValue();

        // Optional: reschedule reason code value id. When omitted with adjustWithInterestRecalculation=true, a default
        // active LoanRescheduleReason code value is auto-selected.
        final Long rescheduleReasonId = this.fromApiJsonHelper.extractLongNamed("rescheduleReasonId", element);
        baseDataValidator.reset().parameter("rescheduleReasonId").value(rescheduleReasonId).ignoreIfNull().longGreaterThanZero();

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private List<String> getSupportedParameters() {
        final List<String> params = new ArrayList<>();
        params.add("installmentNumber");
        params.add("newDueDate");
        params.add("adjustmentDate");
        params.add("locale");
        params.add("dateFormat");
        params.add("adjustWithInterestRecalculation");
        params.add("rescheduleReasonId");
        params.add("rescheduleReasonComment");
        return params;
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    dataValidationErrors);
        }
    }
}
