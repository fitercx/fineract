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
package com.crediblex.fineract.portfolio.loanaccount.api;

import com.crediblex.fineract.portfolio.loanaccount.commands.LoanCommandWrapperBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.CommandParameterUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.api.LoanChargesApiResource;
import org.apache.fineract.portfolio.loanaccount.api.LoanChargesApiResourceSwagger;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;

/**
 * Custom LoanChargesApiResource for CredibleX that extends base functionality to support additional charge operations
 * like reversing paid charges.
 */
@Path("/v1/loans")
@Tag(name = "Loan Charges", description = "Its typical for MFIs to directly associate charges with an given loan account. In addition to the typical loan charges for an account such as administrative, legal or other loan processing type charges, charges may also be used as penalties when interest or principal payments are paid late.")
public class CredXLoanChargesApiResource extends LoanChargesApiResource {

    public static final String COMMAND_REVERSE_PAID = "reversePaid";

    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<LoanChargeData> toApiJsonSerializer;

    public CredXLoanChargesApiResource(PlatformSecurityContext context, ChargeReadPlatformService chargeReadPlatformService,
            LoanChargeReadPlatformService loanChargeReadPlatformService, DefaultToApiJsonSerializer<LoanChargeData> toApiJsonSerializer,
            ApiRequestParameterHelper apiRequestParameterHelper,
            PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            LoanReadPlatformService loanReadPlatformService) {
        super(context, chargeReadPlatformService, loanChargeReadPlatformService, toApiJsonSerializer, apiRequestParameterHelper,
                commandsSourceWritePlatformService, loanReadPlatformService);
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
    }

    /**
     * Override to add support for REVERSEPAID command using custom command wrapper builder
     */
    @POST
    @Path("{loanId}/charges/{loanChargeId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Pay / Waive / Adjustment for Loan Charge", description = "Loan Charge will be paid if the loan is linked with a savings account | Waive Loan Charge | Add Charge Adjustment")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = LoanChargesApiResourceSwagger.PostLoansLoanIdChargesChargeIdRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LoanChargesApiResourceSwagger.PostLoansLoanIdChargesChargeIdResponse.class))) })
    public String executeLoanCharge(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId,
            @PathParam("loanChargeId") @Parameter(description = "loanChargeId") final Long loanChargeId,
            @QueryParam("command") @Parameter(description = "command") final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        // Check if this is our custom REVERSEPAID command
        if (CommandParameterUtil.is(commandParam, COMMAND_REVERSE_PAID)) {
            // Use custom command wrapper builder for REVERSEPAID command
            final CommandWrapper commandRequest = new LoanCommandWrapperBuilder().reversePaidLoanCharge(loanId, loanChargeId)
                    .withJson(apiRequestBodyAsJson).build();
            final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
            return this.toApiJsonSerializer.serialize(result);
        }

        // For all other commands, delegate to parent
        return super.executeLoanCharge(loanId, loanChargeId, commandParam, apiRequestBodyAsJson);
    }
}
