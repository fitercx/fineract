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
import com.crediblex.fineract.portfolio.loanaccount.service.LoanRepaymentStrategyUpdateService;
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

@Path("/v1/loan-repayment-strategy")
@Component
@Tag(name = "Loan Repayment Strategy", description = "Manually change the repayment strategy stored on an active loan")
@RequiredArgsConstructor
public class LoanRepaymentStrategyApiResource {

    private final PlatformSecurityContext context;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> toApiJsonSerializer;
    private final LoanRepaymentStrategyUpdateService loanRepaymentStrategyUpdateService;

    @GET
    @Path("/{loanId}/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Repayment strategy template", description = "Current strategy and the strategies a manual change may select.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String template(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId) {
        this.context.authenticatedUser().validateHasReadPermission("LOAN");
        final Map<String, Object> template = this.loanRepaymentStrategyUpdateService.template(loanId);
        return new Gson().toJson(template);
    }

    @POST
    @Path("/{loanId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Change repayment strategy", description = "Submits a manual repayment strategy change for checker approval.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    public String update(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new LoanCommandWrapperBuilder().updateRepaymentStrategy(loanId).withJson(apiRequestBodyAsJson)
                .build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return this.toApiJsonSerializer.serialize(result);
    }
}
