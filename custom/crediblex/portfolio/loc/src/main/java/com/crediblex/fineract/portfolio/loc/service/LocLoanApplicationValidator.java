package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditData;
import com.crediblex.fineract.portfolio.loc.api.LocApiConstants;
import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

/**
 * Line of Credit specific validator for loan applications.
 * This validator checks if a lineOfCreditId is provided and validates
 * that the loan amount does not exceed the available balance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocLoanApplicationValidator {

    private final LineOfCreditReadPlatformService lineOfCreditReadPlatformService;
    private final FromJsonHelper fromApiJsonHelper;

    /**
     * Validates Line of Credit specific parameters and business rules.
     * This method should be called after the standard loan application validation.
     * 
     * @param element the JSON element containing loan application data
     */
    public void validateLineOfCredit(final JsonElement element) {
        log.debug("Starting Line of Credit validation for loan application");
        
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan");

        // Check if lineOfCreditId parameter is provided
        if (this.fromApiJsonHelper.parameterExists(LocApiConstants.LINE_OF_CREDIT_ID_PARAMETER_NAME, element)) {
            final Long lineOfCreditId = this.fromApiJsonHelper.extractLongNamed(LocApiConstants.LINE_OF_CREDIT_ID_PARAMETER_NAME, element);
            
            // Validate lineOfCreditId is not null and greater than zero
            baseDataValidator.reset().parameter(LocApiConstants.LINE_OF_CREDIT_ID_PARAMETER_NAME)
                    .value(lineOfCreditId).notNull().longGreaterThanZero();

            if (lineOfCreditId != null && lineOfCreditId > 0) {
                try {
                    // Fetch the line of credit
                    final LineOfCreditData lineOfCredit = this.lineOfCreditReadPlatformService.retrieveOne(lineOfCreditId);
                    
                    if (lineOfCredit == null) {
                        baseDataValidator.reset().parameter(LocApiConstants.LINE_OF_CREDIT_ID_PARAMETER_NAME)
                                .failWithCode("line.of.credit.not.found", 
                                    "Line of credit not found with id: " + lineOfCreditId);
                    } else {
                        // Validate available balance
                        validateAvailableBalance(element, lineOfCredit, baseDataValidator);
                    }
                } catch (Exception e) {
                    log.error("Error retrieving line of credit with id {}: {}", lineOfCreditId, e.getMessage());
                    baseDataValidator.reset().parameter(LocApiConstants.LINE_OF_CREDIT_ID_PARAMETER_NAME)
                            .failWithCode("line.of.credit.retrieval.error", 
                                "Error retrieving line of credit: " + e.getMessage());
                }
            }
        }

        // Throw validation errors if any exist
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
        
        log.debug("Line of Credit validation completed successfully");
    }

    /**
     * Validates that the loan amount does not exceed the available balance of the line of credit.
     * 
     * @param element the JSON element containing loan application data
     * @param lineOfCredit the line of credit data
     * @param baseDataValidator the data validator builder
     */
    private void validateAvailableBalance(final JsonElement element, final LineOfCreditData lineOfCredit, 
            final DataValidatorBuilder baseDataValidator) {
        
        // Extract the principal amount from the loan application
        final BigDecimal principal = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(
                "principal", element);
        
        if (principal != null && lineOfCredit.getAvailableBalance() != null) {
            // Check if the principal amount exceeds the available balance
            if (principal.compareTo(lineOfCredit.getAvailableBalance()) > 0) {
                baseDataValidator.reset().parameter("principal")
                        .failWithCode("loan.amount.exceeds.available.balance", 
                            String.format("Loan amount %s exceeds available balance %s of line of credit %s", 
                                principal, lineOfCredit.getAvailableBalance(), lineOfCredit.getName()));
                
                log.warn("Loan amount {} exceeds available balance {} for line of credit {}", 
                    principal, lineOfCredit.getAvailableBalance(), lineOfCredit.getId());
            } else {
                log.debug("Loan amount {} is within available balance {} for line of credit {}", 
                    principal, lineOfCredit.getAvailableBalance(), lineOfCredit.getId());
            }
        }
    }
}
