package com.crediblex.fineract.portfolio.loanaccount.api;

import com.crediblex.fineract.portfolio.loanaccount.commands.LoanCommandWrapperBuilder;
import com.crediblex.fineract.portfolio.loanaccount.service.CustomLoanWritePlatformServiceJpaRepositoryImpl;
import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

@Path("/v1/loan-adjust-installment")
@Component
@Tag(name = "Loan Adjust Installment Date", description = "API to adjust installment dates for a loan")
@RequiredArgsConstructor
public class LoanAdjustInstallmentDateApiResource {

    private final PlatformSecurityContext context;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> toApiJsonSerializer;
    private final CustomLoanWritePlatformServiceJpaRepositoryImpl customLoanWritePlatformService;

    @GET
    @Path("/{loanId}/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Adjust Installment Date template", description = "Tells the UI whether interest recalculation is available for this loan and, if not, the reason - so the option can be disabled with an on-screen message.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String template(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId) {
        this.context.authenticatedUser().validateHasReadPermission("LOAN");
        final Map<String, Object> template = this.customLoanWritePlatformService.retrieveAdjustInstallmentDateTemplate(loanId);
        return new Gson().toJson(template);
    }

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

        // Create CommandWrapper with proper entity and action names for audit trail
        final CommandWrapper commandRequest = new LoanCommandWrapperBuilder().adjustInstallmentDate(loanId).withJson(apiRequestBodyAsJson)
                .build();

        // This will log the command source (audit trail) and trigger the command handler
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }
}
