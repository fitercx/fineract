package com.crediblex.fineract.portfolio.loanaccount.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonQuery;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleCalculationPlatformService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Path("/v1/loans/calculator")
@Component
@Tag(name = "Loan Calculator", description = "Calculate loan details without creating a loan")
@RequiredArgsConstructor
public class LoanCalculatorApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSIONS = "LOAN_CALCULATOR";

    private final PlatformSecurityContext context;
    private final FromJsonHelper fromJsonHelper;
    private final LoanScheduleCalculationPlatformService calculationPlatformService;
    private final DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer;

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Calculate Loan Details", description = "Calculate installment amount, total interest and total repayment amount for a loan without creating it")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LoanCalculatorApiResourceSwagger.PostLoanCalculatorResponse.class))) })
    public String calculateLoan(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        //this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        // Parse the request
        final JsonElement parsedQuery = this.fromJsonHelper.parse(apiRequestBodyAsJson);

        // Extract parameters from the request or use defaults
        JsonObject loanCalculationRequest = createLoanCalculationRequest(parsedQuery);

        // Calculate the loan schedule
        final JsonQuery query = JsonQuery.from(loanCalculationRequest.toString(), loanCalculationRequest, this.fromJsonHelper);
        final LoanScheduleModel loanSchedule = this.calculationPlatformService.calculateLoanSchedule(query, true);

        // Extract the required information from the schedule
        final Map<String, Object> result = extractLoanCalculationSummary(loanSchedule.toData());

        return this.toApiJsonSerializer.serialize(result);
    }

    private JsonObject createLoanCalculationRequest(JsonElement parsedQuery) {
        // If the request is already in the format expected by the calculation service, use it directly
        if (parsedQuery.isJsonObject()) {
            JsonObject requestObject = parsedQuery.getAsJsonObject();

            // Ensure required fields are present
            ensureRequiredFields(requestObject);

            return requestObject;
        } else {
            // This should not happen with proper API usage
            throw new IllegalArgumentException("Invalid request format");
        }
    }

    private void ensureRequiredFields(JsonObject requestObject) {
        // Set default values for required fields if not provided

        // Product ID is required by the calculation service
        if (!requestObject.has("productId") || requestObject.get("productId").isJsonNull()) {
            requestObject.addProperty("productId", 1);
        }

        // Set dates if not provided
        LocalDate currentDate = DateUtils.getBusinessLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        String formattedDate = currentDate.format(formatter);

        if (!requestObject.has("submittedOnDate") || requestObject.get("submittedOnDate").isJsonNull()) {
            requestObject.addProperty("submittedOnDate", formattedDate);
        }

        if (!requestObject.has("expectedDisbursementDate") || requestObject.get("expectedDisbursementDate").isJsonNull()) {
            requestObject.addProperty("expectedDisbursementDate", formattedDate);
        }

        // Set transaction processing strategy if not provided
        if (!requestObject.has("transactionProcessingStrategyCode") || requestObject.get("transactionProcessingStrategyCode").isJsonNull()) {
            requestObject.addProperty("transactionProcessingStrategyCode", "mifos-standard-strategy");
        }

        // Set loan type if not provided
        if (!requestObject.has("loanType") || requestObject.get("loanType").isJsonNull()) {
            requestObject.addProperty("loanType", "individual");
        }

        // Set formatting parameters if not provided
        if (!requestObject.has("dateFormat") || requestObject.get("dateFormat").isJsonNull()) {
            requestObject.addProperty("dateFormat", "dd MMMM yyyy");
        }

        if (!requestObject.has("locale") || requestObject.get("locale").isJsonNull()) {
            requestObject.addProperty("locale", "en");
        }

        // Set repayment frequency type if not provided
        if (!requestObject.has("repaymentFrequencyType") || requestObject.get("repaymentFrequencyType").isJsonNull()) {
            if (requestObject.has("loanTermFrequencyType") && !requestObject.get("loanTermFrequencyType").isJsonNull()) {
                requestObject.addProperty("repaymentFrequencyType", requestObject.get("loanTermFrequencyType").getAsInt());
            } else {
                requestObject.addProperty("repaymentFrequencyType", 2); // Monthly by default
            }
        }

        // Set repayment every if not provided
        if (!requestObject.has("repaymentEvery") || requestObject.get("repaymentEvery").isJsonNull()) {
            if (requestObject.has("loanTermFrequency") && requestObject.has("numberOfRepayments") &&
                    !requestObject.get("loanTermFrequency").isJsonNull() && !requestObject.get("numberOfRepayments").isJsonNull()) {
                int loanTermFrequency = requestObject.get("loanTermFrequency").getAsInt();
                int numberOfRepayments = requestObject.get("numberOfRepayments").getAsInt();
                int repaymentEvery = loanTermFrequency / numberOfRepayments;
                if (repaymentEvery < 1) repaymentEvery = 1;
                requestObject.addProperty("repaymentEvery", repaymentEvery);
            } else {
                requestObject.addProperty("repaymentEvery", 1);
            }
        }

        // Set interest rate frequency type if not provided
        if (!requestObject.has("interestRateFrequencyType") || requestObject.get("interestRateFrequencyType").isJsonNull()) {
            requestObject.addProperty("interestRateFrequencyType", 3); // Annual by default
        }

        // Set amortization type if not provided
        if (!requestObject.has("amortizationType") || requestObject.get("amortizationType").isJsonNull()) {
            requestObject.addProperty("amortizationType", 1); // Equal installments by default
        }

        // Set interest calculation period type if not provided
        if (!requestObject.has("interestCalculationPeriodType") || requestObject.get("interestCalculationPeriodType").isJsonNull()) {
            requestObject.addProperty("interestCalculationPeriodType", 1); // Same as repayment period by default
        }

        // Set empty arrays for charges and collateral if not provided
        if (!requestObject.has("charges")) {
            requestObject.add("charges", new JsonArray()); //fromJsonHelper.createEmptyJsonArray()
        }

        if (!requestObject.has("collateral")) {
            requestObject.add("collateral", new JsonArray()); //fromJsonHelper.createEmptyJsonArray()
        }
    }

    private Map<String, Object> extractLoanCalculationSummary(LoanScheduleData scheduleData) {
        Map<String, Object> result = new HashMap<>();

        // Extract the total interest amount
        BigDecimal totalInterestCharged = scheduleData.getTotalInterestCharged();
        result.put("interestAmount", totalInterestCharged);

        // Extract the total repayment amount (principal + interest)
        BigDecimal totalRepaymentAmount = scheduleData.getTotalRepaymentExpected();
        result.put("repaymentAmount", totalRepaymentAmount);

        // Calculate the installment amount (total / number of installments)
        int numberOfInstallments = scheduleData.getPeriods().size() - 1; // Subtract 1 for the disbursement period
        if (numberOfInstallments > 0) {
            BigDecimal installmentAmount = totalRepaymentAmount.divide(
                    BigDecimal.valueOf(numberOfInstallments),
                    2,
                    RoundingMode.HALF_UP);
            result.put("installmentAmount", installmentAmount);
        } else {
            result.put("installmentAmount", totalRepaymentAmount);
        }

        return result;
    }
}
