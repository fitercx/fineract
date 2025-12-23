package com.crediblex.fineract.portfolio.loanaccount.api;

import com.crediblex.fineract.portfolio.loanaccount.service.CustomLoanWritePlatformServiceJpaRepositoryImpl;
import com.google.gson.JsonElement;
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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

@Path("/v1/loan-adjust-installment")
@Component
@Tag(name = "Loan Adjust Installment Date", description = "API to adjust installment dates for a loan")
@RequiredArgsConstructor
public class LoanAdjustInstallmentDateApiResource {

    private final PlatformSecurityContext context;
    private final CustomLoanWritePlatformServiceJpaRepositoryImpl loanWritePlatformService;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final FromJsonHelper fromJsonHelper;

    @POST
    @Path("/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Adjust Installment Date", description = "Adjusts the due date of a specific installment and cascades the change to subsequent installments")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    public String adjustInstallmentDate(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        this.context.authenticatedUser().validateHasReadPermission("LOAN");

        final JsonElement parsedCommand = this.fromJsonHelper.parse(apiRequestBodyAsJson);
        final JsonCommand command = JsonCommand.from(apiRequestBodyAsJson, parsedCommand, this.fromJsonHelper, "LOAN", loanId, null, null,
                null, loanId, null, null, null, null, null, null, null, ExternalId.empty());

        final CommandProcessingResult result = this.loanWritePlatformService.adjustInstallmentDate(loanId, command);

        return this.toApiJsonSerializer.serialize(result);
    }
}
