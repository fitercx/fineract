/*
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

import com.crediblex.fineract.portfolio.loanaccount.service.LoanKeyFactStatementReadPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

@Component
@Path("/v1/loans")
@Tag(name = "Loan Key Fact Statement", description = "Key Fact Statement data for loan downloads")
@RequiredArgsConstructor
public class LoanKeyFactStatementApiResource {

    private final PlatformSecurityContext context;
    private final LoanKeyFactStatementReadPlatformService keyFactStatementReadPlatformService;
    private final ToApiJsonSerializer<?> toApiJsonSerializer;

    @GET
    @Path("{loanId}/key-fact-statement")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Key Fact Statement data for a loan")
    public String retrieveKeyFactStatement(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId) {
        context.authenticatedUser().validateHasReadPermission("LOAN");
        Map<String, Object> response = keyFactStatementReadPlatformService.retrieveKeyFactStatement(loanId);
        return toApiJsonSerializer.serialize(response);
    }
}
