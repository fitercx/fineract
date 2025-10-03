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

package com.crediblex.fineract.portfolio.loc.api;

import com.crediblex.fineract.portfolio.loc.commands.LineOfCreditCommandWrapperBuilder;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditActionRequest;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditData;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditTransactionData;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditWithLoansData;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditReadPlatformService;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditTransactionReadPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Path("/v1/clients")
@Component
@RequiredArgsConstructor
@Tag(name = "Line of Credit", description = "Line of Credit management for clients")
public class LineOfCreditApiResource {

    private final PlatformSecurityContext context;
    private final LineOfCreditReadPlatformService readPlatformService;
    private final LineOfCreditTransactionReadPlatformService transactionReadPlatformService;
    private final DefaultToApiJsonSerializer<LineOfCreditData> toApiJsonSerializer;
    private final DefaultToApiJsonSerializer<LineOfCreditWithLoansData> toApiWithLoansJsonSerializer;
    private final DefaultToApiJsonSerializer<LineOfCreditTransactionData> transactionToApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    @Qualifier("portfolioCommandSourceWritePlatformServiceImpl")
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Path("{clientId}/creditlines/template")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Line of Credit Template", description = "This is a convenience resource. It can be useful when building maintenance user interface screens for line of credit applications.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditTemplateResponse.class))) })
    public String retrieveTemplate(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(LocApiConstants.LINE_OF_CREDIT);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        final LineOfCreditData template = this.readPlatformService.retrieveTemplate();

        return this.toApiJsonSerializer.serialize(settings, template);
    }

    @GET
    @Path("{clientId}/creditlines")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List Line of Credits for Client", description = "Retrieves all line of credits for a specific client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditsResponse.class))) })
    public String retrieveAllForClient(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(LocApiConstants.LINE_OF_CREDIT);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        final Collection<LineOfCreditWithLoansData> lineOfCredits = this.readPlatformService
                .retrieveLineOfCreditWithLoansForClient(clientId);

        return this.toApiWithLoansJsonSerializer.serialize(settings, lineOfCredits);
    }

    @GET
    @Path("{clientId}/creditlines/{lineOfCreditId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a Line of Credit for Client", description = "Retrieves a specific line of credit for a client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditResponse.class))) })
    public String retrieveOne(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(LocApiConstants.LINE_OF_CREDIT);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        final LineOfCreditData lineOfCredit = this.readPlatformService.retrieveOneWithCharges(lineOfCreditId, clientId);
        // Verify that the line of credit belongs to the specified client

        return this.toApiJsonSerializer.serialize(settings, lineOfCredit);
    }

    @POST
    @Path("{clientId}/creditlines")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a Line of Credit", description = "Creates a new line of credit for a client.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = LineOfCreditRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.PostLineOfCreditResponse.class))) })
    public String create(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @Parameter(hidden = true) final LineOfCreditRequest lineOfCreditRequest) {

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder().createLineOfCredit(clientId)
                .withJson(toApiJsonSerializer.serialize(lineOfCreditRequest)).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @PUT
    @Path("{clientId}/creditlines/{lineOfCreditId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a Line of Credit", description = "Updates an existing line of credit.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = LineOfCreditRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.PutLineOfCreditResponse.class))) })
    public String update(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @Parameter(hidden = true) final LineOfCreditRequest lineOfCreditRequest) {

        if (lineOfCreditRequest != null) {
            lineOfCreditRequest.setClientId(clientId);
        }

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder().updateLineOfCredit(lineOfCreditId)
                .withJson(toApiJsonSerializer.serialize(lineOfCreditRequest)).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("{clientId}/creditlines/{lineOfCreditId}/{action}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Perform Action on Line of Credit", description = "Performs various actions on a line of credit: approve, activate, close, deactivate")
    @RequestBody(required = false, content = @Content(schema = @Schema(implementation = LineOfCreditActionRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.PostLineOfCreditResponse.class))) })
    public String performAction(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @PathParam("action") @Parameter(description = "action", schema = @Schema(allowableValues = { "approve", "activate", "close",
                    "deactivate" })) final String action,
            @Parameter(hidden = true) final LineOfCreditActionRequest lineOfCreditActionRequest) {

        // Create a default request if none provided
        LineOfCreditActionRequest request = lineOfCreditActionRequest != null ? lineOfCreditActionRequest
                : new LineOfCreditActionRequest("yyyy-MM-dd", "en");

        CommandWrapper commandRequest;
        LineOfCreditCommandWrapperBuilder builder = new LineOfCreditCommandWrapperBuilder();

        switch (action.toLowerCase()) {
            case "approve":
                commandRequest = builder.approveLineOfCredit(lineOfCreditId, clientId).withJson(request.toJson()).build();
            break;
            case "activate":
                commandRequest = builder.activateLineOfCredit(lineOfCreditId, clientId).withJson(request.toJson()).build();
            break;
            case "close":
                commandRequest = builder.closeLineOfCredit(lineOfCreditId, clientId).withJson(request.toJson()).build();
            break;
            case "deactivate":
                commandRequest = builder.deactivateLineOfCredit(lineOfCreditId, clientId).withJson(request.toJson()).build();
            break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action: " + action + ". Supported actions are: approve, activate, close, deactivate");
        }

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @DELETE
    @Path("{clientId}/creditlines/{lineOfCreditId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a Line of Credit", description = "Deletes a line of credit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.DeleteLineOfCreditResponse.class))) })

    public String delete(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId) {
        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder().deleteLineOfCredit(lineOfCreditId).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @GET
    @Path("{clientId}/creditlines/{lineOfCreditId}/transactions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List Line of Credit Transactions", description = "Retrieves paginated transaction history for a line of credit.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveTransactions(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @QueryParam("offset") @Parameter(description = "offset") @DefaultValue("0") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit") @DefaultValue("20") final Integer limit,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(LocApiConstants.LINE_OF_CREDIT);

        final Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by("transactionDate").descending());

        final Page<LineOfCreditTransactionData> transactions = this.transactionReadPlatformService.retrieveAllTransactions(lineOfCreditId,
                pageable);

        return this.transactionToApiJsonSerializer.serialize(transactions);
    }

    @GET
    @Path("{clientId}/creditlines/{lineOfCreditId}/transactions/{transactionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Line of Credit Transaction", description = "Retrieves a specific transaction for a line of credit.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public String retrieveTransaction(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @PathParam("transactionId") @Parameter(description = "transactionId") final Long transactionId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(LocApiConstants.LINE_OF_CREDIT);

        final LineOfCreditTransactionData transaction = this.transactionReadPlatformService.retrieveTransaction(lineOfCreditId,
                transactionId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        return this.transactionToApiJsonSerializer.serialize(settings, transaction);
    }
}
