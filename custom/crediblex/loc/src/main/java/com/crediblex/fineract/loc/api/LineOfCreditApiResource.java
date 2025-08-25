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

package com.crediblex.fineract.loc.api;

import com.crediblex.fineract.loc.data.LineOfCreditData;
import com.crediblex.fineract.loc.data.LineOfCreditTransactionData;
import com.crediblex.fineract.loc.service.LineOfCreditReadPlatformService;
import com.crediblex.fineract.loc.service.LineOfCreditWritePlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import com.crediblex.fineract.loc.commands.LineOfCreditCommandWrapperBuilder;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;

@Path("/v1/lineofcredit")
@Component
@Tag(name = "Line of Credit", description = "Line of Credit management for clients")
@RequiredArgsConstructor
public class LineOfCreditApiResource {

    private final PlatformSecurityContext context;
    private final LineOfCreditReadPlatformService readPlatformService;
    private final LineOfCreditWritePlatformService writePlatformService;
    private final DefaultToApiJsonSerializer<LineOfCreditData> toApiJsonSerializer;
    private final DefaultToApiJsonSerializer<LineOfCreditTransactionData> transactionToApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List Line of Credits", description = "The list capability of line of credits can support pagination and sorting.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditsResponse.class))) })
    public String retrieveAll(@Context final UriInfo uriInfo,
                             @QueryParam("clientId") @Parameter(description = "clientId") final Long clientId,
                             @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
                             @QueryParam("limit") @Parameter(description = "limit") final Integer limit,
                             @QueryParam("orderBy") @Parameter(description = "orderBy") final String orderBy,
                             @QueryParam("sortOrder") @Parameter(description = "sortOrder") final String sortOrder) {

        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        Collection<LineOfCreditData> lineOfCredits;
        if (clientId != null) {
            lineOfCredits = this.readPlatformService.retrieveAllLineOfCreditsForClient(clientId);
        } else {
            lineOfCredits = this.readPlatformService.retrieveAllLineOfCredits();
        }

        return this.toApiJsonSerializer.serialize(settings, lineOfCredits, Collections.singleton("lineOfCredit"));
    }

    @GET
    @Path("template")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Line of Credit Template", description = "This is a convenience resource. It can be useful when building maintenance user interface screens for line of credit applications.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditTemplateResponse.class))) })
    public String retrieveTemplate(@Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        final LineOfCreditData template = this.readPlatformService.retrieveTemplate();

        return this.toApiJsonSerializer.serialize(settings, Collections.singleton(template), Collections.singleton("lineOfCredit"));
    }

    @GET
    @Path("{lineOfCreditId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a Line of Credit", description = "Example Requests:\n" + "\n" + "lineofcredit/1")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditResponse.class))) })
    public String retrieveOne(@PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
                             @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        final LineOfCreditData lineOfCredit = this.readPlatformService.retrieveOne(lineOfCreditId);

        return this.toApiJsonSerializer.serialize(settings, Collections.singleton(lineOfCredit), Collections.singleton("lineOfCredit"));
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a Line of Credit", description = "Creates a new line of credit for a client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.PostLineOfCreditResponse.class))) })
    public String create(@Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder() //
                .createLineOfCredit() //
                .withJson(apiRequestBodyAsJson) //
                .build(); //

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @PUT
    @Path("{lineOfCreditId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a Line of Credit", description = "Updates an existing line of credit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.PutLineOfCreditResponse.class))) })
    public String update(@PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
                        @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder() //
                .updateLineOfCredit(lineOfCreditId) //
                .withJson(apiRequestBodyAsJson) //
                .build(); //

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("{lineOfCreditId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate a Line of Credit", description = "Activates an inactive line of credit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.PostLineOfCreditResponse.class))) })
    public String activate(@PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
                          @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder() //
                .activateLineOfCredit(lineOfCreditId) //
                .withJson(apiRequestBodyAsJson) //
                .build(); //

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @DELETE
    @Path("{lineOfCreditId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a Line of Credit", description = "Deletes a line of credit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.DeleteLineOfCreditResponse.class))) })
    public String delete(@PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId) {

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder() //
                .deleteLineOfCredit(lineOfCreditId) //
                .build(); //

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("{lineOfCreditId}/draw")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Draw Amount from Line of Credit", description = "Draws an amount from an active line of credit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.PostLineOfCreditDrawResponse.class))) })
    public String drawAmount(@PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
                            @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder() //
                .drawLineOfCreditAmount(lineOfCreditId) //
                .withJson(apiRequestBodyAsJson) //
                .build(); //

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("{lineOfCreditId}/repay")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Repay Amount to Line of Credit", description = "Repays an amount to an active line of credit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.PostLineOfCreditRepayResponse.class))) })
    public String repayAmount(@PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
                             @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder() //
                .repayLineOfCreditAmount(lineOfCreditId) //
                .withJson(apiRequestBodyAsJson) //
                .build(); //

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @GET
    @Path("{lineOfCreditId}/transactions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List Line of Credit Transactions", description = "The list capability of line of credit transactions can support pagination and sorting.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditTransactionsResponse.class))) })
    public String retrieveTransactions(@PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
                                      @Context final UriInfo uriInfo,
                                      @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
                                      @QueryParam("limit") @Parameter(description = "limit") final Integer limit) {

        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        final Collection<LineOfCreditTransactionData> transactions = this.readPlatformService.retrieveAllTransactions(lineOfCreditId);

        return this.transactionToApiJsonSerializer.serialize(settings, transactions, Collections.singleton("lineOfCreditTransaction"));
    }
} 