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
import com.crediblex.fineract.portfolio.loc.data.AddVendorRequest;
import com.crediblex.fineract.portfolio.loc.data.BulkLoanDisbursementRequest;
import com.crediblex.fineract.portfolio.loc.data.BulkLoanDisbursementResponse;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditActionRequest;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditData;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditTransactionData;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditWithLoansData;
import com.crediblex.fineract.portfolio.loc.data.UpdateVendorRequest;
import com.crediblex.fineract.portfolio.loc.data.VendorResponse;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditBulkDisbursementService;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditReadPlatformService;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditTransactionReadPlatformService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
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
    private final ToApiJsonSerializer<VendorResponse> vendorResponseSerializer;
    private final ToApiJsonSerializer<BulkLoanDisbursementResponse> bulkDisbursementResponseSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    @Qualifier("portfolioCommandSourceWritePlatformServiceImpl")
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final LineOfCreditBulkDisbursementService bulkDisbursementService;

    @GET
    @Path("{clientId}/creditlines/template")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Line of Credit Template", description = "This is a convenience resource. It can be useful when building maintenance user interface screens for line of credit applications.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditTemplateResponse.class))) })
    public String retrieveTemplate(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(LineOfCreditApiConstants.LINE_OF_CREDIT);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        final LineOfCreditData template = this.readPlatformService.retrieveTemplate(clientId);

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
        this.context.authenticatedUser().validateHasReadPermission(LineOfCreditApiConstants.LINE_OF_CREDIT);
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

        this.context.authenticatedUser().validateHasReadPermission(LineOfCreditApiConstants.LINE_OF_CREDIT);
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
    @Operation(summary = "Perform Action on Line of Credit", description = "Performs various actions on a line of credit: approve, activate, close, deactivate, manageapprovedbuyers, blockamount, unblockamount, adjustcreditlimit")
    @RequestBody(required = false, content = @Content(schema = @Schema(implementation = LineOfCreditActionRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.PostLineOfCreditResponse.class))) })
    public String performAction(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @PathParam("action") @Parameter(description = "action", schema = @Schema(allowableValues = { "approve", "activate", "close",
                    "deactivate", "manageapprovedbuyers", "increasecreditlimit", "decreasecreditlimit", "adjustcreditlimit", "undoclose",
                    "reactivate", "blockamount", "unblockamount" })) final String action,
            @Parameter(hidden = true) final String requestBody) {

        // Handle different request types based on action
        String jsonRequest;
        if ("manageapprovedbuyers".equalsIgnoreCase(action)) {
            // For manage approved buyers, use the request body directly (ManageApprovedBuyersRequest)
            jsonRequest = requestBody != null ? requestBody : "{}";
        } else {
            // Create a default request if none provided for other actions
            try {
                LineOfCreditActionRequest request = requestBody != null && !requestBody.trim().isEmpty()
                        ? new ObjectMapper().readValue(requestBody, LineOfCreditActionRequest.class)
                        : new LineOfCreditActionRequest("yyyy-MM-dd", "en");
                jsonRequest = request.toJson();
            } catch (Exception e) {
                // Fallback to default request
                LineOfCreditActionRequest defaultRequest = new LineOfCreditActionRequest("yyyy-MM-dd", "en");
                jsonRequest = defaultRequest.toJson();
            }
        }

        CommandWrapper commandRequest;
        LineOfCreditCommandWrapperBuilder builder = new LineOfCreditCommandWrapperBuilder();

        commandRequest = switch (action.toLowerCase()) {
            case "approve" -> builder.approveLineOfCredit(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "activate" -> builder.activateLineOfCredit(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "close" -> builder.closeLineOfCredit(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "deactivate" -> builder.deactivateLineOfCredit(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "increasecreditlimit" -> builder.increaseCreditLimit(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "decreasecreditlimit" -> builder.decreaseCreditLimit(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "adjustcreditlimit" -> builder.adjustCreditLimit(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "undoclose" -> builder.undoCloseLineOfCredit(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "reactivate" -> builder.reactivateLineOfCredit(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "manageapprovedbuyers" -> builder.manageApprovedBuyers(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "blockamount" -> builder.blockAmount(lineOfCreditId, clientId).withJson(jsonRequest).build();
            case "unblockamount" -> builder.unblockAmount(lineOfCreditId, clientId).withJson(jsonRequest).build();
            default -> throw new PlatformApiDataValidationException("error.msg.lineofcredit.invalid.action",
                    "The action `" + action + "` is not valid for line of credit " + lineOfCreditId, List.of());
        };

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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditTransactionsResponse.class))) })
    public String retrieveTransactions(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @QueryParam("offset") @Parameter(description = "offset") @DefaultValue("0") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit") @DefaultValue("20") final Integer limit,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(LineOfCreditApiConstants.LINE_OF_CREDIT);

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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditTransactionResponse.class))) })
    public String retrieveTransaction(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @PathParam("transactionId") @Parameter(description = "transactionId") final Long transactionId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(LineOfCreditApiConstants.LINE_OF_CREDIT);

        final LineOfCreditTransactionData transaction = this.transactionReadPlatformService.retrieveTransaction(lineOfCreditId,
                transactionId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        return this.transactionToApiJsonSerializer.serialize(settings, transaction);
    }

    @POST
    @Path("{clientId}/creditlines/{lineOfCreditId}/vendors")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Add Vendor to Line of Credit", description = "Adds a new vendor/supplier to the approved buyers list for a line of credit")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = AddVendorRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = VendorResponse.class))) })
    public String addVendor(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @Parameter(hidden = true) final String requestBody) {

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder().addVendor(lineOfCreditId, clientId)
                .withJson(requestBody).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @GET
    @Path("{clientId}/creditlines/{lineOfCreditId}/vendors")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List Vendors for Line of Credit", description = "Retrieves all vendors/suppliers for a specific line of credit")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = VendorResponse.class))) })
    public String retrieveAllVendors(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(LineOfCreditApiConstants.LINE_OF_CREDIT);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        final Collection<VendorResponse> vendors = this.readPlatformService.retrieveAllVendors(lineOfCreditId);

        return this.vendorResponseSerializer.serialize(settings, vendors);
    }

    @GET
    @Path("vendors/los-external-id/{losExternalId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get Vendor by LOS External ID", description = "Retrieves a vendor by their LOS external ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = VendorResponse.class))) })
    public String retrieveVendorByLosExternalId(
            @PathParam("losExternalId") @Parameter(description = "losExternalId") final String losExternalId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(LineOfCreditApiConstants.LINE_OF_CREDIT);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        final VendorResponse vendor = this.readPlatformService.retrieveVendorByLosExternalId(losExternalId);

        return this.vendorResponseSerializer.serialize(settings, vendor);
    }

    @PUT
    @Path("{clientId}/creditlines/{lineOfCreditId}/vendors/{vendorId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update Vendor Name", description = "Updates the name of a vendor. Only the name field can be updated.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = UpdateVendorRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.PutLineOfCreditResponse.class))) })
    public String updateVendor(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @PathParam("vendorId") @Parameter(description = "vendorId") final Long vendorId,
            @Parameter(hidden = true) final String requestBody) {

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder().updateVendor(lineOfCreditId, vendorId, clientId)
                .withJson(requestBody).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @DELETE
    @Path("{clientId}/creditlines/{lineOfCreditId}/vendors/{vendorId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete Vendor", description = "Deletes a vendor if it is not associated with any active drawdowns")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.DeleteLineOfCreditResponse.class))) })
    public String deleteVendor(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "lineOfCreditId") final Long lineOfCreditId,
            @PathParam("vendorId") @Parameter(description = "vendorId") final Long vendorId) {

        final CommandWrapper commandRequest = new LineOfCreditCommandWrapperBuilder().deleteVendor(lineOfCreditId, vendorId, clientId)
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("{clientId}/creditlines/{lineOfCreditId}/bulkdisburse")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Bulk Disburse Loans", description = "Disburses multiple approved loans under a Line of Credit in a single operation. "
            + "Each loan must be in 'Approved' status and belong to the specified Line of Credit. "
            + "The operation processes each loan independently, returning individual results for each disbursement. "
            + "Partial failures are supported - some loans may succeed while others fail.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = BulkLoanDisbursementRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bulk disbursement processed successfully", content = @Content(schema = @Schema(implementation = BulkLoanDisbursementResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error in request"),
            @ApiResponse(responseCode = "404", description = "Line of Credit or loans not found") })
    public String bulkDisbursLoans(@PathParam("clientId") @Parameter(description = "Client ID") final Long clientId,
            @PathParam("lineOfCreditId") @Parameter(description = "Line of Credit ID") final Long lineOfCreditId,
            @Parameter(hidden = true) final BulkLoanDisbursementRequest request, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(LineOfCreditApiConstants.LINE_OF_CREDIT);

        final BulkLoanDisbursementResponse response = this.bulkDisbursementService.bulkDisburseLoansByLineOfCredit(lineOfCreditId, clientId,
                request);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        return this.bulkDisbursementResponseSerializer.serialize(settings, response);
    }
}
