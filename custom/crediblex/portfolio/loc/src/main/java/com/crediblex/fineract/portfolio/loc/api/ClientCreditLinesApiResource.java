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

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditData;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditReadPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.util.Collection;
import java.util.Collections;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Path("/v1/clients")
@Component
@Tag(name = "Client Credit Lines", description = "Line of Credit management for clients")
public class ClientCreditLinesApiResource {

    private final PlatformSecurityContext context;
    private final LineOfCreditReadPlatformService readPlatformService;
    private final DefaultToApiJsonSerializer<LineOfCreditData> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @Autowired
    public ClientCreditLinesApiResource(PlatformSecurityContext context, LineOfCreditReadPlatformService readPlatformService,
            DefaultToApiJsonSerializer<LineOfCreditData> toApiJsonSerializer, ApiRequestParameterHelper apiRequestParameterHelper) {
        this.context = context;
        this.readPlatformService = readPlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
    }

    @GET
    @Path("{clientId}/creditlines")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List Line of Credits for Client", description = "Retrieves all line of credits for a specific client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LineOfCreditApiResourceSwagger.GetLineOfCreditsResponse.class))) })
    public String retrieveCreditLines(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        final Collection<LineOfCreditData> lineOfCredits = this.readPlatformService.retrieveAllLineOfCreditsForClient(clientId);

        return this.toApiJsonSerializer.serialize(settings, lineOfCredits, Collections.singleton("lineOfCredit"));
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

        this.context.authenticatedUser().validateHasReadPermission("LINE_OF_CREDIT");

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        final LineOfCreditData lineOfCredit = this.readPlatformService.retrieveOne(lineOfCreditId);

        // Verify that the line of credit belongs to the specified client
        if (lineOfCredit == null || lineOfCredit.getClientId() == null || !lineOfCredit.getClientId().equals(clientId)) {
            throw new RuntimeException("Line of credit not found for the specified client");
        }

        return this.toApiJsonSerializer.serialize(settings, lineOfCredit, Collections.singleton("lineOfCredit"));
    }
}
